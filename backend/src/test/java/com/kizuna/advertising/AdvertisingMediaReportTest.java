package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingMediaReport;
import com.kizuna.advertising.domain.AdvertisingMediaReport.Entry;
import com.kizuna.advertising.domain.AdvertisingMediaReport.InquiryStatus;
import com.kizuna.advertising.domain.AdvertisingMedium;
import com.kizuna.shared.exception.ServiceUnavailableException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdvertisingMediaReportTest {
  Entry entry(String name, Long count) {
    return new Entry(AdvertisingCategory.SALES, medium(name, name), 100, count);
  }

  static AdvertisingMedium medium(String id, String name) {
    return new AdvertisingMedium(id, name);
  }

  @Test
  void groupsByMediumIdSoSameNamedMediaStaySeparateAndSortsByCategoryNameThenId() {
    var result =
        AdvertisingMediaReport.aggregate(
            List.of(
                new Entry(AdvertisingCategory.RECRUITMENT, medium("m1", "A"), 7, null),
                new Entry(AdvertisingCategory.SALES, medium("m2", "同名"), 10, 1L),
                new Entry(AdvertisingCategory.SALES, medium("m1", "同名"), 20, 2L),
                new Entry(AdvertisingCategory.SALES, medium("m1", "同名"), 30, null),
                new Entry(AdvertisingCategory.SALES, medium("m3", "B"), 40, null)));
    assertThat(result.rows())
        .extracting(r -> r.category() + ":" + r.medium().id() + ":" + r.medium().name())
        .containsExactly("SALES:m3:B", "SALES:m1:同名", "SALES:m2:同名", "RECRUITMENT:m1:A");
    assertThat(result.rows().get(1))
        .returns(2L, AdvertisingMediaReport.Row::entryCount)
        .returns(50L, AdvertisingMediaReport.Row::recordedAmount);
    assertThat(result.rows().get(2).recordedAmount()).isEqualTo(10);
  }

  @Test
  void separatesMediaAndCategoriesWhilePreservingMissingAndZero() {
    var inputs = new ArrayList<Entry>();
    inputs.addAll(
        List.of(
            entry("空", null),
            entry("空", null),
            entry("零と空", 0L),
            entry("零と空", null),
            entry("一部", 5L),
            entry("一部", null),
            entry("零", 0L),
            entry("零", 0L),
            entry("合計", 5L),
            entry("合計", 7L),
            entry("ABC", 1L),
            entry("abc", 2L),
            entry("ＡＢＣ", 3L),
            entry("AB C", 4L)));
    inputs.add(new Entry(AdvertisingCategory.RECRUITMENT, medium("合計", "合計"), 500, 9L));
    var result = AdvertisingMediaReport.aggregate(inputs);
    assertThat(result.entryCount()).isEqualTo(15);
    assertThat(result.recordedTotalAmount()).isEqualTo(1900);
    assertThat(result.rows()).hasSize(10);
    assertThat(result.rows().getLast().category()).isEqualTo(AdvertisingCategory.RECRUITMENT);
    for (var row : result.rows()) {
      if (row.category() != AdvertisingCategory.SALES) continue;
      switch (row.medium().name()) {
        case "空" -> {
          assertThat(row.recordedInquiryCountSum()).isNull();
          assertThat(row.inquiryStatus()).isEqualTo(InquiryStatus.UNRECORDED);
        }
        case "零と空" -> {
          assertThat(row.recordedInquiryCountSum()).isZero();
          assertThat(row.inquiryStatus()).isEqualTo(InquiryStatus.PARTIAL);
        }
        case "一部" -> {
          assertThat(row.recordedInquiryCountSum()).isEqualTo(5);
          assertThat(row.unrecordedInquiryEntryCount()).isEqualTo(1);
        }
        case "零" -> {
          assertThat(row.recordedInquiryCountSum()).isZero();
          assertThat(row.inquiryStatus()).isEqualTo(InquiryStatus.RECORDED);
        }
        case "合計" -> {
          assertThat(row.recordedInquiryCountSum()).isEqualTo(12);
          assertThat(row.recordedInquiryEntryCount()).isEqualTo(2);
        }
        default -> assertThat(row.entryCount()).isEqualTo(1);
      }
    }
    assertThat(result.categoryTotals())
        .extracting(AdvertisingMediaReport.CategoryTotal::recordedAmount)
        .containsExactly(1400L, 500L);
  }

  @Test
  void emptyMonthHasBothCategoriesButNoInventedMedia() {
    var result = AdvertisingMediaReport.aggregate(List.of());
    assertThat(result.rows()).isEmpty();
    assertThat(result.entryCount()).isZero();
    assertThat(result.recordedTotalAmount()).isZero();
    assertThat(result.categoryTotals())
        .hasSize(2)
        .allSatisfy(c -> assertThat(c.entryCount()).isZero());
  }

  @Test
  void refusesUnsafeAmountsAndInquirySumsWithoutRounding() {
    long max = 9_007_199_254_740_991L;
    assertThat(
            AdvertisingMediaReport.aggregate(
                    List.of(new Entry(AdvertisingCategory.SALES, medium("媒体", "媒体"), max, max)))
                .recordedTotalAmount())
        .isEqualTo(max);
    assertThatThrownBy(
            () ->
                AdvertisingMediaReport.aggregate(
                    List.of(
                        new Entry(AdvertisingCategory.SALES, medium("媒体", "媒体"), max, null),
                        entry("別", null))))
        .isInstanceOf(ServiceUnavailableException.class);
    assertThatThrownBy(
            () -> AdvertisingMediaReport.aggregate(List.of(entry("媒体", max), entry("媒体", 1L))))
        .isInstanceOf(ServiceUnavailableException.class);
    assertThatThrownBy(() -> AdvertisingMediaReport.safeAdd(0, Long.MAX_VALUE))
        .isInstanceOf(ServiceUnavailableException.class);
  }
}
