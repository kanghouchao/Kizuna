package com.kizuna.reporting.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.advertising.reporting.AdvertisingReportFacts;
import com.kizuna.order.reporting.OperationalFacts;
import com.kizuna.remuneration.reporting.RemunerationReportFacts;
import com.kizuna.shared.exception.ServiceUnavailableException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record OperationalReport(
    ReportCriteria criteria,
    OperationalFacts facts,
    long totalOrderCount,
    long invalidatedOrderCount,
    long totalFee,
    long totalRemuneration,
    List<Row> rows,
    RemunerationReportFacts remunerationFacts,
    Amounts remuneration,
    AdvertisingReportFacts advertisingFacts,
    AdvertisingAmounts advertising) {
  public static final String BASIS = "completed-orders-current-v1";

  public String basis() {
    if (advertising != null)
      return remuneration == null
          ? "completed-orders-advertising-current-v3"
          : "completed-orders-remuneration-advertising-current-v3";
    return remuneration == null ? BASIS : "completed-orders-remuneration-current-v2";
  }

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Amounts(
      long knownGuaranteeTotal,
      Long guaranteeTotal,
      long bonusTotal,
      Long total,
      long pendingAttendanceDays,
      long notConfiguredDays) {}

  public record Row(
      Long storeId,
      String storeName,
      String period,
      long orderCount,
      long invalidatedOrderCount,
      long totalFee,
      long totalRemuneration,
      @JsonInclude(JsonInclude.Include.NON_NULL) Amounts remuneration,
      @JsonInclude(JsonInclude.Include.NON_NULL) AdvertisingAmounts advertising) {}

  private record Key(Long storeId, String period) {}

  public static OperationalReport aggregate(ReportCriteria criteria, OperationalFacts facts) {
    return aggregate(criteria, facts, null);
  }

  public static OperationalReport aggregate(
      ReportCriteria criteria, OperationalFacts facts, RemunerationReportFacts remuneration) {
    return aggregate(criteria, facts, remuneration, null);
  }

  public static OperationalReport aggregate(
      ReportCriteria criteria,
      OperationalFacts facts,
      RemunerationReportFacts remuneration,
      AdvertisingReportFacts advertising) {
    String advertisingStatus = AdvertisingAmounts.inapplicableStatus(criteria);
    Map<Long, String> names = new HashMap<>();
    facts.stores().forEach(store -> names.put(store.storeId(), store.storeName()));
    Map<Key, Totals> groups =
        new TreeMap<>(Comparator.comparing(Key::storeId).thenComparing(Key::period));
    var total = new Totals();
    for (var order : facts.orders()) {
      groups
          .computeIfAbsent(
              new Key(order.storeId(), criteria.period(order.businessDate())), key -> new Totals())
          .order(order);
      total.order(order);
    }
    if (remuneration != null)
      for (var day : remuneration.days()) {
        groups
            .computeIfAbsent(
                new Key(day.storeId(), criteria.period(day.businessDate())), key -> new Totals())
            .day(day);
        total.day(day);
      }
    if (advertising != null && advertisingStatus == null)
      for (var cost : advertising.costs()) {
        groups
            .computeIfAbsent(
                new Key(cost.storeId(), criteria.groupBy().equals("month") ? cost.month() : ""),
                key -> new Totals())
            .cost(cost);
        total.cost(cost);
      }
    var rows = new ArrayList<Row>();
    groups.forEach(
        (key, sum) ->
            rows.add(
                new Row(
                    key.storeId(),
                    names.get(key.storeId()),
                    key.period(),
                    sum.orders,
                    sum.invalidated,
                    sum.fee,
                    sum.orderAmount,
                    remuneration == null ? null : sum.amounts(),
                    advertising == null ? null : sum.advertising(advertisingStatus))));
    return new OperationalReport(
        criteria,
        facts,
        total.orders,
        total.invalidated,
        total.fee,
        total.orderAmount,
        List.copyOf(rows),
        remuneration,
        remuneration == null ? null : total.amounts(),
        advertising,
        advertising == null ? null : total.advertising(advertisingStatus));
  }

  private static final class Totals {
    long orders,
        invalidated,
        fee,
        orderAmount,
        guaranteeKnown,
        bonuses,
        pendingAttendance,
        notConfigured,
        advertisingEntries,
        sales,
        recruitment;

    void order(OperationalFacts.Order order) {
      if (order.invalidated()) {
        invalidated = add(invalidated, 1);
        return;
      }
      orders = add(orders, 1);
      fee = add(fee, order.totalFee());
      orderAmount = add(orderAmount, order.remuneration());
    }

    void day(RemunerationReportFacts.Day day) {
      if (day.guaranteeAmount() != null)
        guaranteeKnown = add(guaranteeKnown, day.guaranteeAmount());
      bonuses = add(bonuses, day.bonusAmount());
      if (day.guaranteeStatus().equals("PENDING_ATTENDANCE"))
        pendingAttendance = add(pendingAttendance, 1);
      if (day.guaranteeStatus().equals("NOT_CONFIGURED")) notConfigured = add(notConfigured, 1);
    }

    void cost(AdvertisingReportFacts.Cost cost) {
      advertisingEntries = add(advertisingEntries, 1);
      switch (cost.category()) {
        case "SALES" -> sales = add(sales, cost.amount());
        case "RECRUITMENT" -> recruitment = add(recruitment, cost.amount());
        default -> throw new ServiceUnavailableException("広告費区分を確認できません");
      }
    }

    AdvertisingAmounts advertising(String status) {
      if (status != null) return new AdvertisingAmounts(status, null, null, null, null);
      return new AdvertisingAmounts(
          advertisingEntries == 0 ? "NO_RECORDS" : "RECORDED",
          advertisingEntries,
          sales,
          recruitment,
          add(sales, recruitment));
    }

    Amounts amounts() {
      boolean complete = pendingAttendance == 0 && notConfigured == 0;
      long knownTotal = add(add(orderAmount, guaranteeKnown), bonuses);
      return new Amounts(
          guaranteeKnown,
          complete ? guaranteeKnown : null,
          bonuses,
          complete ? knownTotal : null,
          pendingAttendance,
          notConfigured);
    }
  }

  private static long add(long a, long b) {
    if (a < 0 || b < 0 || a > 9_007_199_254_740_991L - b)
      throw new ServiceUnavailableException("集計金額が扱える範囲を超えています。条件を絞ってください");
    return a + b;
  }
}
