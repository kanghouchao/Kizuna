package com.kizuna.remuneration.reporting;

import com.kizuna.order.reporting.OperationalFacts;
import com.kizuna.remuneration.domain.AttendanceDuration;
import com.kizuna.remuneration.domain.DailyGuarantee;
import com.kizuna.remuneration.domain.GuaranteeState;
import com.kizuna.remuneration.domain.RemunerationAmounts;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shift.remuneration.AttendanceFacts;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RemunerationReportReader {
  private final EntityManager em;
  private final AttendanceFacts attendances;
  private final StoreContext context;

  public record Term(
      Long storeId,
      Long personId,
      String id,
      LocalDate effectiveFrom,
      GuaranteeState state,
      Long dailyAmount) {}

  private record Person(Long store, Long person) {}

  private record Key(Long store, Long person, LocalDate day) {}

  private static final class Values {
    long orders, bonuses;
    final List<AttendanceDuration.Interval> attendance = new ArrayList<>();
  }

  @PreAuthorize("hasAuthority('PERM_REMUNERATION_VIEW')")
  @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
  public RemunerationReportFacts read(
      OperationalFacts orders, LocalDate from, LocalDate to, int limit) {
    var stores = orders.stores().stream().map(OperationalFacts.Store::storeId).toList();
    var scope =
        StoreScope.fromAuthentication(SecurityContextHolder.getContext().getAuthentication());
    for (var store : stores)
      if (context.hasStoreId()
          ? !store.equals(context.getStoreId())
          : scope == null || !scope.authorizes(store))
        throw new AccessDeniedException("授権店舗集合に含まれません");
    if (stores.isEmpty()) return new RemunerationReportFacts(List.of(), List.of());
    if (limit < 1 || limit > 100_000) throw tooLarge();
    var intervals = attendances.forReport(stores, from, to, limit);
    int remaining = limit - intervals.size();
    if (remaining < 0) throw tooLarge();
    var bonuses =
        em.createQuery(
                """
      select new com.kizuna.remuneration.reporting.RemunerationReportFacts$Bonus(
        b.storeId, b.personId, b.id, b.version, b.awardDate,
        case when b.cancelledAt is null then false else true end,
        case when b.cancelledAt is null then b.amount else 0L end)
      from BonusAward b where b.storeId in :stores and b.awardDate >= :from and b.awardDate <= :to
      order by b.storeId, b.personId, b.awardDate, b.id
      """,
                RemunerationReportFacts.Bonus.class)
            .setParameter("stores", stores)
            .setParameter("from", from)
            .setParameter("to", to)
            .setMaxResults(remaining + 1)
            .getResultList();
    remaining -= bonuses.size();
    if (remaining < 0) throw tooLarge();
    var terms =
        em.createQuery(
                """
      select new com.kizuna.remuneration.reporting.RemunerationReportReader$Term(
        t.storeId, t.personId, t.id, t.effectiveFrom, t.state, t.dailyAmount)
      from GuaranteeTerm t where t.storeId in :stores and t.cancelledAt is null and t.effectiveFrom <= :to
      order by t.storeId, t.personId, t.effectiveFrom, t.id
      """,
                Term.class)
            .setParameter("stores", stores)
            .setParameter("to", to)
            .setMaxResults(remaining + 1)
            .getResultList();
    if (terms.size() > remaining) throw tooLarge();
    try {
      return calculate(orders, intervals, terms, bonuses, limit);
    } catch (ServiceException | ArithmeticException ex) {
      throw new ServiceUnavailableException("報酬金額が扱える範囲を超えています。条件を絞ってください");
    }
  }

  static RemunerationReportFacts calculate(
      OperationalFacts orders,
      List<AttendanceFacts.ReportInterval> intervals,
      List<Term> terms,
      List<RemunerationReportFacts.Bonus> bonuses,
      int limit) {
    Map<Key, Values> values =
        new TreeMap<>(
            Comparator.comparing(Key::store).thenComparing(Key::person).thenComparing(Key::day));
    for (var order : orders.orders()) {
      if (order.personId() == null) continue;
      var value =
          values.computeIfAbsent(
              new Key(order.storeId(), order.personId(), order.businessDate()), k -> new Values());
      value.orders =
          RemunerationAmounts.add(value.orders, order.invalidated() ? 0 : order.remuneration());
    }
    for (var interval : intervals)
      values
          .computeIfAbsent(
              new Key(interval.storeId(), interval.personId(), interval.businessDate()),
              k -> new Values())
          .attendance
          .add(new AttendanceDuration.Interval(interval.start(), interval.end()));
    for (var bonus : bonuses) {
      var value =
          values.computeIfAbsent(
              new Key(bonus.storeId(), bonus.personId(), bonus.awardDate()), k -> new Values());
      value.bonuses = RemunerationAmounts.add(value.bonuses, bonus.effectiveAmount());
    }
    if (values.size() > limit) throw tooLarge();
    Map<Person, TreeMap<LocalDate, Term>> timelines = new HashMap<>();
    for (var term : terms)
      timelines
          .computeIfAbsent(new Person(term.storeId(), term.personId()), k -> new TreeMap<>())
          .put(term.effectiveFrom(), term);
    var result = new ArrayList<RemunerationReportFacts.Day>();
    values.forEach(
        (key, value) -> {
          var timeline = timelines.get(new Person(key.store(), key.person()));
          var entry = timeline == null ? null : timeline.floorEntry(key.day());
          var term = entry == null ? null : entry.getValue();
          var duration = AttendanceDuration.of(value.attendance);
          var guarantee =
              DailyGuarantee.calculate(
                  term == null ? null : term.state(),
                  term == null ? null : term.dailyAmount(),
                  duration,
                  value.orders);
          result.add(
              new RemunerationReportFacts.Day(
                  key.store(),
                  key.person(),
                  key.day(),
                  value.orders,
                  term == null ? null : term.id(),
                  term == null ? null : term.effectiveFrom(),
                  term == null ? null : term.state().name(),
                  term == null ? null : term.dailyAmount(),
                  duration.closedDuration().toString(),
                  duration.incomplete(),
                  guarantee.status().name(),
                  guarantee.amount(),
                  value.bonuses));
        });
    return new RemunerationReportFacts(result, bonuses);
  }

  private static ServiceUnavailableException tooLarge() {
    return new ServiceUnavailableException("報酬根拠が多すぎます。期間または店舗を絞ってください");
  }
}
