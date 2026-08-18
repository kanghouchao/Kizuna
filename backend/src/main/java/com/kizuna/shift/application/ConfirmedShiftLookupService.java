package com.kizuna.shift.application;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeExempt;
import com.kizuna.shift.api.dto.PublicShiftResponse;
import com.kizuna.shift.domain.ConfirmedShiftCastView;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftStatus;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 確定シフトの店舗外向け読み口。会員ポータルの指名候補提示と、申請時の指名の妥当性検証が共有する。
 *
 * <p>呼び手（会員）は店舗文脈を確立できないため {@code @StoreScoped} は用いず、店舗の隔離は問い合わせの storeId を明示指定することで担保する —
 * storeFilter は働かないので、この明示指定が唯一の境界である。
 */
@Service
@RequiredArgsConstructor
@NamedInterface("application")
public class ConfirmedShiftLookupService {

  /** 予約で扱える先の上限日数。候補一覧の照会と申請の利用日検証（MemberOrderService）が共有し、無制限の未来日を防ぐ。 */
  public static final int MAX_LOOKAHEAD_DAYS = 90;

  private static final String EXPLICIT_STORE_ID_IS_THE_BOUNDARY =
      "呼び手（会員）は店舗文脈を確立できないため、問い合わせへの storeId 明示指定が唯一の境界";

  private final ShiftRepository shiftRepository;
  private final StoreExistenceCheck storeExistenceCheck;
  private final AppProperties appProperties;

  /** 指定店舗・指定日の露出可能（CONFIRMED ∧ 公開可）なシフトに入っている ACTIVE キャストを返す。 */
  @StoreScopeExempt(reason = EXPLICIT_STORE_ID_IS_THE_BOUNDARY)
  @Transactional(readOnly = true)
  public List<PublicShiftResponse> listConfirmedCasts(Long storeId, LocalDate workDate) {
    if (!storeExistenceCheck.exists(storeId)) {
      throw new ServiceException("店舗が見つかりません");
    }
    validateWorkDate(workDate);
    // 同じ日に複数の確定シフトを持つキャストは 1 件に畳む。指名はキャスト単位で、どのシフトかは選べないため、
    // 重複させると同じ値の選択肢が並ぶだけになる。問い合わせは開始時刻の昇順なので、残るのは最も早い出勤。
    Map<String, PublicShiftResponse> byCast = new LinkedHashMap<>();
    for (ConfirmedShiftCastView view : shiftRepository.findConfirmedCasts(storeId, workDate)) {
      byCast.computeIfAbsent(
          view.getCastId(),
          castId ->
              PublicShiftResponse.builder()
                  .castId(castId)
                  .castName(view.getCastName())
                  .castPhotoUrl(view.getCastPhotoUrl())
                  .startTime(view.getStartTime())
                  .endTime(view.getEndTime())
                  .build());
    }
    return List.copyOf(byCast.values());
  }

  /**
   * 指定店舗・指定キャスト・指定日に確定シフトがあるか。公開可否は見ない — 店舗の受注確定の内部検証が宛先で、 公開可否は店外露出のフィルタであって状態機械の一部ではない（ADR 0015
   * の負向不変量）。
   *
   * <p>店外（会員）から来る指名の検証は代わりに {@link #hasPubliclyVisibleShift} を使う。
   */
  @StoreScopeExempt(reason = EXPLICIT_STORE_ID_IS_THE_BOUNDARY)
  @Transactional(readOnly = true)
  public boolean hasConfirmedShift(Long storeId, String castId, LocalDate workDate) {
    return shiftRepository.existsByStoreIdAndCastIdAndWorkDateAndStatus(
        storeId, castId, workDate, ShiftStatus.CONFIRMED);
  }

  /**
   * 指定店舗・指定キャスト・指定日に露出可能（CONFIRMED ∧ 公開可）なシフトがあるか。会員経由の指名の書き込み検証が使う。
   *
   * <p>{@link #listConfirmedCasts} と同じ述語をここで共有する — 候補から隠すだけでは cast_id を直接送る要求を防げない。
   * 粒度も候補と揃えて日単位で、時間帯の照合はしない。
   */
  @StoreScopeExempt(reason = EXPLICIT_STORE_ID_IS_THE_BOUNDARY)
  @Transactional(readOnly = true)
  public boolean hasPubliclyVisibleShift(Long storeId, String castId, LocalDate workDate) {
    return shiftRepository.existsByStoreIdAndCastIdAndWorkDateAndStatusAndPublishedTrue(
        storeId, castId, workDate, ShiftStatus.CONFIRMED);
  }

  private void validateWorkDate(LocalDate workDate) {
    LocalDate today = LocalDate.now(ZoneId.of(appProperties.getTimezone()));
    if (workDate.isBefore(today) || ChronoUnit.DAYS.between(today, workDate) > MAX_LOOKAHEAD_DAYS) {
      throw new ServiceException("取得できる日付の範囲を超えています");
    }
  }
}
