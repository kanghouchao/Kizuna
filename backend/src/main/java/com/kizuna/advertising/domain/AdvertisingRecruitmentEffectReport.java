package com.kizuna.advertising.domain;

import static com.kizuna.advertising.domain.AdvertisingMediaReport.safeAdd;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public record AdvertisingRecruitmentEffectReport(
    long costEntryCount,
    Long recordedRecruitmentAmount,
    long hireCount,
    long unnamedMediaHireCount,
    List<Row> rows) {
  public static final String BASIS = "recorded-recruitment-cost-per-hire-v1";
  public static final String MATCHING = "MEDIUM_ID";
  public static final String HIRE_BASIS = "HIRED_APPLICANT_ENROLLMENT_STARTED_IN_MONTH";
  public static final String RETENTION_BASIS = "NOT_ENDED_AFTER_STARTED_ON_PLUS_MONTHS";
  public static final String ROUNDING = "HALF_UP_2_DECIMAL_YEN";
  public static final List<Integer> RETENTION_MONTHS = List.of(2, 3, 4);

  public record Cost(AdvertisingMedium medium, long amount) {}

  /** medium が null の入店は媒体へ割り当てず、件数だけ別掲する。 */
  public record Hire(AdvertisingMedium medium, LocalDate startedOn, OffsetDateTime endedAt) {}

  public enum Status {
    CALCULATED,
    NO_COST_RECORDS,
    NO_HIRES
  }

  public record Retention(int months, long judgeableCount, long retainedCount) {}

  public record Row(
      AdvertisingMedium medium,
      long costEntryCount,
      Long recordedRecruitmentAmount,
      long hireCount,
      String costPerHire,
      Status calculationStatus,
      List<Retention> retention) {}

  /**
   * 時点 T = 入店日 + N か月が today 以前の在籍だけを可判定とし、退店日（zone での日付）が T より後か退店していなければ定着とする。 SUSPENDED
   * は退店日を持たないので在籍として数える。
   */
  public static AdvertisingRecruitmentEffectReport aggregate(
      List<Cost> costs, List<Hire> hires, LocalDate today, ZoneId zone) {
    var groups = new HashMap<String, Totals>();
    long amount = 0, unnamed = 0;
    for (var cost : costs) {
      var group = group(groups, cost.medium());
      group.costs = safeAdd(group.costs, 1);
      group.amount = safeAdd(group.amount, cost.amount());
      amount = safeAdd(amount, cost.amount());
    }
    for (var hire : hires) {
      if (hire.medium() == null) {
        unnamed = safeAdd(unnamed, 1);
        continue;
      }
      var group = group(groups, hire.medium());
      group.hires = safeAdd(group.hires, 1);
      for (int i = 0; i < RETENTION_MONTHS.size(); i++) {
        var checkpoint = hire.startedOn().plusMonths(RETENTION_MONTHS.get(i));
        if (checkpoint.isAfter(today)) continue;
        group.judgeable[i] = safeAdd(group.judgeable[i], 1);
        if (hire.endedAt() == null
            || hire.endedAt().atZoneSameInstant(zone).toLocalDate().isAfter(checkpoint))
          group.retained[i] = safeAdd(group.retained[i], 1);
      }
    }
    return new AdvertisingRecruitmentEffectReport(
        costs.size(),
        costs.isEmpty() ? null : amount,
        hires.size(),
        unnamed,
        groups.values().stream()
            .map(Totals::row)
            .sorted(Comparator.comparing(Row::medium, AdvertisingMedium.BY_NAME_THEN_ID))
            .toList());
  }

  private static Totals group(Map<String, Totals> groups, AdvertisingMedium medium) {
    return groups.computeIfAbsent(medium.id(), ignored -> new Totals(medium));
  }

  private static class Totals {
    final AdvertisingMedium medium;
    long costs, amount, hires;
    final long[] judgeable = new long[RETENTION_MONTHS.size()];
    final long[] retained = new long[RETENTION_MONTHS.size()];

    Totals(AdvertisingMedium medium) {
      this.medium = medium;
    }

    Row row() {
      var status =
          costs == 0 ? Status.NO_COST_RECORDS : hires == 0 ? Status.NO_HIRES : Status.CALCULATED;
      var ratio =
          status == Status.CALCULATED
              ? BigDecimal.valueOf(amount)
                  .divide(BigDecimal.valueOf(hires), 2, RoundingMode.HALF_UP)
                  .toPlainString()
              : null;
      var retention =
          IntStream.range(0, RETENTION_MONTHS.size())
              .mapToObj(i -> new Retention(RETENTION_MONTHS.get(i), judgeable[i], retained[i]))
              .toList();
      return new Row(medium, costs, costs == 0 ? null : amount, hires, ratio, status, retention);
    }
  }
}
