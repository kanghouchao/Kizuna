package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.advertising.domain.AdvertisingOrderCostReport;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport.Cost;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport.Order;
import com.kizuna.advertising.domain.AdvertisingOrderCostReport.Status;
import com.kizuna.shared.exception.ServiceUnavailableException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AdvertisingOrderCostReportTest {
  @Test
  void comparesExactNamesAndRetainsBothUnmatchedSidesAndUnnamedOrders() {
    var report =
        AdvertisingOrderCostReport.aggregate(
            List.of(
                new Cost("ABC", 100), new Cost("ABC", 0), new Cost("費用のみ", 500), new Cost("零", 0)),
            List.of(
                new Order("ABC", false),
                new Order("ABC", true),
                new Order("ABC", false),
                new Order("abc", false),
                new Order("ＡＢＣ", false),
                new Order(" ABC ", false),
                new Order("零", true),
                new Order(null, true),
                new Order("", false),
                new Order(" \t", false)));
    assertThat(report.costEntryCount()).isEqualTo(4);
    assertThat(report.recordedSalesAmount()).isEqualTo(600);
    assertThat(report.validCompletedOrderCount()).isEqualTo(10);
    assertThat(report.zeroAmountOrderCount()).isEqualTo(3);
    assertThat(report.unnamedMediaOrderCount()).isEqualTo(3);
    assertThat(report.rows()).hasSize(6);
    var matched =
        report.rows().stream().filter(r -> r.mediaName().equals("ABC")).findFirst().orElseThrow();
    assertThat(matched.costPerOrder()).isEqualTo("33.33");
    assertThat(matched.calculationStatus()).isEqualTo(Status.CALCULATED);
    assertThat(matched.zeroAmountOrderCount()).isEqualTo(1);
    var onlyOrder = report.rows().getFirst();
    assertThat(onlyOrder.mediaName()).isEqualTo(" ABC ");
    assertThat(onlyOrder.recordedSalesAmount()).isNull();
    assertThat(onlyOrder.costPerOrder()).isNull();
    assertThat(onlyOrder.calculationStatus()).isEqualTo(Status.NO_COST_RECORDS);
    var onlyCost =
        report.rows().stream().filter(r -> r.mediaName().equals("費用のみ")).findFirst().orElseThrow();
    assertThat(onlyCost.costPerOrder()).isNull();
    assertThat(onlyCost.calculationStatus()).isEqualTo(Status.NO_VALID_ORDERS);
    assertThat(
            report.rows().stream()
                .filter(r -> r.mediaName().equals("零"))
                .findFirst()
                .orElseThrow()
                .costPerOrder())
        .isEqualTo("0.00");
  }

  @Test
  void preservesMissingMonthAndRoundsHalfUpWithoutFloatingPoint() {
    var empty = AdvertisingOrderCostReport.aggregate(List.of(), List.of());
    assertThat(empty.rows()).isEmpty();
    assertThat(empty.recordedSalesAmount()).isNull();
    var orders = IntStream.range(0, 32).mapToObj(i -> new Order("媒体", false)).toList();
    assertThat(
            AdvertisingOrderCostReport.aggregate(List.of(new Cost("媒体", 1)), orders)
                .rows()
                .getFirst()
                .costPerOrder())
        .isEqualTo("0.03");
    var tie = IntStream.range(0, 8).mapToObj(i -> new Order("媒体", false)).toList();
    assertThat(
            AdvertisingOrderCostReport.aggregate(List.of(new Cost("媒体", 1)), tie)
                .rows()
                .getFirst()
                .costPerOrder())
        .isEqualTo("0.13");
    long max = 9_007_199_254_740_991L;
    assertThat(
            AdvertisingOrderCostReport.aggregate(
                    List.of(new Cost("媒体", max)), List.of(new Order("媒体", false)))
                .rows()
                .getFirst()
                .costPerOrder())
        .isEqualTo("9007199254740991.00");
    assertThatThrownBy(
            () ->
                AdvertisingOrderCostReport.aggregate(
                    List.of(new Cost("媒体", max), new Cost("別", 1)), List.of()))
        .isInstanceOf(ServiceUnavailableException.class);
  }
}
