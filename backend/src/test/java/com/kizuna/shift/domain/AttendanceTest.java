package com.kizuna.shift.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttendanceTest {

  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 18);
  private static final LocalDateTime START = LocalDateTime.of(2026, 8, 18, 19, 0);
  private static final Long ACTOR_ID = 7L;

  private Attendance recorded() {
    return Attendance.record(
        "cast-1", BUSINESS_DATE, START, START.plusHours(5), "shift-1", "1番待機室", ACTOR_ID);
  }

  @Test
  @DisplayName("実終了が実開始以前の記録を拒否すること")
  void rejectsEndNotAfterStart() {
    assertThatThrownBy(
            () -> Attendance.record("cast-1", BUSINESS_DATE, START, START, null, null, ACTOR_ID))
        .isInstanceOf(AttendanceStateException.class)
        .hasMessageContaining("実終了");

    assertThatThrownBy(
            () ->
                Attendance.record(
                    "cast-1", BUSINESS_DATE, START, START.minusMinutes(1), null, null, ACTOR_ID))
        .isInstanceOf(AttendanceStateException.class);
  }

  @Test
  @DisplayName("実終了の未記入は許され、日跨ぎの終了も記録できること")
  void allowsNullEndAndOvernightEnd() {
    assertThat(
            Attendance.record("cast-1", BUSINESS_DATE, START, null, null, null, ACTOR_ID)
                .getActualEndAt())
        .isNull();

    LocalDateTime nextMorning = START.plusHours(8);
    assertThat(
            Attendance.record("cast-1", BUSINESS_DATE, START, nextMorning, null, null, ACTOR_ID)
                .getActualEndAt())
        .isEqualTo(nextMorning);
  }

  @Test
  @DisplayName("訂正が営業日・時刻・待機場所を置き換え、空へ戻す訂正も通ること")
  void correctionReplacesTheCorrectableFacet() {
    Attendance attendance = recorded();
    LocalDate corrected = BUSINESS_DATE.minusDays(1);

    attendance.correct(corrected, START.minusHours(1), null, null, 99L);

    assertThat(attendance.getBusinessDate()).isEqualTo(corrected);
    assertThat(attendance.getActualStartAt()).isEqualTo(START.minusHours(1));
    assertThat(attendance.getActualEndAt()).isNull();
    assertThat(attendance.getWaitingPlace()).isNull();
    assertThat(attendance.getUpdatedBy()).isEqualTo(99L);
  }

  @Test
  @DisplayName("訂正でも実終了が実開始以前なら拒否されること")
  void correctionRejectsEndNotAfterStart() {
    Attendance attendance = recorded();

    assertThatThrownBy(() -> attendance.correct(BUSINESS_DATE, START, START, null, 99L))
        .isInstanceOf(AttendanceStateException.class);
  }

  @Test
  @DisplayName("取消が標記だけを付け、二度目の取消と取消後の訂正を拒否すること")
  void cancellationIsMarkedOnceAndFreezesTheRow() {
    Attendance attendance = recorded();
    OffsetDateTime at = OffsetDateTime.now();

    attendance.cancel(ACTOR_ID, at);

    assertThat(attendance.isCancelled()).isTrue();
    assertThat(attendance.getCancelledAt()).isEqualTo(at);
    assertThat(attendance.getCancelledBy()).isEqualTo(ACTOR_ID);
    assertThat(attendance.getActualStartAt()).isEqualTo(START);

    assertThatThrownBy(() -> attendance.cancel(ACTOR_ID, at))
        .isInstanceOf(AttendanceStateException.class);
    assertThatThrownBy(
            () -> attendance.correct(BUSINESS_DATE, START, START.plusHours(1), null, ACTOR_ID))
        .isInstanceOf(AttendanceStateException.class);
  }

  @Test
  @DisplayName("訂正履歴が編集前の姿を、訂正口を持たない項目まで写し取ること")
  void snapshotCapturesThePreEditRow() {
    Attendance attendance = recorded();
    OffsetDateTime correctedAt = OffsetDateTime.now();

    AttendanceCorrection snapshot = AttendanceCorrection.snapshotOf(attendance, 99L, correctedAt);
    attendance.correct(BUSINESS_DATE.minusDays(1), START.minusHours(2), null, "別室", 99L);

    assertThat(snapshot.getCastId()).isEqualTo("cast-1");
    assertThat(snapshot.getShiftId()).isEqualTo("shift-1");
    assertThat(snapshot.getBusinessDate()).isEqualTo(BUSINESS_DATE);
    assertThat(snapshot.getActualStartAt()).isEqualTo(START);
    assertThat(snapshot.getActualEndAt()).isEqualTo(START.plusHours(5));
    assertThat(snapshot.getWaitingPlace()).isEqualTo("1番待機室");
    assertThat(snapshot.getCorrectedBy()).isEqualTo(99L);
    assertThat(snapshot.getCorrectedAt()).isEqualTo(correctedAt);
  }
}
