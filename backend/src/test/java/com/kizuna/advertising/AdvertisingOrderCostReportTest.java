package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.advertising.domain.AdvertisingMedium;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport.Cost;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport.Order;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport.Status;
import com.kizuna.shared.exception.ServiceUnavailableException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AdvertisingOrderCostReportTest {
  static AdvertisingMedium medium(String id, String name) {
    return new AdvertisingMedium(id, name);
  }

  static Cost cost(String name, long amount) {
    return new Cost(medium(name, name), amount);
  }

  static Order order(String name, boolean zero) {
    return new Order(name == null ? null : medium(name, name), zero);
  }

  @Test
  void comparesByMediumIdAndRetainsBothUnmatchedSidesAndUnnamedOrders() {
    var report =
        AdvertisingOrderCostReport.aggregate(
            List.of(cost("ABC", 100), cost("ABC", 0), cost("費用のみ", 500), cost("零", 0)),
            List.of(
                order("ABC", false),
                order("ABC", true),
                order("ABC", false),
                order("受注のみ", false),
                order("零", true),
                order(null, true),
                order(null, false)));
    assertThat(report.costEntryCount()).isEqualTo(4);
    assertThat(report.recordedSalesAmount()).isEqualTo(600);
    assertThat(report.validCompletedOrderCount()).isEqualTo(7);
    assertThat(report.zeroAmountOrderCount()).isEqualTo(3);
    assertThat(report.unnamedMediaOrderCount()).isEqualTo(2);
    assertThat(report.rows())
        .extracting(r -> r.medium().name())
        .containsExactly("ABC", "受注のみ", "費用のみ", "零");
    var matched = report.rows().getFirst();
    assertThat(matched.costPerOrder()).isEqualTo("33.33");
    assertThat(matched.calculationStatus()).isEqualTo(Status.CALCULATED);
    assertThat(matched.zeroAmountOrderCount()).isEqualTo(1);
    var onlyOrder = report.rows().get(1);
    assertThat(onlyOrder.recordedSalesAmount()).isNull();
    assertThat(onlyOrder.costPerOrder()).isNull();
    assertThat(onlyOrder.calculationStatus()).isEqualTo(Status.NO_COST_RECORDS);
    var onlyCost = report.rows().get(2);
    assertThat(onlyCost.costPerOrder()).isNull();
    assertThat(onlyCost.calculationStatus()).isEqualTo(Status.NO_VALID_ORDERS);
    assertThat(report.rows().getLast().costPerOrder()).isEqualTo("0.00");
  }

  @Test
  void sameNamedMediaStaySeparateAndSortByNameThenId() {
    // "b" はハッシュ順で "aF" より先に並ぶので、id による並べ替えがなければ順序が崩れる。
    var report =
        AdvertisingOrderCostReport.aggregate(
            List.of(
                new Cost(medium("b", "同名"), 10),
                new Cost(medium("aF", "同名"), 20),
                new Cost(medium("z", "A"), 30)),
            List.of(new Order(medium("aF", "同名"), false)));
    assertThat(report.rows()).extracting(r -> r.medium().id()).containsExactly("z", "aF", "b");
    assertThat(report.rows().get(1).costPerOrder()).isEqualTo("20.00");
    assertThat(report.rows().get(2).calculationStatus()).isEqualTo(Status.NO_VALID_ORDERS);
  }

  @Test
  void preservesMissingMonthAndRoundsHalfUpWithoutFloatingPoint() {
    var empty = AdvertisingOrderCostReport.aggregate(List.of(), List.of());
    assertThat(empty.rows()).isEmpty();
    assertThat(empty.recordedSalesAmount()).isNull();
    var orders = IntStream.range(0, 32).mapToObj(i -> order("媒体", false)).toList();
    assertThat(
            AdvertisingOrderCostReport.aggregate(List.of(cost("媒体", 1)), orders)
                .rows()
                .getFirst()
                .costPerOrder())
        .isEqualTo("0.03");
    var tie = IntStream.range(0, 8).mapToObj(i -> order("媒体", false)).toList();
    assertThat(
            AdvertisingOrderCostReport.aggregate(List.of(cost("媒体", 1)), tie)
                .rows()
                .getFirst()
                .costPerOrder())
        .isEqualTo("0.13");
    long max = 9_007_199_254_740_991L;
    assertThat(
            AdvertisingOrderCostReport.aggregate(
                    List.of(cost("媒体", max)), List.of(order("媒体", false)))
                .rows()
                .getFirst()
                .costPerOrder())
        .isEqualTo("9007199254740991.00");
    assertThatThrownBy(
            () ->
                AdvertisingOrderCostReport.aggregate(
                    List.of(cost("媒体", max), cost("別", 1)), List.of()))
        .isInstanceOf(ServiceUnavailableException.class);
  }
}
