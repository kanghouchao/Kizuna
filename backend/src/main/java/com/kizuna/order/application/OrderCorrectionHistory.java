package com.kizuna.order.application;

import com.kizuna.order.domain.OrderCorrection;
import com.kizuna.order.domain.OrderCorrectionRepository;
import com.kizuna.order.domain.OrderCorrectionSnapshot;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.result.OrderCorrectionResult;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.storescope.StoreSetScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCorrectionHistory {
  private final OrderRepository orders;
  private final OrderCorrectionRepository corrections;

  @StoreScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CursorPage<OrderCorrectionResult> store(String orderId, String cursor, int size) {
    return read(orderId, cursor, size, "store");
  }

  @StoreSetScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CursorPage<OrderCorrectionResult> platform(String orderId, String cursor, int size) {
    return read(orderId, cursor, size, "platform");
  }

  private CursorPage<OrderCorrectionResult> read(
      String orderId, String cursor, int size, String surface) {
    var order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("受注が見つかりません"));
    int limit = CursorPage.clampSize(size);
    long version = Long.MAX_VALUE;
    String id = "";
    if (cursor != null) {
      var key = PageCursor.decode(cursor);
      var anchor =
          corrections
              .findById(key.id())
              .orElseThrow(() -> new ServiceException("続きの位置（cursor）が不正です"));
      if (!anchor.getOrderId().equals(orderId)
          || !anchor.getStoreId().equals(order.getStoreId())
          || !key.key().equals(cursorKey(surface, anchor)))
        throw new ServiceException("続きの位置（cursor）が不正です");
      version = anchor.getAfterVersion();
      id = anchor.getId();
    }
    return CursorPage.of(
            corrections.history(orderId, version, id, PageRequest.of(0, limit + 1)),
            limit,
            c -> new PageCursor(cursorKey(surface, c), c.getId()).encode())
        .map(OrderCorrectionHistory::result);
  }

  private String cursorKey(String surface, OrderCorrection c) {
    return surface + ":" + c.getStoreId() + ":" + c.getOrderId() + ":" + c.getAfterVersion();
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
