package com.kizuna.remuneration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.remuneration.domain.AttendanceDuration.Interval;
import com.kizuna.remuneration.domain.DailyGuarantee.Status;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuaranteeCalculationTest {
  private static final LocalDateTime START = LocalDateTime.of(2026, 9, 30, 22, 0);
  private static final LocalDate DAY = START.toLocalDate();

  private AttendanceDuration duration(long seconds, boolean open) {
    return AttendanceDuration.of(
        open
            ? List.of(
                new Interval(START, START.plusSeconds(seconds)),
                new Interval(START.plusHours(6), null))
            : List.of(new Interval(START, START.plusSeconds(seconds))));
  }

  @Test
  void exactlyFourHoursQualifiesAndOneSecondLessDoesNot() {
    assertThat(
            DailyGuarantee.calculate(GuaranteeState.ACTIVE, 10000L, duration(14400, false), 7000))
        .isEqualTo(new DailyGuarantee(Status.CALCULATED, 3000L));
    assertThat(DailyGuarantee.calculate(GuaranteeState.ACTIVE, 10000L, duration(14399, false), 0))
        .isEqualTo(new DailyGuarantee(Status.NOT_ELIGIBLE, 0L));
  }

  @Test
  void overlapsAcrossEnrollmentsCountOnceAndGapsAreNotWork() {
    var overlap =
        AttendanceDuration.of(
            List.of(
                new Interval(START, START.plusHours(3)),
                new Interval(START.plusHours(2), START.plusHours(4)),
                new Interval(START, START.plusHours(2))));
    assertThat(overlap.closedDuration()).isEqualTo(Duration.ofHours(4));
    var gap =
        AttendanceDuration.of(
            List.of(
                new Interval(START, START.plusHours(2)),
                new Interval(START.plusHours(4), START.plusHours(6))));
    assertThat(gap.closedDuration()).isEqualTo(Duration.ofHours(4));
  }

  @Test
  void unfinishedIntervalsAreNotFilledAndClosedEvidenceCanQualify() {
    assertThat(DailyGuarantee.calculate(GuaranteeState.ACTIVE, 10000L, duration(14399, true), 7000))
        .isEqualTo(new DailyGuarantee(Status.PENDING_ATTENDANCE, null));
    assertThat(
            DailyGuarantee.calculate(GuaranteeState.ACTIVE, 10000L, duration(14400, true), 7000)
                .amount())
        .isEqualTo(3000);
    assertThat(AttendanceDuration.of(List.of(new Interval(START, null))).closedDuration()).isZero();
  }

  @Test
  void stoppedOverridesUnfinishedAttendance() {
    assertThat(DailyGuarantee.calculate(GuaranteeState.STOPPED, null, duration(100, true), 0))
        .isEqualTo(new DailyGuarantee(Status.STOPPED, 0L));
  }

  @Test
  void missingSettingZeroSettingAndNoAttendanceAreDifferent() {
    assertThat(DailyGuarantee.calculate(null, null, duration(14400, false), 0))
        .isEqualTo(new DailyGuarantee(Status.NOT_CONFIGURED, null));
    assertThat(DailyGuarantee.calculate(GuaranteeState.ACTIVE, 0L, duration(14400, false), 0))
        .isEqualTo(new DailyGuarantee(Status.CALCULATED, 0L));
    assertThat(DailyGuarantee.calculate(null, null, AttendanceDuration.of(List.of()), 0))
        .isEqualTo(new DailyGuarantee(Status.NOT_ELIGIBLE, 0L));
  }

  @Test
  void highIncomeDoesNotOffsetAnotherDaysShortfall() {
    long low =
        DailyGuarantee.calculate(GuaranteeState.ACTIVE, 10000L, duration(14400, false), 7000)
            .amount();
    long high =
        DailyGuarantee.calculate(GuaranteeState.ACTIVE, 10000L, duration(14400, false), 20000)
            .amount();
    assertThat(low + high).isEqualTo(3000);
  }

  @Test
  void guaranteeCancellationFreezesTheRecordAndTimelineRejectsStaleVersion() {
    var term = new GuaranteeTerm(1L, DAY, GuaranteeState.ACTIVE, 10000L, "契約日額");
    term.replace(DAY.plusDays(1), GuaranteeState.STOPPED, null, "保証停止");
    assertThat(term.getState()).isEqualTo(GuaranteeState.STOPPED);
    term.cancel();
    assertThatThrownBy(term::cancel).isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> term.replace(DAY, GuaranteeState.ACTIVE, 0L, "訂正"))
        .isInstanceOf(ServiceException.class);
    var timeline = new GuaranteeTimeline(1L);
    timeline.advance(0);
    assertThatThrownBy(() -> timeline.advance(0)).isInstanceOf(ConflictException.class);
  }

  @Test
  void incompatibleGuaranteeFieldsAreRejected() {
    assertThatThrownBy(() -> new GuaranteeTerm(1L, DAY, GuaranteeState.STOPPED, 1L, "停止"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> new GuaranteeTerm(1L, DAY, GuaranteeState.ACTIVE, null, "日額"))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> new GuaranteeTerm(1L, DAY, GuaranteeState.ACTIVE, -1L, "日額"))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void bonusIsAPositiveAwardWithCorrectionAndCancellation() {
    var bonus = new BonusAward(1L, DAY, 1000, "付与");
    bonus.replace(DAY.plusDays(1), 2000, "訂正後の説明");
    assertThat(bonus.effectiveAmount()).isEqualTo(2000);
    bonus.cancel();
    assertThat(bonus.effectiveAmount()).isZero();
    assertThat(bonus.getAmount()).isEqualTo(2000);
    assertThatThrownBy(() -> bonus.replace(DAY, 1000, "訂正")).isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> new BonusAward(1L, DAY, 0, "付与")).isInstanceOf(ServiceException.class);
  }

  @Test
  void moneyCannotExceedExactJsonIntegerRange() {
    assertThatThrownBy(() -> RemunerationAmounts.add(RemunerationAmounts.MAX, 1))
        .isInstanceOf(ServiceException.class);
  }
}
