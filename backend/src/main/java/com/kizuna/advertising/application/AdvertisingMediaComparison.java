package com.kizuna.advertising.application;

import static com.kizuna.advertising.domain.AdvertisingMediaReport.safeAdd;

import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingMedium;
import com.kizuna.medium.lookup.MediumLookup;
import com.kizuna.medium.lookup.MediumReference;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.export.DocumentBudget;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 対象月の広告費を媒体ごとに比べる帳票の共通の流れ。費用は出力時点の登録から読み、事実（受注・入店）は媒体名をまとめて一度引いて媒体を付ける。
 * 費用と事実を同じ読取り時点で見るため、呼出側のトランザクションの内側で使う。
 */
@Component
@RequiredArgsConstructor
public class AdvertisingMediaComparison {
  private final AdvertisingService costs;
  private final MediumLookup mediumLookup;
  private final AppProperties properties;

  /** 対象期間の事実を上限件数まで読む。上限を超えたら読取り側が失敗させる。 */
  @FunctionalInterface
  public interface FactReader<F> {
    List<F> read(Long storeId, LocalDate from, LocalDate to, int limit);
  }

  /** 比べる費用の区分と、その区分の費用に突き合わせる事実。事実の媒体 ID は媒体以外の経路なら null。 */
  public record Basis<F>(
      AdvertisingCategory category, FactReader<F> facts, Function<F, String> mediumId) {}

  public record Cost(
      AdvertisingCategory category, AdvertisingMedium medium, long amount, Integer inquiryCount) {}

  /** medium は事実の媒体の現在名。媒体を持たない事実では null。 */
  public record Fact<F>(AdvertisingMedium medium, F fact) {}

  public record Compared<R>(
      long storeId, String month, long monthVersion, OffsetDateTime generatedAt, R report) {}

  /** 全区分の費用だけを集計する。 */
  public <R> Compared<R> costs(
      Long storeId, String month, DocumentBudget budget, Function<List<Cost>, R> aggregate) {
    return read(storeId, month, null, budget, aggregate);
  }

  public <F, R> Compared<R> compare(
      Long storeId,
      String month,
      Basis<F> basis,
      DocumentBudget budget,
      BiFunction<List<Cost>, List<Fact<F>>, R> aggregate) {
    var period = YearMonth.parse(AdvertisingInput.month(month));
    return read(
        storeId,
        month,
        basis.category(),
        budget,
        selected -> aggregate.apply(selected, facts(storeId, period, basis, budget)));
  }

  private <R> Compared<R> read(
      Long storeId,
      String month,
      AdvertisingCategory category,
      DocumentBudget budget,
      Function<List<Cost>, R> aggregate) {
    var source = costs.snapshot(storeId, month);
    budget.check();
    var selected =
        source.costs().stream()
            .filter(c -> category == null || c.category() == category)
            .map(
                c -> {
                  budget.text(c.medium().name());
                  return new Cost(c.category(), c.medium(), c.amount(), c.inquiryCount());
                })
            .toList();
    var report = aggregate.apply(selected);
    budget.check();
    var summary = source.summary();
    return new Compared<>(
        safeAdd(0, summary.storeId()),
        summary.month(),
        safeAdd(0, summary.version()),
        summary.generatedAt(),
        report);
  }

  private <F> List<Fact<F>> facts(
      Long storeId, YearMonth period, Basis<F> basis, DocumentBudget budget) {
    var read =
        basis
            .facts()
            .read(
                storeId,
                period.atDay(1),
                period.atEndOfMonth(),
                properties.getAdvertisingCost().getMaxOrders());
    var media =
        mediumLookup.findAll(
            storeId,
            read.stream()
                .map(basis.mediumId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
    media.values().forEach(m -> budget.text(m.name()));
    return read.stream()
        .map(f -> new Fact<>(medium(MediumLookup.referenced(media, basis.mediumId().apply(f))), f))
        .toList();
  }

  private static AdvertisingMedium medium(MediumReference medium) {
    return medium == null ? null : new AdvertisingMedium(medium.id(), medium.name());
  }
}
