package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.advertising.domain.AdvertisingMedium;
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
    return report.rows().stream()
        .filter(r -> r.medium().name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static AdvertisingMedium medium(String name) {
    return new AdvertisingMedium(name, name);
  }

  private static Cost cost(String name, long amount) {
    return new Cost(medium(name), amount);
  }

  private static Hire hire(String name, LocalDate startedOn, OffsetDateTime endedAt) {
    return new Hire(name == null ? null : medium(name), startedOn, endedAt);
  }

  @Test
  void comparesByMediumIdAndKeepsCostOnlyHireOnlyAndUnnamedHiresApart() {
    var report =
        AdvertisingRecruitmentEffectReport.aggregate(
            List.of(cost("求人A", 30000), cost("求人A", 1), cost("費用だけ", 500)),
            List.of(
                hire("求人A", MAY_1, null),
                hire("求人A", MAY_1, null),
                hire("求人A", MAY_1, null),
                hire("入店だけ", MAY_1, null),
                hire(null, MAY_1, null),
                hire(null, MAY_1, null)),
            LocalDate.of(2026, 5, 31),
            TOKYO);
    assertThat(report.costEntryCount()).isEqualTo(3);
    assertThat(report.recordedRecruitmentAmount()).isEqualTo(30501);
    assertThat(report.hireCount()).isEqualTo(6);
    assertThat(report.unnamedMediaHireCount()).isEqualTo(2);
    assertThat(report.rows())
        .extracting(r -> r.medium().name())
        .containsExactly("入店だけ", "求人A", "費用だけ");
    var matched = row(report, "求人A");
    assertThat(matched.hireCount()).isEqualTo(3);
    assertThat(matched.costPerHire()).isEqualTo("10000.33");
    assertThat(matched.calculationStatus()).isEqualTo(Status.CALCULATED);
    var hireOnly = row(report, "入店だけ");
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
  void sameNamedMediaStaySeparateAndSortByNameThenId() {
    // "b" はハッシュ順で "aF" より先に並ぶので、id による並べ替えがなければ順序が崩れる。
    var report =
        AdvertisingRecruitmentEffectReport.aggregate(
            List.of(
                new Cost(new AdvertisingMedium("b", "同名"), 10),
                new Cost(new AdvertisingMedium("aF", "同名"), 20),
                new Cost(new AdvertisingMedium("z", "A"), 30)),
            List.of(new Hire(new AdvertisingMedium("aF", "同名"), MAY_1, null)),
            MAY_1,
            TOKYO);
    assertThat(report.rows()).extracting(r -> r.medium().id()).containsExactly("z", "aF", "b");
    assertThat(report.rows().get(1).costPerHire()).isEqualTo("20.00");
    assertThat(report.rows().getLast().calculationStatus()).isEqualTo(Status.NO_HIRES);
  }

  @Test
  void checkpointOnTodayIsJudgeableAndEndingOnTheCheckpointIsNotRetained() {
    var checkpoint = MAY_1.plusMonths(2);
    var report =
        AdvertisingRecruitmentEffectReport.aggregate(
            List.of(),
            List.of(
                hire("媒体", MAY_1, null),
                hire("媒体", MAY_1, checkpoint.atTime(23, 59).atZone(TOKYO).toOffsetDateTime()),
                hire("媒体", MAY_1, checkpoint.plusDays(1).atStartOfDay(TOKYO).toOffsetDateTime()),
                hire("媒体", MAY_2, null)),
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
            List.of(), List.of(hire("媒体", MAY_1, ended)), LocalDate.of(2026, 9, 30), TOKYO);
    assertThat(row(report, "媒体").retention())
        .containsExactly(new Retention(2, 1, 1), new Retention(3, 1, 0), new Retention(4, 1, 0));
  }

  @Test
  void monthEndStartClampsCheckpointToTheShorterMonth() {
    var report =
        AdvertisingRecruitmentEffectReport.aggregate(
            List.of(),
            List.of(hire("媒体", LocalDate.of(2026, 12, 31), null)),
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
