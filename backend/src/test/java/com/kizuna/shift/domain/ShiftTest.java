package com.kizuna.shift.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ShiftTest {

  private Shift baseShift() {
    return Shift.builder()
        .castId("c1")
        .workDate(LocalDate.of(2026, 7, 8))
        .startTime(LocalTime.of(18, 0))
        .endTime(LocalTime.of(23, 0))
        .status("TENTATIVE")
        .build();
  }

  @Test
  void apply_updatesOnlyNonNullFields() {
    Shift shift = baseShift();

    shift.apply(new ShiftPatch(null, null, null, LocalTime.of(1, 0), "CONFIRMED"));

    // null のフィールドは変更されない
    assertThat(shift.getCastId()).isEqualTo("c1");
    assertThat(shift.getWorkDate()).isEqualTo(LocalDate.of(2026, 7, 8));
    assertThat(shift.getStartTime()).isEqualTo(LocalTime.of(18, 0));
    // 非 null のフィールドだけ更新される
    assertThat(shift.getEndTime()).isEqualTo(LocalTime.of(1, 0));
    assertThat(shift.getStatus()).isEqualTo("CONFIRMED");
  }

  @Test
  void apply_withAllFields_replacesAll() {
    Shift shift = baseShift();

    shift.apply(
        new ShiftPatch(
            "c2", LocalDate.of(2026, 7, 9), LocalTime.of(19, 0), LocalTime.of(2, 0), "CONFIRMED"));

    assertThat(shift.getCastId()).isEqualTo("c2");
    assertThat(shift.getWorkDate()).isEqualTo(LocalDate.of(2026, 7, 9));
    assertThat(shift.getStartTime()).isEqualTo(LocalTime.of(19, 0));
    assertThat(shift.getEndTime()).isEqualTo(LocalTime.of(2, 0));
    assertThat(shift.getStatus()).isEqualTo("CONFIRMED");
  }

  @Test
  void apply_withEmptyPatch_changesNothing() {
    Shift shift = baseShift();

    shift.apply(new ShiftPatch(null, null, null, null, null));

    assertThat(shift.getCastId()).isEqualTo("c1");
    assertThat(shift.getWorkDate()).isEqualTo(LocalDate.of(2026, 7, 8));
    assertThat(shift.getStartTime()).isEqualTo(LocalTime.of(18, 0));
    assertThat(shift.getEndTime()).isEqualTo(LocalTime.of(23, 0));
    assertThat(shift.getStatus()).isEqualTo("TENTATIVE");
  }
}
