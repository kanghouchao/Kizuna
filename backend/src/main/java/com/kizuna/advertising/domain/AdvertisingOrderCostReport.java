package com.kizuna.advertising.domain;

import static com.kizuna.advertising.domain.AdvertisingMediaReport.safeAdd;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record AdvertisingOrderCostReport(
    long costEntryCount,
    Long recordedSalesAmount,
    long validCompletedOrderCount,
    long zeroAmountOrderCount,
    long unnamedMediaOrderCount,
    List<Row> rows) {
  public static final String BASIS = "recorded-sales-cost-per-valid-order-v1";
  public static final String MATCHING = "MEDIUM_ID";
  public static final String ORDER_BASIS = "VALID_COMPLETED_ORIGINAL_BUSINESS_DATE_INCLUDING_ZERO";
  public static final String ROUNDING = "HALF_UP_2_DECIMAL_YEN";

  public record Cost(AdvertisingMedium medium, long amount) {}

  /** medium が null の受注は媒体未入力として件数だけ別掲する。 */
  public record Order(AdvertisingMedium medium, boolean zeroAmount) {}

  public enum Status {
    CALCULATED,
    NO_COST_RECORDS,
    NO_VALID_ORDERS
  }

  public record Row(
      AdvertisingMedium medium,
      long costEntryCount,
      Long recordedSalesAmount,
      long validCompletedOrderCount,
      long zeroAmountOrderCount,
      String costPerOrder,
      Status calculationStatus) {}

  public static AdvertisingOrderCostReport aggregate(List<Cost> costs, List<Order> orders) {
    var groups = new HashMap<String, Totals>();
    long amount = 0, zero = 0, unnamed = 0;
    for (var cost : costs) {
      var group = group(groups, cost.medium());
      group.costs = safeAdd(group.costs, 1);
      group.amount = safeAdd(group.amount, cost.amount());
      amount = safeAdd(amount, cost.amount());
    }
    for (var order : orders) {
      if (order.zeroAmount()) zero = safeAdd(zero, 1);
      if (order.medium() == null) {
        unnamed = safeAdd(unnamed, 1);
        continue;
      }
      var group = group(groups, order.medium());
      group.orders = safeAdd(group.orders, 1);
      if (order.zeroAmount()) group.zero = safeAdd(group.zero, 1);
    }
    return new AdvertisingOrderCostReport(
        costs.size(),
        costs.isEmpty() ? null : amount,
        orders.size(),
        zero,
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
    long costs, amount, orders, zero;

    Totals(AdvertisingMedium medium) {
      this.medium = medium;
    }

    Row row() {
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
      return new Row(medium, costs, costs == 0 ? null : amount, orders, zero, ratio, status);
    }
  }
}
