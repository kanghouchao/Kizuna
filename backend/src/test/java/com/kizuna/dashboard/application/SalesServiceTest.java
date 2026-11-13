package com.kizuna.dashboard.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SalesServiceTest {
  @ParameterizedTest(name = "{0} の前月同時点は {1} まで")
  @CsvSource({
    "2026-11-12, 2026-10-12",
    "2026-03-31, 2026-02-28",
    "2028-03-31, 2028-02-29",
    "2026-05-31, 2026-04-30",
    "2026-01-31, 2025-12-31",
    "2026-03-01, 2026-02-01"
  })
  void previousMonthToDateEndsAtTheSameDayOrTheMonthEnd(LocalDate today, LocalDate end) {
    assertThat(SalesService.previousMonthSameDay(today)).isEqualTo(end);
  }
}
