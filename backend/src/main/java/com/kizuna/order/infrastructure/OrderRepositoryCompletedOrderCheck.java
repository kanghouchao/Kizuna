package com.kizuna.order.infrastructure;

import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.store.domain.CompletedOrderCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** {@link CompletedOrderCheck} の order モジュール実装。OrderRepository の存在確認へ委譲する。 */
@Component
@RequiredArgsConstructor
class OrderRepositoryCompletedOrderCheck implements CompletedOrderCheck {

  private final OrderRepository orderRepository;

  @Override
  public boolean existsForStore(long storeId) {
    return orderRepository.existsByStoreIdAndStatus(storeId, OrderStatus.COMPLETED);
  }
}
