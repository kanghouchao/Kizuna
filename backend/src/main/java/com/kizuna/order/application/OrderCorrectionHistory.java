package com.kizuna.order.application;

import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCorrection;
import com.kizuna.order.domain.OrderCorrectionRepository;
import com.kizuna.order.domain.OrderCorrectionSnapshot;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.result.OrderCorrectionResult;
import com.kizuna.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCorrectionHistory {
  private final OrderRepository orders;
  private final OrderCorrectionRepository corrections;

  /** 履歴の母集合は受注の店舗と受注 ID で縛る。受注が呼出側の店舗に無ければ 404 で、他店の訂正には届かない。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<OrderCorrectionResult> store(Long storeId, String orderId, Pageable pageable) {
    var order =
        orders
            .findByIdAndStoreId(orderId, storeId)
            .orElseThrow(() -> new NotFoundException("受注が見つかりません"));
    return readChanges(order, pageable).map(OrderCorrectionHistory::result);
  }

  /** 本人の担当受注に限る。他人の受注は 404 で、その訂正には届かない。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<OrderCorrection> self(Long userId, String orderId, Pageable pageable) {
    var order =
        orders
            .findSelfOrder(userId, orderId)
            .orElseThrow(() -> new NotFoundException("報酬明細が見つかりません"));
    return readChanges(order, pageable);
  }

  private Page<OrderCorrection> readChanges(Order order, Pageable pageable) {
    return corrections.findByStoreIdAndOrderId(order.getStoreId(), order.getId(), pageable);
  }

  public static OrderCorrectionResult result(OrderCorrection c) {
    return new OrderCorrectionResult(
        c.getId(),
        c.getOrderId(),
        c.getStoreId(),
        c.getBusinessDate(),
        c.getCompletedAt(),
        c.getCorrectedAt(),
        c.getCorrectedBy(),
        c.getReason(),
        c.getBeforeVersion(),
        c.getAfterVersion(),
        snapshot(c.getBeforeSnapshot()),
        snapshot(c.getAfterSnapshot()),
        c.getChangeType());
  }

  private static OrderCorrectionResult.Snapshot snapshot(OrderCorrectionSnapshot s) {
    var c = s.course();
    var course =
        new OrderCorrectionResult.Course(
            c.serviceId(),
            c.revisionId(),
            c.revisionNumber(),
            c.name(),
            c.durationMinutes(),
            c.price(),
            c.remuneration(),
            c.adoptionBasis(),
            c.adoptedAt());
    var lines =
        s.feeLines().stream()
            .map(
                l -> {
                  var a = l.adoption();
                  return new OrderCorrectionResult.FeeLine(
                      l.lineId(),
                      l.kind().name(),
                      l.name(),
                      l.kind().displayedAmountOf(l.amount()),
                      l.durationMinutes(),
                      l.remuneration(),
                      l.kind().isSystemOwned(),
                      l.serviceId(),
                      a == null ? null : a.revisionId(),
                      a == null ? null : a.revisionNumber(),
                      a == null ? null : a.adoptionBasis(),
                      a == null ? null : a.adoptedAt());
                })
            .toList();
    var specials =
        s.specialServices().stream()
            .map(
                t ->
                    new OrderCorrectionResult.SpecialService(
                        t.serviceId(),
                        t.revisionId(),
                        t.revisionNumber(),
                        t.termsVersion(),
                        t.name(),
                        t.chargeType(),
                        t.price(),
                        t.remuneration(),
                        t.adoptionBasis(),
                        t.adoptedAt(),
                        t.enrollmentId(),
                        t.consentEventId(),
                        t.consentVersion()))
            .toList();
    return new OrderCorrectionResult.Snapshot(
        s.actualArrivalTime(),
        s.actualEndTime(),
        course,
        lines,
        specials,
        s.totalFee(),
        s.totalDurationMinutes(),
        s.totalRemuneration(),
        s.accruedRemuneration(),
        s.completionInvalidated());
  }
}
