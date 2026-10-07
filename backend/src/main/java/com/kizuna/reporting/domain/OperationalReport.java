package com.kizuna.reporting.domain;

import com.kizuna.order.reporting.OperationalFacts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OperationalReport(
    ReportCriteria criteria,
    OperationalFacts facts,
    long totalOrderCount,
    long invalidatedOrderCount,
    long totalFee,
    long totalRemuneration,
    List<Row> rows) {
  public static final String BASIS = "completed-orders-current-v1";

  public record Row(
      Long storeId,
      String storeName,
      String period,
      long orderCount,
      long invalidatedOrderCount,
      long totalFee,
      long totalRemuneration) {}

  private record Key(Long storeId, String period) {}

  public static OperationalReport aggregate(ReportCriteria criteria, OperationalFacts facts) {
    Map<Long, String> names = new LinkedHashMap<>();
    facts.stores().forEach(store -> names.put(store.storeId(), store.storeName()));
    Map<Key, long[]> groups = new LinkedHashMap<>();
    long[] total = new long[4];
    for (var order : facts.orders()) {
      var sums =
          groups.computeIfAbsent(
              new Key(order.storeId(), criteria.period(order.businessDate())), key -> new long[4]);
      long[] values = {
        order.invalidated() ? 0 : 1,
        order.invalidated() ? 1 : 0,
        order.invalidated() ? 0 : order.totalFee(),
        order.invalidated() ? 0 : order.remuneration()
      };
      for (int i = 0; i < values.length; i++) {
        sums[i] = Math.addExact(sums[i], values[i]);
        total[i] = Math.addExact(total[i], values[i]);
      }
    }
    var rows = new ArrayList<Row>();
    groups.forEach(
        (key, sum) ->
            rows.add(
                new Row(
                    key.storeId(),
                    names.get(key.storeId()),
                    key.period(),
                    sum[0],
                    sum[1],
                    sum[2],
                    sum[3])));
    return new OperationalReport(
        criteria, facts, total[0], total[1], total[2], total[3], List.copyOf(rows));
  }
}
