package com.kizuna.remuneration.application;

import com.kizuna.order.remuneration.OrderRemunerationFacts;
import com.kizuna.remuneration.domain.AttendanceDuration;
import com.kizuna.remuneration.domain.DailyGuarantee;
import com.kizuna.remuneration.domain.GuaranteeTerm;
import com.kizuna.remuneration.domain.RemunerationAmounts;
import com.kizuna.remuneration.domain.StatementContent;
import com.kizuna.remuneration.domain.StatementContent.BonusBasis;
import com.kizuna.remuneration.domain.StatementContent.OrderBasis;
import com.kizuna.remuneration.infrastructure.RemunerationRecords;
import com.kizuna.shift.remuneration.AttendanceFacts;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 営業日ごとの明細の内容。報酬明細書と承諾の照合が同じ計算を通るよう、組み立てはここだけで行う — 二箇所で組むと要約が食い違い、正当な承諾が 409
 * に落ちる。呼び手のトランザクションのスナップショットで読む。
 */
@Component
@RequiredArgsConstructor
public class StatementContents {
  private final RemunerationRecords records;
  private final OrderRemunerationFacts orders;
  private final AttendanceFacts attendances;

  public record DayContent(
      LocalDate businessDate, StatementContent content, boolean attendanceIncomplete) {}

  public List<DayContent> between(Long store, Long person, LocalDate start, LocalDate end) {
    var orderBasis = new HashMap<LocalDate, List<OrderBasis>>();
    for (var o : orders.basis(store, person, start, end))
      orderBasis
          .computeIfAbsent(o.businessDate(), d -> new ArrayList<>())
          .add(new OrderBasis(o.orderId(), o.accruedRemuneration(), o.invalidated()));
    var attendance = new HashMap<LocalDate, List<AttendanceDuration.Interval>>();
    for (var fact : attendances.between(store, person, start, end))
      attendance
          .computeIfAbsent(fact.businessDate(), d -> new ArrayList<>())
          .add(new AttendanceDuration.Interval(fact.start(), fact.end()));
    var bonusBasis = new HashMap<LocalDate, List<BonusBasis>>();
    for (var b : records.activeBonuses(store, person, start, end))
      bonusBasis
          .computeIfAbsent(b.getAwardDate(), d -> new ArrayList<>())
          .add(new BonusBasis(b.getId(), b.getAmount()));
    var terms = records.activeTerms(store, person);
    int termIndex = 0;
    GuaranteeTerm term = null;
    var days = new ArrayList<DayContent>();
    for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
      while (termIndex < terms.size() && !terms.get(termIndex).getEffectiveFrom().isAfter(day))
        term = terms.get(termIndex++);
      var dayOrders = orderBasis.getOrDefault(day, List.of());
      var dayBonuses = bonusBasis.getOrDefault(day, List.of());
      long orderAmount = 0, bonusAmount = 0;
      for (var o : dayOrders)
        orderAmount = RemunerationAmounts.add(orderAmount, o.effectiveAmount());
      for (var b : dayBonuses) bonusAmount = RemunerationAmounts.add(bonusAmount, b.amount());
      var duration = AttendanceDuration.of(attendance.getOrDefault(day, List.of()));
      var state = term == null ? null : term.getState();
      Long daily = term == null ? null : term.getDailyAmount();
      var guarantee = DailyGuarantee.calculate(state, daily, duration, orderAmount);
      days.add(
          new DayContent(
              day,
              new StatementContent(
                  orderAmount,
                  guarantee.amount(),
                  bonusAmount,
                  dayOrders,
                  duration.closedDuration().toString(),
                  guarantee.status(),
                  state,
                  daily,
                  dayBonuses),
              duration.incomplete()));
    }
    return List.copyOf(days);
  }
}
