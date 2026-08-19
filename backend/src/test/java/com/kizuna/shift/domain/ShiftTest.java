package com.kizuna.shift.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShiftTest {

  private Shift baseShift() {
    return Shift.builder()
        .castId("c1")
        .workDate(LocalDate.of(2026, 7, 8))
        .startTime(LocalTime.of(18, 0))
        .endTime(LocalTime.of(23, 0))
        .status(ShiftStatus.TENTATIVE)
        .build();
  }

  @Test
  void apply_updatesOnlyNonNullFields() {
    Shift shift = baseShift();

    shift.apply(new ShiftPatch(null, null, null, LocalTime.of(1, 0), ShiftStatus.CONFIRMED));

    // null のフィールドは変更されない
    assertThat(shift.getCastId()).isEqualTo("c1");
    assertThat(shift.getWorkDate()).isEqualTo(LocalDate.of(2026, 7, 8));
    assertThat(shift.getStartTime()).isEqualTo(LocalTime.of(18, 0));
    // 非 null のフィールドだけ更新される
    assertThat(shift.getEndTime()).isEqualTo(LocalTime.of(1, 0));
    assertThat(shift.getStatus()).isEqualTo(ShiftStatus.CONFIRMED);
  }

  @Test
  void apply_withAllFields_replacesAll() {
    Shift shift = baseShift();

    shift.apply(
        new ShiftPatch(
            "c2",
            LocalDate.of(2026, 7, 9),
            LocalTime.of(19, 0),
            LocalTime.of(2, 0),
            ShiftStatus.CONFIRMED));

    assertThat(shift.getCastId()).isEqualTo("c2");
    assertThat(shift.getWorkDate()).isEqualTo(LocalDate.of(2026, 7, 9));
    assertThat(shift.getStartTime()).isEqualTo(LocalTime.of(19, 0));
    assertThat(shift.getEndTime()).isEqualTo(LocalTime.of(2, 0));
    assertThat(shift.getStatus()).isEqualTo(ShiftStatus.CONFIRMED);
  }

  @Test
  void publication_defaultsToPublishedAndSurvivesPatches() {
    Shift shift = baseShift();

    assertThat(shift.isPublished()).as("既定は公開可（非公開化が例外操作）").isTrue();

    shift.apply(
        new ShiftPatch(
            "c2",
            LocalDate.of(2026, 7, 9),
            LocalTime.of(19, 0),
            LocalTime.of(2, 0),
            ShiftStatus.CONFIRMED));

    assertThat(shift.isPublished()).as("部分更新は公開可否を巻き込まない").isTrue();
  }

  @Test
  void changePublication_flipsOnlyThePublicationAxis() {
    Shift shift = baseShift();

    shift.changePublication(false);

    assertThat(shift.isPublished()).isFalse();
    assertThat(shift.getStatus()).isEqualTo(ShiftStatus.TENTATIVE);
    assertThat(shift.getStartTime()).isEqualTo(LocalTime.of(18, 0));

    shift.changePublication(true);

    assertThat(shift.isPublished()).isTrue();
  }

  @Test
  @DisplayName("日付変更時刻が既定 00:00 なら勤務日はそのまま暦日として読まれる")
  void scheduledEndAt_withMidnightBoundaryKeepsCalendarSemantics() {
    Shift shift = baseShift();

    assertThat(shift.scheduledEndAt(LocalTime.MIDNIGHT))
        .isEqualTo(LocalDateTime.of(2026, 7, 8, 23, 0));
  }

  @Test
  @DisplayName("終了時刻が開始以下なら予定終了は翌日へ送られる")
  void scheduledEndAt_acrossMidnight() {
    Shift shift = baseShift();
    shift.apply(new ShiftPatch(null, null, LocalTime.of(23, 0), LocalTime.of(7, 0), null));

    assertThat(shift.scheduledEndAt(LocalTime.MIDNIGHT))
        .isEqualTo(LocalDateTime.of(2026, 7, 9, 7, 0));

    // 境界: 開始と一致する終了も「以下」なので翌日、つまり 24 時間勤務として読む。
    shift.apply(new ShiftPatch(null, null, null, LocalTime.of(23, 0), null));
    assertThat(shift.scheduledEndAt(LocalTime.MIDNIGHT))
        .isEqualTo(LocalDateTime.of(2026, 7, 9, 23, 0));
  }

  @Test
  @DisplayName("日付変更時刻より前に始まる枠は、勤務日ではなくその翌暦日に来る")
  void scheduledEndAt_whenTheShiftStartsBeforeTheDateChangeTime() {
    Shift shift = baseShift();
    shift.apply(new ShiftPatch(null, null, LocalTime.of(2, 0), LocalTime.of(7, 0), null));

    // 勤務日 07-08 は営業日。日付変更時刻 05:00 の下では 02:00 も 07:00 も暦日 07-09 に来る。
    assertThat(shift.scheduledEndAt(LocalTime.of(5, 0)))
        .isEqualTo(LocalDateTime.of(2026, 7, 9, 7, 0));

    // 対照: 日付変更時刻を跨いで終わる枠は、開始側だけが翌暦日へ送られる。
    shift.apply(new ShiftPatch(null, null, LocalTime.of(18, 0), LocalTime.of(6, 0), null));
    assertThat(shift.scheduledEndAt(LocalTime.of(5, 0)))
        .isEqualTo(LocalDateTime.of(2026, 7, 9, 6, 0));
  }

  @Test
  void apply_withEmptyPatch_changesNothing() {
    Shift shift = baseShift();

    shift.apply(new ShiftPatch(null, null, null, null, null));

    assertThat(shift.getCastId()).isEqualTo("c1");
    assertThat(shift.getWorkDate()).isEqualTo(LocalDate.of(2026, 7, 8));
    assertThat(shift.getStartTime()).isEqualTo(LocalTime.of(18, 0));
    assertThat(shift.getEndTime()).isEqualTo(LocalTime.of(23, 0));
    assertThat(shift.getStatus()).isEqualTo(ShiftStatus.TENTATIVE);
  }
}
