package com.kizuna.order.infrastructure;

import com.kizuna.cast.domain.OrderReferenceCheck;
import com.kizuna.order.domain.OrderRepository;
import java.util.Collection;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OrderRepositoryReferenceCheck implements OrderReferenceCheck {
  private final OrderRepository orders;

  @Override
  public Set<String> findReferencedCastIds(Collection<String> castIds) {
    return orders.findReferencedCastIds(castIds);
  }
}
