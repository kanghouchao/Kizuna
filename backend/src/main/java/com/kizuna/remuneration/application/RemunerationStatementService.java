package com.kizuna.remuneration.application;

import com.kizuna.cast.remuneration.RemunerationPersonLookup;
import com.kizuna.order.remuneration.OrderRemunerationFacts;
import com.kizuna.remuneration.api.dto.RemunerationStatementResponse;
import com.kizuna.remuneration.api.dto.RemunerationStatementResponse.BonusItem;
import com.kizuna.remuneration.api.dto.RemunerationStatementResponse.Day;
import com.kizuna.remuneration.domain.AttendanceDuration;
import com.kizuna.remuneration.domain.DailyGuarantee;
import com.kizuna.remuneration.domain.GuaranteeTerm;
import com.kizuna.remuneration.domain.RemunerationAmounts;
import com.kizuna.remuneration.infrastructure.RemunerationRecords;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeExempt;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.storescope.StoreSetScoped;
import com.kizuna.shift.remuneration.AttendanceFacts;
import com.kizuna.user.application.ActorIdentityService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RemunerationStatementService {
  private final RemunerationRecords records;
  private final RemunerationPersonLookup people;
  private final OrderRemunerationFacts orders;
  private final AttendanceFacts attendances;
  private final StoreContext stores;
  private final ActorIdentityService actors;

  @StoreScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public RemunerationStatementResponse store(
      Long person, String month, int orderPage, int bonusPage, int size) {
    return read(stores.getStoreId(), person, month, orderPage, bonusPage, size);
  }

  @StoreSetScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public RemunerationStatementResponse platform(
      Long store, Long person, String month, int orderPage, int bonusPage, int size) {
    var scope =
        StoreScope.fromAuthentication(SecurityContextHolder.getContext().getAuthentication());
    if (scope == null || !scope.authorizes(store)) throw new AccessDeniedException("授権店舗集合に含まれません");
    return read(store, person, month, orderPage, bonusPage, size);
  }

  @StoreScopeExempt(reason = "認証主体の本人と店舗の歴史在籍を照合し、全照会を明示した本人・店舗に限定する")
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public RemunerationStatementResponse self(
      String actor, Long store, String month, int orderPage, int bonusPage, int size) {
    return read(store, people.self(actors.requireUserId(actor)), month, orderPage, bonusPage, size);
  }

  private RemunerationStatementResponse read(
      Long store, Long person, String month, int orderPage, int bonusPage, int size) {
    RemunerationInput.person(store);
    RemunerationInput.person(person);
    var target = RemunerationInput.month(month);
    var orderPaging = RemunerationInput.page(orderPage, size);
    var bonusPaging = RemunerationInput.page(bonusPage, size);
    var identity = people.require(store, person);
    var amounts = orders.daily(store, person, target);
    var attendance = new HashMap<LocalDate, List<AttendanceDuration.Interval>>();
    for (var fact : attendances.between(store, person, target.atDay(1), target.atEndOfMonth()))
      attendance
          .computeIfAbsent(fact.businessDate(), d -> new ArrayList<>())
          .add(new AttendanceDuration.Interval(fact.start(), fact.end()));
    var bonusAmounts = new HashMap<LocalDate, Long>();
    for (var b : records.bonusDays(store, person, target.atDay(1), target.atEndOfMonth()))
      bonusAmounts.put(b.day(), b.checkedAmount());
    var terms = records.activeTerms(store, person);
    int termIndex = 0;
    GuaranteeTerm term = null;
    long orderTotal = 0, guaranteeKnown = 0, bonusTotal = 0;
    boolean complete = true;
    var days = new ArrayList<Day>();
    for (LocalDate day = target.atDay(1);
        !day.isAfter(target.atEndOfMonth());
        day = day.plusDays(1)) {
      while (termIndex < terms.size() && !terms.get(termIndex).getEffectiveFrom().isAfter(day))
        term = terms.get(termIndex++);
      long orderAmount = amounts.getOrDefault(day, 0L),
          bonusAmount = bonusAmounts.getOrDefault(day, 0L);
      var duration = AttendanceDuration.of(attendance.getOrDefault(day, List.of()));
      var state = term == null ? null : term.getState();
      Long daily = term == null ? null : term.getDailyAmount();
      var guarantee = DailyGuarantee.calculate(state, daily, duration, orderAmount);
      if (guarantee.amount() == null) complete = false;
      else guaranteeKnown = RemunerationAmounts.add(guaranteeKnown, guarantee.amount());
      orderTotal = RemunerationAmounts.add(orderTotal, orderAmount);
      bonusTotal = RemunerationAmounts.add(bonusTotal, bonusAmount);
      days.add(
          new Day(
              day,
              orderAmount,
              state,
              daily,
              duration.closedDuration().toString(),
              duration.incomplete(),
              guarantee.status(),
              guarantee.amount(),
              bonusAmount));
    }
    Long total =
        complete
            ? RemunerationAmounts.add(
                RemunerationAmounts.add(orderTotal, guaranteeKnown), bonusTotal)
            : null;
    return new RemunerationStatementResponse(
        store,
        identity.storeName(),
        person,
        identity.name(),
        month,
        OffsetDateTime.now(),
        orderTotal,
        guaranteeKnown,
        complete ? guaranteeKnown : null,
        bonusTotal,
        total,
        List.copyOf(days),
        orders.orders(store, person, target, orderPaging),
        records
            .bonuses(store, person, target.atDay(1), target.atEndOfMonth(), bonusPaging)
            .map(
                b ->
                    new BonusItem(
                        b.getId(),
                        b.getAwardDate(),
                        b.effectiveAmount(),
                        b.getReason(),
                        b.getCancelledAt() != null)));
  }
}
