package com.kizuna.customer.infrastructure;

import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerListRepository;
import com.kizuna.shared.config.AppProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaCustomerListRepository implements CustomerListRepository {
  // 台帳の残高は期限内の加算ロットの残り。利用取消はロットではなく消費の逆転として数える。
  // HQL の参照に閉じ、customer と order の Java モジュール依存を循環させない。
  private static final String SELECT =
      """
      select c as customer,
        (select max(o.businessDate) from com.kizuna.order.domain.Order o
          where o.customerId = c.id and o.storeId = c.storeId
            and o.status = com.kizuna.order.domain.OrderStatus.COMPLETED
            and o.completionInvalidated = false) as lastVisitDate,
        (select coalesce(sum(greatest(0L, e.amount -
          (select coalesce(sum(case when debit.entryType = com.kizuna.point.domain.PointEntryType.USE_CANCEL
            then -a.amount else a.amount end), 0L)
           from com.kizuna.point.domain.PointEntry debit join debit.allocations a
           where a.sourceEntryId = e.id))), 0L)
         from com.kizuna.point.domain.PointEntry e
         where e.memberId = link.memberId and e.amount > 0
           and e.entryType <> com.kizuna.point.domain.PointEntryType.USE_CANCEL
           and (e.expiresOn is null or e.expiresOn >= :today)) as linkedBalance,
        link.id as linkId
      from com.kizuna.customer.domain.Customer c
      left join com.kizuna.customer.domain.CustomerMemberLink link
        on link.customerId = c.id and link.storeId = c.storeId
          and link.status = com.kizuna.customer.domain.LinkStatus.ACTIVE
      """;

  private final EntityManager em;
  private final AppProperties appProperties;

  @Override
  public Page<Row> findAll(Specification<Customer> specification, Pageable pageable) {
    var cb = em.unwrap(Session.class).getCriteriaBuilder();
    var query = cb.createQuery(SELECT, Object[].class);
    @SuppressWarnings("unchecked")
    Root<Customer> customer = (Root<Customer>) query.getRoot(0, Customer.class);
    query.where(specification.toPredicate(customer, query, cb));
    var selections = query.getSelection().getCompoundSelectionItems();
    var visit = (Expression<?>) selections.get(1);
    var balance = (Expression<?>) selections.get(2);
    var link = (Expression<?>) selections.get(3);
    var orders = new ArrayList<Order>();
    Sort sort = pageable.getSort();
    if (sort.getOrderFor("id") == null) sort = sort.and(Sort.by(Customer::getId));
    for (var order : sort) {
      Expression<?> value;
      switch (order.getProperty()) {
        case "lastVisitDate" -> {
          value = visit;
          orders.add(cb.asc(cb.selectCase().when(cb.isNull(visit), 1).otherwise(0)));
        }
        case "pointBalance" -> {
          value = balance;
          orders.add(cb.asc(cb.selectCase().when(cb.isNull(link), 1).otherwise(0)));
        }
        default -> value = customer.get(order.getProperty());
      }
      orders.add(order.isAscending() ? cb.asc(value) : cb.desc(value));
    }
    query.orderBy(orders);
    var today = LocalDate.now(ZoneId.of(appProperties.getTimezone()));
    var rows =
        em
            .createQuery(query)
            .setParameter("today", today)
            .setFirstResult(Math.toIntExact(pageable.getOffset()))
            .setMaxResults(pageable.getPageSize())
            .getResultList()
            .stream()
            .map(
                row ->
                    new Row(
                        (Customer) row[0],
                        (LocalDate) row[1],
                        row[3] == null ? null : (Long) row[2],
                        row[3] != null))
            .toList();
    var count = cb.createQuery(Long.class);
    var countRoot = count.from(Customer.class);
    count.select(cb.count(countRoot)).where(specification.toPredicate(countRoot, count, cb));
    return new PageImpl<>(rows, pageable, em.createQuery(count).getSingleResult());
  }
}
