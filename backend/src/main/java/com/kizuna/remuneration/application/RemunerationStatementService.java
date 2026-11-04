package com.kizuna.remuneration.application;

import com.kizuna.cast.remuneration.RemunerationPersonLookup;
import com.kizuna.order.remuneration.OrderRemunerationFacts;
import com.kizuna.remuneration.application.RemunerationStatement.AcceptanceSummary;
import com.kizuna.remuneration.application.RemunerationStatement.BonusItem;
import com.kizuna.remuneration.application.RemunerationStatement.Day;
import com.kizuna.remuneration.domain.AcceptanceState;
import com.kizuna.remuneration.domain.DayAcceptance;
import com.kizuna.remuneration.domain.RemunerationAmounts;
import com.kizuna.remuneration.domain.StatementAcceptance;
import com.kizuna.remuneration.domain.StatementAcceptanceRepository;
import com.kizuna.remuneration.infrastructure.RemunerationRecords;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.persistence.Timestamps;
import com.kizuna.user.application.ActorIdentityService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RemunerationStatementService {
  private final RemunerationRecords records;
  private final StatementAcceptanceRepository acceptances;
  private final RemunerationPersonLookup people;
  private final OrderRemunerationFacts orders;
  private final StatementContents contents;
  private final BusinessDateService businessDates;
  private final ActorIdentityService actors;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public RemunerationStatement store(
      Long storeId, Long person, String month, Pageable orderPageable, Pageable bonusPageable) {
    return read(storeId, person, month, orderPageable, bonusPageable);
  }

  /** 本人の明細。認証主体の本人と店舗の在籍の歴史を照合し（無ければ 404）、全照会を明示した本人・店舗に限る。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public RemunerationStatement self(
      String actor, Long store, String month, Pageable orderPageable, Pageable bonusPageable) {
    return read(
        store, people.self(actors.requireUserId(actor)), month, orderPageable, bonusPageable);
  }

  private RemunerationStatement read(
      Long store, Long person, String month, Pageable orderPageable, Pageable bonusPageable) {
    RemunerationInput.store(store);
    RemunerationInput.person(person);
    var target = RemunerationInput.month(month);
    var identity = people.require(store, person);
    LocalDate start = target.atDay(1), end = target.atEndOfMonth();
    // 営業日の終了は明細に対して一度だけ決める。日ごとに引くと境界を跨いだ応答の中で承諾可否の基準がずれる。
    var current = businessDates.currentBusinessDate();
    var accepted = new HashMap<LocalDate, List<StatementAcceptance>>();
    for (var a : acceptances.findInPeriod(store, person, start, end))
      accepted.computeIfAbsent(a.getBusinessDate(), d -> new ArrayList<>()).add(a);
    long orderTotal = 0, guaranteeKnown = 0, bonusTotal = 0;
    int acceptedDays = 0, acceptableDays = 0;
    boolean complete = true;
    var days = new ArrayList<Day>();
    for (var dayContent : contents.between(store, person, start, end)) {
      var day = dayContent.businessDate();
      var content = dayContent.content();
      var acceptance =
          DayAcceptance.resolve(
              content, day.isBefore(current), accepted.getOrDefault(day, List.of()));
      if (content.guaranteeAmount() == null) complete = false;
      else guaranteeKnown = RemunerationAmounts.add(guaranteeKnown, content.guaranteeAmount());
      orderTotal = RemunerationAmounts.add(orderTotal, content.orderAmount());
      bonusTotal = RemunerationAmounts.add(bonusTotal, content.bonusAmount());
      if (acceptance.state() != AcceptanceState.NOT_ACCEPTABLE) acceptableDays++;
      if (acceptance.state() == AcceptanceState.ACCEPTED) acceptedDays++;
      days.add(
          new Day(
              day,
              content.orderAmount(),
              content.guaranteeState(),
              content.dailyAmount(),
              content.closedDuration(),
              dayContent.attendanceIncomplete(),
              content.guaranteeStatus(),
              content.guaranteeAmount(),
              content.bonusAmount(),
              acceptance.state(),
              acceptance.reason(),
              acceptance.contentDigest(),
              AcceptanceSummary.of(acceptance.latest())));
    }
    Long total =
        complete
            ? RemunerationAmounts.add(
                RemunerationAmounts.add(orderTotal, guaranteeKnown), bonusTotal)
            : null;
    return new RemunerationStatement(
        store,
        identity.storeName(),
        person,
        identity.name(),
        month,
        Timestamps.now(),
        orderTotal,
        guaranteeKnown,
        complete ? guaranteeKnown : null,
        bonusTotal,
        total,
        acceptedDays,
        acceptableDays,
        List.copyOf(days),
        orders.orders(store, person, target, orderPageable),
        records
            .bonuses(store, person, start, end, bonusPageable)
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
