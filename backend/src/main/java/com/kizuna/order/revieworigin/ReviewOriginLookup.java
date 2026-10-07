package com.kizuna.order.revieworigin;

import com.kizuna.order.domain.OrderRepository;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewOriginLookup {
  private final OrderRepository orders;
  private final StoreContext context;

  @StoreScoped
  @Transactional(readOnly = true)
  public Optional<ReviewOriginFacts> find(String orderId) {
    if (!context.hasStoreId()) throw new AccessDeniedException("受注の店舗指定を確認してください");
    return orders
        .findById(orderId)
        .map(
            o ->
                new ReviewOriginFacts(
                    o.getId(), o.getVersion(), o.getStatus().name(), o.isCompletionInvalidated()));
  }
}
