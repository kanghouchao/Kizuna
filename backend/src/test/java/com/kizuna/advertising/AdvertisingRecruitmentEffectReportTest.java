package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.advertising.domain.AdvertisingRecruitmentEffectReport;
import com.kizuna.advertising.domain.AdvertisingRecruitmentEffectReport.Cost;
import com.kizuna.advertising.domain.AdvertisingRecruitmentEffectReport.Hire;
import com.kizuna.advertising.domain.AdvertisingRecruitmentEffectReport.Retention;
import com.kizuna.advertising.domain.AdvertisingRecruitmentEffectReport.Row;
import com.kizuna.advertising.domain.AdvertisingRecruitmentEffectReport.Status;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdvertisingRecruitmentEffectReportTest {
  private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
  private static final LocalDate MAY_1 = LocalDate.of(2026, 5, 1);
  private static final LocalDate MAY_2 = LocalDate.of(2026, 5, 2);

  private static Row row(AdvertisingRecruitmentEffectReport report, String name) {
    return report.rows().stream().filter(r -> r.mediaName().equals(name)).findFirst().orElseThrow();
  }

  @Test
  void comparesExactNamesAndKeepsCostOnlyHireOnlyAndUnnamedHiresApart() {
    var report =
        AdvertisingRecruitmentEffectReport.aggregate(
            List.of(new Cost("求人A", 30000), new Cost("求人A", 1), new Cost("費用だけ", 500)),
            List.of(
                new Hire("求人A", MAY_1, null),
                new Hire("求人A", MAY_1, null),
                new Hire("求人A", MAY_1, null),
                new Hire("求人ａ", MAY_1, null),
                new Hire(" 求人A", MAY_1, null),
                new Hire(null, MAY_1, null),
                new Hire(" ", MAY_1, null)),
            LocalDate.of(2026, 5, 31),
            TOKYO);
    assertThat(report.costEntryCount()).isEqualTo(3);
    assertThat(report.recordedRecruitmentAmount()).isEqualTo(30501);
    assertThat(report.hireCount()).isEqualTo(7);
    assertThat(report.unnamedMediaHireCount()).isEqualTo(2);
    assertThat(report.rows())
        .extracting(Row::mediaName)
        .containsExactly(" 求人A", "求人A", "求人ａ", "費用だけ");
    var matched = row(report, "求人A");
    assertThat(matched.hireCount()).isEqualTo(3);
    assertThat(matched.costPerHire()).isEqualTo("10000.33");
    assertThat(matched.calculationStatus()).isEqualTo(Status.CALCULATED);
    var hireOnly = row(report, "求人ａ");
    assertThat(hireOnly.recordedRecruitmentAmount()).isNull();
    assertThat(hireOnly.costPerHire()).isNull();
    assertThat(hireOnly.calculationStatus()).isEqualTo(Status.NO_COST_RECORDS);
    var costOnly = row(report, "費用だけ");
    assertThat(costOnly.hireCount()).isZero();
    assertThat(costOnly.costPerHire()).isNull();
    assertThat(costOnly.calculationStatus()).isEqualTo(Status.NO_HIRES);
    assertThat(costOnly.retention())
        .containsExactly(new Retention(2, 0, 0), new Retention(3, 0, 0), new Retention(4, 0, 0));
  }

  @Test
  void checkpointOnTodayIsJudgeableAndEndingOnTheCheckpointIsNotRetained() {
    var checkpoint = MAY_1.plusMonths(2);
    var report =
        AdvertisingRecruitmentEffectReport.aggregate(
            List.of(),
            List.of(
                new Hire("媒体", MAY_1, null),
                new Hire("媒体", MAY_1, checkpoint.atTime(23, 59).atZone(TOKYO).toOffsetDateTime()),
                new Hire(
                    "媒体", MAY_1, checkpoint.plusDays(1).atStartOfDay(TOKYO).toOffsetDateTime()),
                new Hire("媒体", MAY_2, null)),
            checkpoint,
            TOKYO);
    assertThat(row(report, "媒体").retention())
        .containsExactly(new Retention(2, 3, 2), new Retention(3, 0, 0), new Retention(4, 0, 0));
  }

  @Test
  void endDateIsReadInTheBusinessZoneNotUtc() {
    // 東京 7/2 00:30 は UTC では 7/1。時点 7/1 より後なので定着。
    var ended = OffsetDateTime.parse("2026-07-01T15:30:00Z");
    var report =
        AdvertisingRecruitmentEffectReport.aggregate(
            List.of(), List.of(new Hire("媒体", MAY_1, ended)), LocalDate.of(2026, 9, 30), TOKYO);
    assertThat(row(report, "媒体").retention())
        .containsExactly(new Retention(2, 1, 1), new Retention(3, 1, 0), new Retention(4, 1, 0));
  }

  @Test
  void monthEndStartClampsCheckpointToTheShorterMonth() {
    var report =
        AdvertisingRecruitmentEffectReport.aggregate(
            List.of(),
            List.of(new Hire("媒体", LocalDate.of(2026, 12, 31), null)),
            LocalDate.of(2027, 2, 28),
            TOKYO);
    assertThat(row(report, "媒体").retention())
        .containsExactly(new Retention(2, 1, 1), new Retention(3, 0, 0), new Retention(4, 0, 0));
  }

  @Test
  void noRecordsYieldsNullAmountAndNoRows() {
    var report = AdvertisingRecruitmentEffectReport.aggregate(List.of(), List.of(), MAY_1, TOKYO);
    assertThat(report.recordedRecruitmentAmount()).isNull();
    assertThat(report.rows()).isEmpty();
  }
}
