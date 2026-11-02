package com.kizuna.advertising.domain;

import com.kizuna.shared.exception.ServiceUnavailableException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;

public record AdvertisingMediaReport(
    long entryCount, long recordedTotalAmount, List<CategoryTotal> categoryTotals, List<Row> rows) {
  public static final String BASIS = "advertising-media-records-v1";
  public static final String MATCHING = "MEDIUM_ID";
  public static final String INQUIRY_BASIS = "MANUAL_RECORDED_SUM_NOT_DEDUPLICATED";

  public AdvertisingMediaReport {
    categoryTotals = List.copyOf(categoryTotals);
    rows = List.copyOf(rows);
  }

  public record Entry(
      AdvertisingCategory category, AdvertisingMedium medium, long amount, Long inquiryCount) {}

  public record CategoryTotal(AdvertisingCategory category, long entryCount, long recordedAmount) {}

  public record Row(
      AdvertisingCategory category,
      AdvertisingMedium medium,
      long entryCount,
      long recordedAmount,
      long recordedInquiryEntryCount,
      long unrecordedInquiryEntryCount,
      Long recordedInquiryCountSum,
      InquiryStatus inquiryStatus) {}

  public enum InquiryStatus {
    UNRECORDED,
    PARTIAL,
    RECORDED
  }

  private record Key(AdvertisingCategory category, String mediumId) {}

  private static final class Accumulator {
    AdvertisingMedium medium;
    long entries, amount, recorded, sum;

    void add(Entry entry) {
      entries = safeAdd(entries, 1);
      amount = safeAdd(amount, entry.amount());
      if (entry.inquiryCount() != null) {
        recorded = safeAdd(recorded, 1);
        sum = safeAdd(sum, entry.inquiryCount());
      }
    }

    Row row(AdvertisingCategory category) {
      return new Row(
          category,
          medium,
          entries,
          amount,
          recorded,
          entries - recorded,
          recorded == 0 ? null : sum,
          recorded == 0
              ? InquiryStatus.UNRECORDED
              : recorded == entries ? InquiryStatus.RECORDED : InquiryStatus.PARTIAL);
    }
  }

  public static AdvertisingMediaReport aggregate(List<Entry> entries) {
    var groups = new LinkedHashMap<Key, Accumulator>();
    var categories = new EnumMap<AdvertisingCategory, Accumulator>(AdvertisingCategory.class);
    for (var category : AdvertisingCategory.values()) categories.put(category, new Accumulator());
    long amount = 0;
    for (var entry : entries) {
      var group =
          groups.computeIfAbsent(
              new Key(entry.category(), entry.medium().id()), key -> new Accumulator());
      group.medium = entry.medium();
      group.add(entry);
      categories
          .get(entry.category())
          .add(new Entry(entry.category(), entry.medium(), entry.amount(), null));
      amount = safeAdd(amount, entry.amount());
    }
    var totals = new ArrayList<CategoryTotal>();
    categories.forEach(
        (category, value) -> totals.add(new CategoryTotal(category, value.entries, value.amount)));
    return new AdvertisingMediaReport(
        entries.size(),
        amount,
        totals,
        groups.entrySet().stream()
            .map(e -> e.getValue().row(e.getKey().category()))
            .sorted(
                Comparator.comparing(Row::category)
                    .thenComparing(Row::medium, AdvertisingMedium.BY_NAME_THEN_ID))
            .toList());
  }

  public static long safeAdd(long left, long right) {
    if (left < 0 || right < 0 || left > 9_007_199_254_740_991L - right)
      throw new ServiceUnavailableException("集計値が安全に表示できる範囲を超えています");
    return left + right;
  }
}
