package com.kizuna.order.application;

import com.kizuna.member.application.MemberRankService;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderAttributionStatus;
import com.kizuna.point.application.PointLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 受注付与の記帳と同期した会員ランクの見直し。判定の材料（跨店舗の来店回数・付与の累計純額）は order と point にあり member
 * からは引けない（依存が環になる）ため、記帳する側であるここが集めて渡す。
 *
 * <p>付与が起きる経路は受注完了と伝票トークンの事後申領の 2 つで、どちらもここを通る — 契機が経路ごとに分かれると片方だけが昇格を取り逃す。
 *
 * <p>{@code @Service} ではなく {@code @Component} なのは、これがユースケースでも取引境界でもなく、呼び出し側の取引の中で回る取りまとめだけを持つため
 * （同じ層の {@link NominatableCastLookup} と同じ理由）。
 */
@Component
@RequiredArgsConstructor
class MemberRankSync {

  private final OrderAttributionRepository orderAttributionRepository;
  private final PointLedgerService pointLedgerService;
  private final MemberRankService memberRankService;

  /**
   * 付与を記帳した直後にランクを見直す。今回の来店を回数へ含めるため、帰属記録の保存はこの呼出より前に済ませること。
   *
   * <p>付与が 0 で台帳に行が無い回は判じない — 契機は記帳そのものであり、その来店は次の付与のときに回数へ算入される（棘輪なので取り逃しにならない）。
   *
   * @param grantEntryId 記帳した付与仕訳。付与が 0 なら null
   */
  void afterGrant(long memberId, Long grantEntryId) {
    if (grantEntryId == null) {
      return;
    }
    memberRankService.syncOnGrant(
        memberId,
        orderAttributionRepository.countByMemberIdAndStatus(
            memberId, OrderAttributionStatus.ACTIVE),
        pointLedgerService.netGrantedPointsFor(memberId),
        grantEntryId);
  }
}
