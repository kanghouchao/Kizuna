package com.kizuna.settings.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusinessDateServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

  @Mock private SystemConfigService systemConfigService;

  private BusinessDateService serviceAt(String dateChangeTime, LocalDateTime now) {
    return serviceAt(dateChangeTime, now.atZone(ZONE).toInstant(), ZONE);
  }

  private BusinessDateService serviceAt(String dateChangeTime, Instant now, ZoneId zone) {
    when(systemConfigService.getConfigValue(BusinessDateService.DATE_CHANGE_TIME_KEY))
        .thenReturn(Optional.ofNullable(dateChangeTime));
    return new BusinessDateService(systemConfigService, Clock.fixed(now, zone));
  }

  @Test
  @DisplayName("日付変更時刻より前の深夜帯は前の暦日が営業日")
  void beforeDateChangeTimeBelongsToPreviousCalendarDay() {
    BusinessDateService service = serviceAt("05:00", LocalDateTime.of(2026, 8, 18, 2, 30));

    assertThat(service.currentBusinessDate()).isEqualTo(LocalDate.of(2026, 8, 17));
  }

  @Test
  @DisplayName("日付変更時刻ちょうどから当日の営業日が始まる")
  void dateChangeTimeItselfStartsTheNewBusinessDate() {
    BusinessDateService service = serviceAt("05:00", LocalDateTime.of(2026, 8, 18, 5, 0));

    assertThat(service.currentBusinessDate()).isEqualTo(LocalDate.of(2026, 8, 18));
  }

  @Test
  @DisplayName("初期値 00:00 では営業日が暦日と一致する（導入前の挙動）")
  void midnightKeepsCalendarDaySemantics() {
    assertThat(serviceAt("00:00", LocalDateTime.of(2026, 8, 18, 0, 0)).currentBusinessDate())
        .isEqualTo(LocalDate.of(2026, 8, 18));
    assertThat(serviceAt("00:00", LocalDateTime.of(2026, 8, 18, 23, 59)).currentBusinessDate())
        .isEqualTo(LocalDate.of(2026, 8, 18));
  }

  @Test
  @DisplayName("未設定・解釈できない値は 00:00 へ倒れる")
  void unsetOrUnparsableFallsBackToMidnight() {
    assertThat(serviceAt(null, LocalDateTime.of(2026, 8, 18, 2, 30)).currentBusinessDate())
        .isEqualTo(LocalDate.of(2026, 8, 18));
    assertThat(serviceAt("", LocalDateTime.of(2026, 8, 18, 2, 30)).currentBusinessDate())
        .isEqualTo(LocalDate.of(2026, 8, 18));
    assertThat(serviceAt("25時", LocalDateTime.of(2026, 8, 18, 2, 30)).currentBusinessDate())
        .isEqualTo(LocalDate.of(2026, 8, 18));
  }

  @Test
  @DisplayName("暦日付き時刻から営業日を求める（飛び込み実績の帰属判定）")
  void businessDateOfExplicitInstant() {
    BusinessDateService service = serviceAt("05:00", LocalDateTime.of(2026, 8, 18, 12, 0));

    assertThat(service.businessDateOf(LocalDateTime.of(2026, 8, 19, 4, 59)))
        .isEqualTo(LocalDate.of(2026, 8, 18));
    assertThat(service.businessDateOf(LocalDateTime.of(2026, 8, 19, 5, 0)))
        .isEqualTo(LocalDate.of(2026, 8, 19));
  }

  @Test
  @DisplayName("営業日の終了は現在の営業日より前かどうか。営業日当日は開始時刻を過ぎても終了していない")
  void hasEndedComparesAgainstCurrentBusinessDate() {
    // 暦日は 8/18 だが日付変更時刻前なので、現在の営業日は 8/17。
    BusinessDateService service = serviceAt("05:00", LocalDateTime.of(2026, 8, 18, 2, 30));

    assertThat(service.hasEnded(LocalDate.of(2026, 8, 16))).isTrue();
    assertThat(service.hasEnded(LocalDate.of(2026, 8, 17))).isFalse();
    assertThat(service.hasEnded(LocalDate.of(2026, 8, 18))).isFalse();
  }

  @Test
  @DisplayName("夏時間の戻しで営業日が巻き戻らないこと（同じ壁時計時刻が 2 度来る帯）")
  void businessDateStaysMonotonicAcrossDaylightSavingFallback() {
    // America/New_York は 2026-11-01 の 02:00 EDT で 01:00 EST へ戻る。日付変更時刻 01:30 は
    // その重なりの中にあり、壁時計の時刻同士で比べると 01:05 EST（後の瞬時点）が「前」に見えて
    // 営業日が 10-31 へ巻き戻る。
    ZoneId newYork = ZoneId.of("America/New_York");
    Instant beforeFallback = Instant.parse("2026-11-01T05:45:00Z");
    Instant afterFallback = Instant.parse("2026-11-01T06:05:00Z");

    assertThat(serviceAt("01:30", beforeFallback, newYork).currentBusinessDate())
        .isEqualTo(LocalDate.of(2026, 11, 1));
    assertThat(serviceAt("01:30", afterFallback, newYork).currentBusinessDate())
        .as("後から来た瞬時点の営業日が前へ戻らないこと")
        .isEqualTo(LocalDate.of(2026, 11, 1));
  }

  @Test
  @DisplayName("夏時間の進みで存在しない日付変更時刻は前へ送られること")
  void nonExistentDateChangeTimeIsShiftedForward() {
    // 2026-03-08 の 02:00 EST から 03:00 EDT へ飛ぶため、02:30 という壁時計時刻は存在しない。
    // 境界は 03:30 EDT（= 07:30Z）へ送られ、その手前はまだ前営業日。
    ZoneId newYork = ZoneId.of("America/New_York");

    assertThat(
            serviceAt("02:30", Instant.parse("2026-03-08T07:29:00Z"), newYork)
                .currentBusinessDate())
        .isEqualTo(LocalDate.of(2026, 3, 7));
    assertThat(
            serviceAt("02:30", Instant.parse("2026-03-08T07:30:00Z"), newYork)
                .currentBusinessDate())
        .isEqualTo(LocalDate.of(2026, 3, 8));
  }
}
