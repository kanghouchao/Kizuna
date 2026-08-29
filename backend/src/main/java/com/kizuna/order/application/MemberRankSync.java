package com.kizuna.order.application;

import com.kizuna.member.application.MemberRankMetrics;
import com.kizuna.member.application.MemberRankService;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderAttributionStatus;
import com.kizuna.point.application.PointLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 受注が会員へ帰属した瞬間と同期した会員ランクの見直し。判定の材料（跨店舗の来店回数・付与の累計純額）は order と point にあり member
 * からは引けない（依存が環になる）ため、帰属を記録する側であるここが供給する。
 *
 * <p>供給に留めて自分では読まないのは、読む時点が会員行のロックの内側でなければならないため（{@link MemberRankMetrics}）。
 *
 * <p>帰属が生まれる経路は受注完了と伝票トークンの事後申領の 2 つで、どちらもここを通る — 契機が経路ごとに分かれると片方だけが昇格を取り逃す。
 *
 * <p>{@code @Service} ではなく {@code @Component} なのは、これがユースケースでも取引境界でもなく、呼び出し側の取引の中で回る取りまとめだけを持つため
 * （同じ層の {@link NominatableCastLookup} と同じ理由）。
 */
@Component
@RequiredArgsConstructor
class MemberRankSync implements MemberRankMetrics {

  private final OrderAttributionRepository orderAttributionRepository;
  private final PointLedgerService pointLedgerService;
  private final MemberRankService memberRankService;

  /**
   * 帰属を記録した直後にランクを見直す。今回の来店を回数へ含めるため、帰属記録の保存はこの呼出より前に済ませること。
   *
   * <p>付与の有無で判定を飛ばさない。回数条件は台帳を見ないので、会計 0 円や付与単位に満たない来店だけを重ねた会員も回数で上がる。
   *
   * @param grantEntryId 同時に記帳した付与仕訳。付与が 0 なら null
   */
  void afterAttribution(long memberId, long attributionId, Long grantEntryId) {
    memberRankService.syncOnAttribution(memberId, this, attributionId, grantEntryId);
  }

  @Override
  public long completedVisitCount(long memberId) {
    return orderAttributionRepository.countByMemberIdAndStatus(
        memberId, OrderAttributionStatus.ACTIVE);
  }

  @Override
  public long netGrantedPoints(long memberId) {
    return pointLedgerService.netGrantedPointsFor(memberId);
  }
}
