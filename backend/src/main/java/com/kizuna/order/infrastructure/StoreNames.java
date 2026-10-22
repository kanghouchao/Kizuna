package com.kizuna.order.infrastructure;

import com.kizuna.shared.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoreNames {
  private final EntityManager entityManager;

  public String name(Long storeId) {
    return entityManager
        .createQuery(
            "select s.name from com.kizuna.store.domain.Store s where s.id = :storeId",
            String.class)
        .setParameter("storeId", storeId)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException("店舗が見つかりません"));
  }
}
