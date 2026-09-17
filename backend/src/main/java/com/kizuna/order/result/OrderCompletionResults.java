package com.kizuna.order.result;

import com.kizuna.order.application.OrderCorrectionHistory;
import com.kizuna.order.domain.OrderCorrectionRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCompletionResults {
  private final OrderRepository orders;
  private final OrderCorrectionRepository corrections;
  private final OrderCorrectionHistory history;

  public CursorPage<OrderCorrectionResult> corrections(String orderId, String cursor, int size) {
    return history.store(orderId, cursor, size);
  }

  @StoreScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Optional<OrderCompletionResult> find(String orderId) {
    return orders
        .findById(orderId)
        .filter(order -> order.getStatus() == OrderStatus.COMPLETED)
        .map(
            order -> {
              var latest =
                  corrections
                      .findFirstByOrderIdOrderByAfterVersionDescIdDesc(orderId)
                      .map(OrderCorrectionHistory::result)
                      .orElse(null);
              return new OrderCompletionResult(
                  order.getId(),
                  order.getStoreId(),
                  order.getCastId(),
                  order.getBusinessDate(),
                  order.getCompletedAt(),
                  order.getFeeLines().stream()
                      .map(
                          line -> {
                            var adoption = line.getAdoption();
                            var special =
                                order.getSpecialServices().stream()
                                    .filter(item -> item.serviceId().equals(line.getServiceId()))
                                    .findFirst()
                                    .orElse(null);
                            return new OrderCompletionResult.Item(
                                line.getId(),
                                line.getKind().name(),
                                line.getName(),
                                line.getAmount(),
                                line.getDurationMinutes(),
                                line.getRemuneration(),
                                line.getServiceId(),
                                adoption == null ? null : adoption.revisionId(),
                                adoption == null ? null : adoption.revisionNumber(),
                                adoption == null ? null : adoption.adoptionBasis(),
                                adoption == null ? null : adoption.adoptedAt(),
                                special == null ? null : special.enrollmentId(),
                                special == null ? null : special.consentEventId(),
                                special == null ? null : special.consentVersion());
                          })
                      .toList(),
                  order.getAccruedRemuneration(),
                  order.getVersion(),
                  latest == null ? null : latest.correctionId(),
                  latest,
                  order.isCompletionInvalidated(),
                  order.getReplacementForOrderId());
            });
  }
}
