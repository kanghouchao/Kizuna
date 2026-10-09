package com.kizuna.remuneration.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.order.reporting.OperationalFacts;
import com.kizuna.remuneration.domain.GuaranteeState;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shift.remuneration.AttendanceFacts;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemunerationReportCalculationTest {
  private static final LocalDate DAY = LocalDate.of(2026, 9, 30);

  @Test
  void overlappingIntervalsAcrossMidnightQualifyAtExactlyFourHoursOnOriginalBusinessDate() {
    var result =
        calculate(
            List.of(),
            List.of(
                interval(1, 10, DAY, "2026-09-30T22:00", "2026-10-01T01:00"),
                interval(1, 10, DAY, "2026-10-01T00:00", "2026-10-01T02:00"),
                interval(1, 10, DAY, "2026-09-30T22:00", "2026-10-01T01:00")),
            List.of(term(1, 10, DAY.minusDays(20), 8000L)),
            List.of());
    assertThat(result.days()).hasSize(1);
    var day = result.days().getFirst();
    assertThat(day.businessDate()).isEqualTo(DAY);
    assertThat(day.closedDuration()).isEqualTo("PT4H");
    assertThat(day.guaranteeStatus()).isEqualTo("CALCULATED");
    assertThat(day.guaranteeAmount()).isEqualTo(8000L);
    assertThat(day.orderAmount()).isZero();
  }

  @Test
  void supplementsUseEachDayRatherThanOffsettingHighEarningsAgainstAnotherDay() {
    var first = DAY.minusDays(1);
    var result =
        calculate(
            List.of(order("high", first, 15000, false), order("low", DAY, 2000, false)),
            List.of(closed(first, 10), closed(DAY, 10)),
            List.of(term(1, 10, first, 10000L)),
            List.of(new RemunerationReportFacts.Bonus(1L, 10L, "bonus", 1, DAY, false, 7000)));
    assertThat(result.days())
        .extracting(RemunerationReportFacts.Day::guaranteeAmount)
        .containsExactly(0L, 8000L);
    assertThat(result.days())
        .extracting(RemunerationReportFacts.Day::bonusAmount)
        .containsExactly(0L, 7000L);
  }

  @Test
  void currentOrderAndBonusVersionsRecalculateTheirOriginalDate() {
    var result =
        calculate(
            List.of(order("corrected", DAY, 3000, false), order("invalidated", DAY, 9000, true)),
            List.of(closed(DAY, 10)),
            List.of(term(1, 10, DAY, 10000L)),
            List.of(
                new RemunerationReportFacts.Bonus(1L, 10L, "corrected-bonus", 4, DAY, false, 2000),
                new RemunerationReportFacts.Bonus(1L, 10L, "cancelled-bonus", 3, DAY, true, 0)));
    var day = result.days().getFirst();
    assertThat(day.orderAmount()).isEqualTo(3000);
    assertThat(day.guaranteeAmount()).isEqualTo(7000);
    assertThat(day.bonusAmount()).isEqualTo(2000);
    assertThat(result.bonuses())
        .extracting(RemunerationReportFacts.Bonus::version)
        .containsExactly(4L, 3L);
    assertThat(result.bonuses().getLast().cancelled()).isTrue();
  }

  @Test
  void effectiveHistoryPersistsUntilNextChangeAndStopIsKnownZero() {
    var first = DAY.minusDays(2);
    var result =
        calculate(
            List.of(),
            List.of(closed(first, 10), closed(first.plusDays(1), 10), closed(DAY, 10)),
            List.of(
                term(1, 10, first.minusDays(20), 8000L),
                term(1, 10, first.plusDays(1), 9000L),
                new RemunerationReportReader.Term(
                    1L, 10L, "stop", DAY, GuaranteeState.STOPPED, null),
                term(1, 10, DAY.plusDays(1), 12000L)),
            List.of());
    assertThat(result.days())
        .extracting(RemunerationReportFacts.Day::guaranteeAmount)
        .containsExactly(8000L, 9000L, 0L);
    assertThat(result.days().getFirst().effectiveFrom()).isEqualTo(first.minusDays(20));
    assertThat(result.days().getLast().guaranteeStatus()).isEqualTo("STOPPED");
  }

  @Test
  void unknownAndKnownZeroStatesStayDistinctAndQualifiedClosedTimeSurvivesAnOpenInterval() {
    var result =
        calculate(
            List.of(),
            List.of(
                interval(1, 10, DAY, "2026-09-30T10:00", null),
                closed(DAY, 11),
                interval(1, 12, DAY, "2026-09-30T10:00", "2026-09-30T13:59"),
                interval(1, 13, DAY, "2026-09-30T10:00", null),
                closed(DAY, 14),
                interval(1, 14, DAY, "2026-09-30T15:00", null)),
            List.of(
                term(1, 10, DAY, 8000L),
                term(1, 12, DAY, 8000L),
                new RemunerationReportReader.Term(
                    1L, 13L, "stop", DAY, GuaranteeState.STOPPED, null),
                term(1, 14, DAY, 8000L)),
            List.of());
    assertThat(result.days())
        .extracting(RemunerationReportFacts.Day::guaranteeStatus)
        .containsExactly(
            "PENDING_ATTENDANCE", "NOT_CONFIGURED", "NOT_ELIGIBLE", "STOPPED", "CALCULATED");
    assertThat(result.days())
        .extracting(RemunerationReportFacts.Day::guaranteeAmount)
        .containsExactly(null, null, 0L, 0L, 8000L);
    assertThat(result.days().getLast().attendanceIncomplete()).isTrue();
  }

  @Test
  void personAndStoreAreIndependentAndBonusOnlyDaysRemainPresent() {
    var result =
        calculate(
            List.of(),
            List.of(closed(DAY, 10), interval(2, 10, DAY, "2026-09-30T10:00", "2026-09-30T14:00")),
            List.of(term(1, 10, DAY, 8000L), term(2, 10, DAY, 12000L)),
            List.of(new RemunerationReportFacts.Bonus(2L, 11L, "bonus-only", 1, DAY, false, 3000)));
    assertThat(result.days())
        .extracting(RemunerationReportFacts.Day::guaranteeAmount)
        .containsExactly(8000L, 12000L, 0L);
    assertThat(result.days().getLast().bonusAmount()).isEqualTo(3000);
    assertThat(result.days().getLast().guaranteeStatus()).isEqualTo("NOT_ELIGIBLE");
  }

  @Test
  void personDayBudgetRejectsEntireCalculationInsteadOfTruncating() {
    assertThatThrownBy(
            () ->
                RemunerationReportReader.calculate(
                    facts(List.of()),
                    List.of(closed(DAY, 10), closed(DAY, 11)),
                    List.of(),
                    List.of(),
                    1))
        .isInstanceOf(ServiceUnavailableException.class);
  }

  private RemunerationReportFacts calculate(
      List<OperationalFacts.Order> orders,
      List<AttendanceFacts.ReportInterval> intervals,
      List<RemunerationReportReader.Term> terms,
      List<RemunerationReportFacts.Bonus> bonuses) {
    return RemunerationReportReader.calculate(facts(orders), intervals, terms, bonuses, 100);
  }

  private OperationalFacts facts(List<OperationalFacts.Order> orders) {
    return new OperationalFacts(
        OffsetDateTime.parse("2026-10-01T12:00:00+09:00"),
        List.of(new OperationalFacts.Store(1L, "一号店"), new OperationalFacts.Store(2L, "二号店")),
        orders);
  }

  private OperationalFacts.Order order(String id, LocalDate day, int amount, boolean invalidated) {
    return new OperationalFacts.Order(id, 1L, day, 2, invalidated, 20000, amount, 10L);
  }

  private RemunerationReportReader.Term term(long store, long person, LocalDate day, Long amount) {
    return new RemunerationReportReader.Term(
        store,
        person,
        "term-" + store + "-" + person + "-" + day,
        day,
        GuaranteeState.ACTIVE,
        amount);
  }

  private AttendanceFacts.ReportInterval closed(LocalDate day, long person) {
    return new AttendanceFacts.ReportInterval(
        1L, person, day, day.atTime(10, 0), day.atTime(14, 0));
  }

  private AttendanceFacts.ReportInterval interval(
      long store, long person, LocalDate day, String start, String end) {
    return new AttendanceFacts.ReportInterval(
        store,
        person,
        day,
        LocalDateTime.parse(start),
        end == null ? null : LocalDateTime.parse(end));
  }
}
