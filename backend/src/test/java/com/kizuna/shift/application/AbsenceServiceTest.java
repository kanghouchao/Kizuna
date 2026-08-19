package com.kizuna.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shift.api.dto.AbsenceResponse;
import com.kizuna.shift.domain.AttendanceRepository;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 欠勤導出の門の単体固定。
 *
 * <p>日付変更時刻は 05:00 に置く。既定の 00:00 では営業日が暦日と一致し、「営業日の終了」と「予定終了の経過」が 同時に成立してしまうため、二つの門を区別できない。営業日
 * 2026-08-18 は 08-19 05:00 に終わるので、 その後に予定終了が来る日跨ぎのシフトだけが二つ目の門を意味あるものにする。
 */
@ExtendWith(MockitoExtension.class)
class AbsenceServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 18);
  private static final String CAST_ID = "cast-1";

  @Mock private ShiftRepository shiftRepository;
  @Mock private AttendanceRepository attendanceRepository;
  @Mock private SystemConfigService systemConfigService;

  private AbsenceService serviceAt(LocalDateTime now) {
    lenient().when(systemConfigService.getConfigValue(any())).thenReturn(Optional.of("05:00"));
    Clock clock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
    return new AbsenceService(
        shiftRepository,
        attendanceRepository,
        new BusinessDateService(systemConfigService, clock),
        clock);
  }

  private void givenConfirmedShifts(Shift... shifts) {
    lenient()
        .when(shiftRepository.findByWorkDateAndStatus(BUSINESS_DATE, ShiftStatus.CONFIRMED))
        .thenReturn(List.of(shifts));
  }

  private void givenActiveAttendanceCastIds(String... castIds) {
    lenient()
        .when(attendanceRepository.findCastIdsWithActiveAttendanceOn(BUSINESS_DATE))
        .thenReturn(Set.of(castIds));
  }

  private static Shift shiftOf(String castId, LocalTime startTime, LocalTime endTime) {
    return Shift.builder()
        .castId(castId)
        .workDate(BUSINESS_DATE)
        .startTime(startTime)
        .endTime(endTime)
        .status(ShiftStatus.CONFIRMED)
        .build();
  }

  private static List<String> castIdsOf(List<AbsenceResponse> rows) {
    return rows.stream().map(AbsenceResponse::getCastId).toList();
  }

  @Test
  @DisplayName("営業日が終わっていなければ、予定終了を過ぎたシフトでも欠勤にならない")
  void doesNotDeriveBeforeTheBusinessDateEnds() {
    givenConfirmedShifts(shiftOf(CAST_ID, LocalTime.of(18, 0), LocalTime.of(21, 0)));

    // 営業日 08-18 は 08-19 05:00 まで続く。予定終了（08-18 21:00）は過ぎているが飛び込みの記録がまだ来うる。
    assertThat(serviceAt(LocalDateTime.of(2026, 8, 19, 2, 30)).list(BUSINESS_DATE)).isEmpty();
  }

  @Test
  @DisplayName("営業日が終わっていても、予定終了が未到来の日跨ぎシフトは欠勤に数えない")
  void doesNotDeriveWhileTheShiftIsStillRunning() {
    givenConfirmedShifts(shiftOf(CAST_ID, LocalTime.of(23, 0), LocalTime.of(7, 0)));

    // 営業日は 08-19 05:00 に終わったが、予定終了は 08-19 07:00 — まだ勤務中である。
    assertThat(serviceAt(LocalDateTime.of(2026, 8, 19, 6, 0)).list(BUSINESS_DATE)).isEmpty();

    // 正向対照: 予定終了を過ぎれば同じシフトが欠勤として出る。
    assertThat(castIdsOf(serviceAt(LocalDateTime.of(2026, 8, 19, 7, 0)).list(BUSINESS_DATE)))
        .containsExactly(CAST_ID);
  }

  @Test
  @DisplayName("日付変更時刻より前に始まる枠は翌暦日に来るので、その予定終了まで欠勤にならない")
  void doesNotDeriveWhileAShiftStartingAfterMidnightIsStillRunning() {
    // 勤務日は暦日ではなく営業日。日付変更時刻 05:00 の下では、営業日 08-18 の 02:00 は暦日 08-19 に来る。
    givenConfirmedShifts(shiftOf(CAST_ID, LocalTime.of(2, 0), LocalTime.of(7, 0)));

    // 営業日は 08-19 05:00 に終わったが、この枠は 08-19 07:00 まで勤務中である。
    assertThat(serviceAt(LocalDateTime.of(2026, 8, 19, 6, 0)).list(BUSINESS_DATE)).isEmpty();

    // 正向対照: 予定終了を過ぎれば欠勤として出る。
    assertThat(castIdsOf(serviceAt(LocalDateTime.of(2026, 8, 19, 7, 0)).list(BUSINESS_DATE)))
        .containsExactly(CAST_ID);
  }

  @Test
  @DisplayName("同一営業日に複数枠があるとき、最遅の予定終了まで欠勤にならない")
  void waitsForTheLatestScheduledEndAmongTheDaysShifts() {
    givenConfirmedShifts(
        shiftOf(CAST_ID, LocalTime.of(12, 0), LocalTime.of(15, 0)),
        shiftOf(CAST_ID, LocalTime.of(23, 0), LocalTime.of(7, 0)));

    // 第一枠（08-18 15:00 終了）は過ぎているが、第二枠が進行中。
    assertThat(serviceAt(LocalDateTime.of(2026, 8, 19, 6, 0)).list(BUSINESS_DATE)).isEmpty();

    assertThat(castIdsOf(serviceAt(LocalDateTime.of(2026, 8, 19, 8, 0)).list(BUSINESS_DATE)))
        .containsExactly(CAST_ID);
  }

  @Test
  @DisplayName("門は当該キャストの枠だけで決まり、他キャストの進行中シフトに引きずられない")
  void gateIsPerCastNotStoreWide() {
    givenConfirmedShifts(
        shiftOf(CAST_ID, LocalTime.of(12, 0), LocalTime.of(15, 0)),
        shiftOf("cast-2", LocalTime.of(23, 0), LocalTime.of(7, 0)));

    // 08-19 06:00 — cast-2 は勤務中だが、cast-1 の枠はとうに終わっている。
    assertThat(castIdsOf(serviceAt(LocalDateTime.of(2026, 8, 19, 6, 0)).list(BUSINESS_DATE)))
        .containsExactly(CAST_ID);
  }

  @Test
  @DisplayName("実績のあるキャストは欠勤にならず、結果はキャスト id の昇順で返る")
  void excludesCastsWithAttendanceAndOrdersByCastId() {
    givenConfirmedShifts(
        shiftOf("cast-3", LocalTime.of(18, 0), LocalTime.of(21, 0)),
        shiftOf(CAST_ID, LocalTime.of(18, 0), LocalTime.of(21, 0)),
        shiftOf("cast-2", LocalTime.of(18, 0), LocalTime.of(21, 0)));
    givenActiveAttendanceCastIds("cast-2");

    List<AbsenceResponse> absences =
        serviceAt(LocalDateTime.of(2026, 8, 19, 8, 0)).list(BUSINESS_DATE);

    assertThat(castIdsOf(absences)).containsExactly(CAST_ID, "cast-3");
    assertThat(absences.getFirst().getBusinessDate()).isEqualTo(BUSINESS_DATE);
  }

  @Test
  @DisplayName("確定シフトが無い営業日は、実績が無くても欠勤を生まない")
  void derivesNothingWithoutConfirmedShifts() {
    givenConfirmedShifts();

    assertThat(serviceAt(LocalDateTime.of(2026, 8, 19, 8, 0)).list(BUSINESS_DATE)).isEmpty();
  }

  @Test
  @DisplayName("欠勤の母集合は確定シフトに限られる")
  void onlyConfirmedShiftsAreQueried() {
    when(shiftRepository.findByWorkDateAndStatus(BUSINESS_DATE, ShiftStatus.CONFIRMED))
        .thenReturn(List.of(shiftOf(CAST_ID, LocalTime.of(18, 0), LocalTime.of(21, 0))));

    assertThat(castIdsOf(serviceAt(LocalDateTime.of(2026, 8, 19, 8, 0)).list(BUSINESS_DATE)))
        .containsExactly(CAST_ID);
  }
}
