package com.kizuna.order.infrastructure;

import com.kizuna.order.api.dto.MonthlyRemunerationCastSummary;
import com.kizuna.order.api.dto.MonthlyRemunerationOrderSummary;
import com.kizuna.shared.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RemunerationQuery {
  private final EntityManager entityManager;

  private static final String PEOPLE =
      """
      from com.kizuna.cast.domain.CastEnrollment e
      join com.kizuna.cast.domain.CastProfile p on p.enrollmentId = e.id
      where e.storeId = :storeId and e.castId is not null
      and not exists (
        select newer.id from com.kizuna.cast.domain.CastEnrollment newer
        where newer.storeId = e.storeId and newer.castId = e.castId
          and (newer.createdAt > e.createdAt or (newer.createdAt = e.createdAt and newer.id > e.id)))
      """;
  private static final String ORDERS =
      """
      from com.kizuna.order.domain.Order o
      join com.kizuna.cast.domain.CastEnrollment e on e.id = o.castId and e.storeId = o.storeId
      where o.storeId = :storeId and e.castId = :personId
        and o.businessDate >= :start and o.businessDate <= :end
        and o.status = com.kizuna.order.domain.OrderStatus.COMPLETED
      """;

  public Page<MonthlyRemunerationCastSummary> casts(Long storeId, String search, Pageable page) {
    String keyword = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
    String from = PEOPLE;
    if (!keyword.isEmpty())
      from +=
          """
        and exists (
          select old.id from com.kizuna.cast.domain.CastEnrollment old
          join com.kizuna.cast.domain.CastProfile profile on profile.enrollmentId = old.id
          where old.storeId = e.storeId and old.castId = e.castId
            and locate(:search, lower(profile.name)) > 0)
        """;
    var rows =
        entityManager
            .createQuery("select e.castId, p.name " + from + " order by e.castId asc", Tuple.class)
            .setParameter("storeId", storeId);
    var count =
        entityManager
            .createQuery("select count(e) " + from, Long.class)
            .setParameter("storeId", storeId);
    if (!keyword.isEmpty()) {
      rows.setParameter("search", keyword);
      count.setParameter("search", keyword);
    }
    var content =
        rows
            .setFirstResult((int) page.getOffset())
            .setMaxResults(page.getPageSize())
            .getResultList()
            .stream()
            .map(
                row ->
                    new MonthlyRemunerationCastSummary(
                        row.get(0, Long.class), row.get(1, String.class)))
            .toList();
    return new PageImpl<>(content, page, count.getSingleResult());
  }

  public String personName(Long storeId, Long personId) {
    return entityManager
        .createQuery("select p.name " + PEOPLE + " and e.castId = :personId", String.class)
        .setParameter("storeId", storeId)
        .setParameter("personId", personId)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException("キャスト本人が見つかりません"));
  }

  public long total(Long storeId, Long personId, YearMonth month) {
    return total(storeId, personId, month.atDay(1), month.atEndOfMonth());
  }

  public long dailyTotal(Long storeId, Long personId, LocalDate day) {
    return total(storeId, personId, day, day);
  }

  private long total(Long storeId, Long personId, LocalDate start, LocalDate end) {
    return bind(
            entityManager.createQuery(
                "select coalesce(sum(case when o.completionInvalidated then 0 else o.accruedRemuneration end), 0) "
                    + ORDERS,
                Long.class),
            storeId,
            personId,
            start,
            end)
        .getSingleResult();
  }

  public Page<MonthlyRemunerationOrderSummary> orders(
      Long storeId, Long personId, YearMonth month, Pageable page) {
    return orders(storeId, personId, month.atDay(1), month.atEndOfMonth(), page);
  }

  public Page<MonthlyRemunerationOrderSummary> dailyOrders(
      Long storeId, Long personId, LocalDate day, Pageable page) {
    return orders(storeId, personId, day, day, page);
  }

  private Page<MonthlyRemunerationOrderSummary> orders(
      Long storeId, Long personId, LocalDate start, LocalDate end, Pageable page) {
    var rows =
        bind(
                entityManager.createQuery(
                    "select o.id, o.businessDate, o.accruedRemuneration, o.completionInvalidated "
                        + ORDERS
                        + " order by o.businessDate desc, o.id desc",
                    Tuple.class),
                storeId,
                personId,
                start,
                end)
            .setFirstResult((int) page.getOffset())
            .setMaxResults(page.getPageSize())
            .getResultList();
    long count =
        bind(
                entityManager.createQuery("select count(o) " + ORDERS, Long.class),
                storeId,
                personId,
                start,
                end)
            .getSingleResult();
    var names = new HashMap<String, List<String>>();
    if (!rows.isEmpty()) {
      var lines =
          entityManager
              .createQuery(
                  """
          select l.orderId, l.name from com.kizuna.order.domain.OrderFeeLine l
          where l.storeId = :storeId and l.orderId in :ids
            and l.kind in (com.kizuna.order.domain.OrderFeeLineKind.BASE_COURSE,
              com.kizuna.order.domain.OrderFeeLineKind.SPECIAL_SERVICE,
              com.kizuna.order.domain.OrderFeeLineKind.EXTENSION,
              com.kizuna.order.domain.OrderFeeLineKind.SURCHARGE)
          order by l.orderId, l.id
          """,
                  Tuple.class)
              .setParameter("storeId", storeId)
              .setParameter("ids", rows.stream().map(r -> r.get(0, String.class)).toList())
              .getResultList();
      for (var line : lines)
        names
            .computeIfAbsent(line.get(0, String.class), ignored -> new ArrayList<>())
            .add(line.get(1, String.class));
    }
    var content =
        rows.stream()
            .map(
                row -> {
                  String id = row.get(0, String.class);
                  boolean invalidated = row.get(3, Boolean.class);
                  return new MonthlyRemunerationOrderSummary(
                      id,
                      row.get(1, LocalDate.class),
                      String.join(" / ", names.getOrDefault(id, List.of())),
                      invalidated ? 0 : row.get(2, Integer.class),
                      invalidated);
                })
            .toList();
    return new PageImpl<>(content, page, count);
  }

  private <T> TypedQuery<T> bind(
      TypedQuery<T> query, Long storeId, Long personId, LocalDate start, LocalDate end) {
    return query
        .setParameter("storeId", storeId)
        .setParameter("personId", personId)
        .setParameter("start", start)
        .setParameter("end", end);
  }
}
