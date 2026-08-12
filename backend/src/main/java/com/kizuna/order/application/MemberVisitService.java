package com.kizuna.order.application;

import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.order.api.dto.MemberVisitResponse;
import com.kizuna.order.domain.MemberVisitView;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.user.domain.PlatformUserRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会員ポータルからの来店履歴の読み取りユースケース。
 *
 * <p>来店として見えるのは有効な帰属記録を持つ受注だけで、関連（Customer–Member Link）の区間は読み直さない（ADR 0009）。したがって関連を解除しても過去の来店は
 * 見え続け、関連成立以前・無帰属・未完了・謝絶／取下げの受注は現れない。本人が申請した予約の追跡は {@link MemberOrderService} が別の読み口として並置する。
 *
 * <p>帰属記録は店舗で分割されない（台帳と同じ platform 帰属）ため storeFilter は働かない。本人の会員 ID の一致を問い合わせ自体に載せることが唯一の隔離境界である。
 *
 * <p>返すのは表示用の projection だけで、書き込みの口は持たない。会計金額・利用ポイント・顧客台帳の内部項目は projection の段階で落ちる。
 */
@Service
@RequiredArgsConstructor
public class MemberVisitService {

  private final PlatformUserRepository platformUserRepository;
  private final MemberLookupService memberLookupService;
  private final OrderAttributionRepository orderAttributionRepository;
  private final PointLedgerService pointLedgerService;

  /**
   * 本人の来店履歴（跨店集約）。
   *
   * <p>続きの指定はポイント明細と同じくカーソル（並びの鍵）で受ける。帰属記録は追加型で行が消えないが、来店は履歴を見ている
   * 最中にも増えるため、件数で位置を指すと続きを取った時点で境界の行を飛ばす。
   *
   * @param cursor 続きの位置。null なら先頭から
   * @param requestedSize 1 回に返す件数の希望値（上限に丸められる）
   */
  @Transactional(readOnly = true)
  public CursorPage<MemberVisitResponse> list(String email, String cursor, int requestedSize) {
    Long memberId = resolveMemberId(email);
    int size = CursorPage.clampSize(requestedSize);
    // 続きの有無は上限より 1 件多く取って判る。総件数の問い合わせを毎回撒かずに済む。
    Limit limit = Limit.of(size + 1);
    List<MemberVisitView> fetched =
        cursor == null
            ? orderAttributionRepository.findMemberVisitViews(memberId, limit)
            : fetchAfter(memberId, PageCursor.decode(cursor), limit);
    CursorPage<MemberVisitView> page = CursorPage.of(fetched, size, MemberVisitService::cursorOf);
    // 獲得ポイントは台帳にしか無いので、返す行の分だけをまとめて引く。行ごとに引くと来店が増えるほど
    // 問い合わせが線形に増え、余分に取った 1 件（続きの有無を判るためだけの行）まで引いてしまう。
    Map<String, Integer> grantedPoints =
        pointLedgerService.grantedPointsByOrder(
            page.content().stream().map(MemberVisitView::getOrderId).toList());
    return page.map(view -> toResponse(view, grantedPoints));
  }

  private List<MemberVisitView> fetchAfter(Long memberId, PageCursor cursor, Limit limit) {
    // 続きの取得でも本人の一致は問い合わせに載せ続ける — カーソルは位置を指すだけで、隔離境界にはならない。
    return orderAttributionRepository.findMemberVisitViewsAfter(
        memberId, cursor.timestampKey(), cursor.longId(), limit);
  }

  /** 続きの位置は一覧の並び（帰属の作成時刻 + id）と同じ組で作る。組が並びとずれると、続きが手前へ戻るか行を飛ばす。 */
  private static String cursorOf(MemberVisitView view) {
    return new PageCursor(view.getCreatedAt().toString(), String.valueOf(view.getId())).encode();
  }

  private Long resolveMemberId(String email) {
    Long platformUserId =
        platformUserRepository
            .findByEmail(email)
            .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
            .getId();
    return memberLookupService
        .findByPlatformUserId(platformUserId)
        .map(MemberLookup::memberId)
        .orElseThrow(() -> new StaleSessionException("会員情報が存在しません"));
  }

  /** 付与行の無い受注は台帳に現れないので 0 とみなす — 0 円完了でも帰属記録は生まれ、来店としては見える。 */
  private MemberVisitResponse toResponse(MemberVisitView view, Map<String, Integer> grantedPoints) {
    return new MemberVisitResponse(
        view.getVisitedOn(),
        view.getStoreName(),
        view.getPax(),
        view.getCastName(),
        grantedPoints.getOrDefault(view.getOrderId(), 0));
  }
}
