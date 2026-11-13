package com.kizuna.order.dashboard;

import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.order.domain.OrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.LocalDate;
import java.util.EnumMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ダッシュボードの区画が数える受注の読み取り。権限は区画の端点が守り、ここは受注の一覧の権限を求めない。 */
@Service
@RequiredArgsConstructor
public class OrderDashboardReader {
  private final EntityManager em;

  @Transactional(readOnly = true)
  public OrderStatusCounts statusCounts(Long storeId, LocalDate businessDate) {
    var counts = new EnumMap<OrderStatus, Long>(OrderStatus.class);
    em.createQuery(
            """
            select o.status, count(o) from com.kizuna.order.domain.Order o
            where o.storeId = :store and o.businessDate = :date
              and o.completionInvalidated = false
            group by o.status
            """,
            Tuple.class)
        .setParameter("store", storeId)
        .setParameter("date", businessDate)
        .getResultList()
        .forEach(row -> counts.put(row.get(0, OrderStatus.class), row.get(1, Long.class)));
    return new OrderStatusCounts(
        counts.getOrDefault(OrderStatus.CONFIRMED, 0L),
        counts.getOrDefault(OrderStatus.IN_SERVICE, 0L),
        counts.getOrDefault(OrderStatus.COMPLETED, 0L),
        counts.getOrDefault(OrderStatus.CANCELLED, 0L));
  }

  /** 希望営業日が今日以降の PENDING の予約申請。希望日を過ぎた申請は失効して確定も謝絶もできないので、処理すべき件数に入れない。 */
  @Transactional(readOnly = true)
  public long pendingApplications(Long storeId, LocalDate businessDate) {
    return em.createQuery(
            """
            select count(a) from com.kizuna.order.domain.OrderApplication a
            where a.storeId = :store and a.status = :pending and a.businessDate >= :date
            """,
            Long.class)
        .setParameter("store", storeId)
        .setParameter("pending", OrderApplicationStatus.PENDING)
        .setParameter("date", businessDate)
        .getSingleResult();
  }
}
