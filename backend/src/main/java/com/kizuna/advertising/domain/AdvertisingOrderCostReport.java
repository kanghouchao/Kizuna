package com.kizuna.advertising.domain;

import static com.kizuna.advertising.domain.AdvertisingMediaReport.safeAdd;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.TreeMap;

public record AdvertisingOrderCostReport(
    long costEntryCount,
    Long recordedSalesAmount,
    long validCompletedOrderCount,
    long zeroAmountOrderCount,
    long unnamedMediaOrderCount,
    List<Row> rows) {
  public static final String BASIS = "recorded-sales-cost-per-valid-order-v1";
  public static final String MATCHING = "EXACT_STORED_NAME";
  public static final String ORDER_BASIS = "VALID_COMPLETED_ORIGINAL_BUSINESS_DATE_INCLUDING_ZERO";
  public static final String ROUNDING = "HALF_UP_2_DECIMAL_YEN";

  public record Cost(String mediaName, long amount) {}

  public record Order(String mediaName, boolean zeroAmount) {}

  public enum Status {
    CALCULATED,
    NO_COST_RECORDS,
    NO_VALID_ORDERS
  }

  public record Row(
      String mediaName,
      long costEntryCount,
      Long recordedSalesAmount,
      long validCompletedOrderCount,
      long zeroAmountOrderCount,
      String costPerOrder,
      Status calculationStatus) {}

  public static AdvertisingOrderCostReport aggregate(List<Cost> costs, List<Order> orders) {
    var groups = new TreeMap<String, Totals>();
    long amount = 0, zero = 0, unnamed = 0;
    for (var cost : costs) {
      var group = groups.computeIfAbsent(cost.mediaName(), ignored -> new Totals());
      group.costs = safeAdd(group.costs, 1);
      group.amount = safeAdd(group.amount, cost.amount());
      amount = safeAdd(amount, cost.amount());
    }
    for (var order : orders) {
      if (order.zeroAmount()) zero = safeAdd(zero, 1);
      if (order.mediaName() == null || order.mediaName().isBlank()) {
        unnamed = safeAdd(unnamed, 1);
        continue;
      }
      var group = groups.computeIfAbsent(order.mediaName(), ignored -> new Totals());
      group.orders = safeAdd(group.orders, 1);
      if (order.zeroAmount()) group.zero = safeAdd(group.zero, 1);
    }
    return new AdvertisingOrderCostReport(
        costs.size(),
        costs.isEmpty() ? null : amount,
        orders.size(),
        zero,
        unnamed,
        groups.entrySet().stream().map(e -> e.getValue().row(e.getKey())).toList());
  }

  private static class Totals {
    long costs, amount, orders, zero;

    Row row(String name) {
      var status =
          costs == 0
              ? Status.NO_COST_RECORDS
              : orders == 0 ? Status.NO_VALID_ORDERS : Status.CALCULATED;
      var ratio =
          status == Status.CALCULATED
              ? BigDecimal.valueOf(amount)
                  .divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP)
                  .toPlainString()
              : null;
      return new Row(name, costs, costs == 0 ? null : amount, orders, zero, ratio, status);
    }
  }
}
