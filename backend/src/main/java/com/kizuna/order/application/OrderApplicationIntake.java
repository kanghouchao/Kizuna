package com.kizuna.order.application;

import com.kizuna.cast.domain.Cast;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shift.application.ConfirmedShiftLookupService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 予約申請を受け付けてよい内容かの判定。会員ポータルと公開店面の 2 つの入口が共有する。
 *
 * <p>共有するのは、入口ごとに判定が分かれると緩い側が厳しい側の拒否を迂回する裏口になるため — 匿名の入口が会員の入口より緩ければ、会員が撥ねられる指名を匿名で申請して確定させられる。
 */
@Component
@RequiredArgsConstructor
public class OrderApplicationIntake {

  private final NominatableCastLookup nominatableCast;
  private final ConfirmedShiftLookupService confirmedShiftLookupService;
  private final BusinessDateService businessDateService;

  /** 希望内容を検める。撥ねる要求が行を起こす前に済ませる。 */
  public void validateRequestedVisit(Long storeId, LocalDate businessDate, String castId) {
    validateBusinessDate(businessDate);
    validateNomination(storeId, castId, businessDate);
  }

  /** 利用日は「現在の営業日以降かつ候補照会と同じ上限（90 日）以内」。指名なしの申請が候補照会を経ずに上限を素通りしないよう、書き込み側でも同じ範囲を見る。 */
  private void validateBusinessDate(LocalDate businessDate) {
    LocalDate today = businessDateService.currentBusinessDate();
    if (businessDate.isBefore(today)) {
      throw new ServiceException("過去の日付は申請できません");
    }
    if (ChronoUnit.DAYS.between(today, businessDate)
        > ConfirmedShiftLookupService.MAX_LOOKAHEAD_DAYS) {
      throw new ServiceException("申請できる日付の範囲を超えています");
    }
  }

  /**
   * 指名は「その店舗に在籍中のキャスト」かつ「当日の公開された出勤予定に入っていること」を満たす場合のみ受け付ける。
   *
   * <p>在籍状態も公開可否も候補一覧と同じ条件で書き込み側でも見る — 候補に出さないだけでは、キャスト ID を直接送る要求を防げない。
   *
   * <p>対象は店舗の外の申請者なので、成立しない理由を区別せず「見つからない」として返す — 区別すると、その id
   * のキャストが当該店舗に在籍することそのものが分かってしまう。店舗スタッフ向けの {@link OrderService} は同じ述語から 400 を返す。
   */
  private void validateNomination(Long storeId, String castId, LocalDate businessDate) {
    if (castId == null) {
      return;
    }
    Cast cast =
        nominatableCast
            .find(storeId, castId)
            .orElseThrow(() -> new NotFoundException("キャストが見つかりません: " + castId));
    // 失敗の文言を非公開かどうかで分けない — 分けた瞬間、隠したはずのシフトの存在が申請者に読み取れる。
    if (!confirmedShiftLookupService.hasPubliclyVisibleShift(storeId, cast.getId(), businessDate)) {
      throw new ServiceException("指名したキャストはこの日の出勤予定がありません");
    }
  }
}
