package com.kizuna.order.application;

import com.kizuna.member.application.MemberRankMetrics;
import com.kizuna.member.application.MemberRankService;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderAttributionStatus;
import com.kizuna.point.application.BenefitGrantService;
import com.kizuna.point.application.PointLedgerService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class AttributionMaterializer implements MemberRankMetrics {
  private final OrderAttributionRepository attributions;
  private final PointLedgerService ledger;
  private final BenefitGrantService benefits;
  private final MemberRankService ranks;

  @Transactional(propagation = Propagation.MANDATORY)
  Result materialize(
      long memberId,
      String memberCode,
      String orderId,
      Long storeId,
      LocalDate businessDate,
      OffsetDateTime occurredAt,
      Long actorId,
      Trigger trigger) {
    // 外部キー検査より先に会員行を押さえ、取引終了まで保持して並行する帰属を直列化する。
    // 利用を付与より先に積み、同じ受注の付与で利用を賄わせない。
    // 帰属は付与ゼロでも保存し、今回の来店と付与仕訳を揃えてから昇格を判定する。
    // 特典の一人一回判定もこのロック内で行い、受益歴の照合と記帳の間への割り込みを防ぐ。
    ranks.lockForPromotion(memberId);
    if (trigger instanceof Completion completion && completion.usePoints() > 0) {
      ledger.useForOrder(memberId, orderId, storeId, completion.usePoints(), actorId);
    }
    OrderAttribution attribution =
        attributions.save(
            switch (trigger) {
              case Completion ignored ->
                  OrderAttribution.onCompletion(orderId, memberId, memberCode, occurredAt);
              case ReceiptClaim ignored ->
                  OrderAttribution.onReceiptClaim(orderId, memberId, memberCode, occurredAt);
            });
    PointLedgerService.GrantedPoints grant =
        switch (trigger) {
          case Completion completion ->
              ledger.grantForOrder(
                  memberId, orderId, storeId, completion.grantBasisAmount(), actorId);
          case ReceiptClaim claim ->
              new PointLedgerService.GrantedPoints(
                  claim.plannedPoints(),
                  ledger.grantPlannedForOrder(
                      memberId, orderId, storeId, claim.plannedPoints(), actorId));
        };
    long benefitPoints =
        benefits.grantVisitBenefits(memberId, orderId, storeId, businessDate, actorId);
    ranks.syncOnAttribution(memberId, this, attribution.getId(), grant.entryId());
    return new Result(grant.points(), benefitPoints);
  }

  sealed interface Trigger permits Completion, ReceiptClaim {}

  record Completion(int grantBasisAmount, int usePoints) implements Trigger {}

  record ReceiptClaim(int plannedPoints) implements Trigger {}

  record Result(int grantedPoints, long benefitPoints) {}

  @Override
  public long completedVisitCount(long memberId) {
    return attributions.countByMemberIdAndStatus(memberId, OrderAttributionStatus.ACTIVE);
  }

  @Override
  public long netGrantedPoints(long memberId) {
    return ledger.netGrantedPointsFor(memberId);
  }
}
