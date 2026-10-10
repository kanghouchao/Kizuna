package com.kizuna.order.reporting;

import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdvertisingOrderReader {
  private final EntityManager em;
  private final StoreContext stores;

  public record Order(String mediaName, boolean zeroAmount) {}

  @StoreScoped
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
  public List<Order> read(LocalDate from, LocalDate to, int limit) {
    if (!stores.hasStoreId()) throw new AccessDeniedException("店舗の指定が必要です");
    if (limit < 1 || limit > 100_000) throw tooLarge();
    var rows =
        em.createQuery(
                """
        select new com.kizuna.order.reporting.AdvertisingOrderReader$Order(
          o.mediaName, case when o.totalFee = 0 then true else false end)
        from com.kizuna.order.domain.Order o
        where o.storeId = :store and o.businessDate >= :from and o.businessDate <= :to
          and o.status = com.kizuna.order.domain.OrderStatus.COMPLETED
          and o.completionInvalidated = false
        order by o.id
        """,
                Order.class)
            .setParameter("store", stores.getStoreId())
            .setParameter("from", from)
            .setParameter("to", to)
            .setMaxResults(limit + 1)
            .getResultList();
    if (rows.size() > limit) throw tooLarge();
    return List.copyOf(rows);
  }

  private static ServiceUnavailableException tooLarge() {
    return new ServiceUnavailableException("有効完了受注が処理可能な上限を超えています。対象を確認してください");
  }
}
