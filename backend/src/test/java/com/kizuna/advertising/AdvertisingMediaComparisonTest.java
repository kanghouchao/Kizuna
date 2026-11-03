package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.advertising.api.dto.AdvertisingResponses.CostResponse;
import com.kizuna.advertising.api.dto.AdvertisingResponses.MediumResponse;
import com.kizuna.advertising.api.dto.AdvertisingResponses.MonthResponse;
import com.kizuna.advertising.application.AdvertisingMediaComparison;
import com.kizuna.advertising.application.AdvertisingMediaComparison.Basis;
import com.kizuna.advertising.application.AdvertisingMediaComparison.Cost;
import com.kizuna.advertising.application.AdvertisingMediaComparison.Fact;
import com.kizuna.advertising.application.AdvertisingService;
import com.kizuna.advertising.application.AdvertisingService.ExportSnapshot;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingMedium;
import com.kizuna.medium.lookup.MediumLookup;
import com.kizuna.medium.lookup.MediumReference;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.export.DocumentBudget;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdvertisingMediaComparisonTest {
  static final OffsetDateTime AT = OffsetDateTime.parse("2026-10-09T12:00:00Z");

  record Visit(String mediumId, String label) {}

  record Seen(List<Cost> costs, List<Fact<Visit>> facts) {}

  AdvertisingService costs = mock(AdvertisingService.class);
  MediumLookup lookup = mock(MediumLookup.class);
  AppProperties properties = new AppProperties();
  AdvertisingMediaComparison comparison = new AdvertisingMediaComparison(costs, lookup, properties);

  static CostResponse cost(AdvertisingCategory category, String mediumId, String name, int amount) {
    return new CostResponse(
        "c" + mediumId + amount,
        1L,
        "2026-09",
        category,
        new MediumResponse(mediumId, name),
        null,
        null,
        category == AdvertisingCategory.SALES ? 3 : null,
        amount,
        1,
        AT,
        AT);
  }

  void costs(CostResponse... rows) {
    when(costs.snapshot(1L, "2026-09"))
        .thenReturn(
            new ExportSnapshot(
                new MonthResponse(1L, "2026-09", 7, rows.length, 0, 0, 0, AT), List.of(rows)));
  }

  static DocumentBudget budget() {
    return new DocumentBudget(new AppProperties.OperationalReport());
  }

  @Test
  void comparesTheCategoryCostsWithFactsWhoseMediaAreResolvedTogether() {
    costs(
        cost(AdvertisingCategory.SALES, "m1", "媒体A", 100),
        cost(AdvertisingCategory.RECRUITMENT, "m2", "求人B", 50));
    when(lookup.findAll(1L, Set.of("m1", "m3")))
        .thenReturn(
            Map.of(
                "m1", new MediumReference("m1", "媒体A", true),
                "m3", new MediumReference("m3", "停止済み媒体", false)));
    properties.getAdvertisingCost().setMaxOrders(50);
    var reads = new ArrayList<List<Object>>();
    var basis =
        new Basis<Visit>(
            AdvertisingCategory.SALES,
            (store, from, to, limit) -> {
              reads.add(List.of(store, from, to, limit));
              return List.of(
                  new Visit("m1", "媒体経由"), new Visit("m3", "停止後"), new Visit(null, "紹介"));
            },
            Visit::mediumId);
    var compared = comparison.compare(1L, "2026-09", basis, budget(), Seen::new);
    assertThat(reads)
        .containsExactly(
            List.of(1L, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"), 50));
    assertThat(compared.storeId()).isEqualTo(1);
    assertThat(compared.month()).isEqualTo("2026-09");
    assertThat(compared.monthVersion()).isEqualTo(7);
    assertThat(compared.generatedAt()).isEqualTo(AT);
    assertThat(compared.report().costs())
        .containsExactly(
            new Cost(AdvertisingCategory.SALES, new AdvertisingMedium("m1", "媒体A"), 100, 3));
    assertThat(compared.report().facts())
        .containsExactly(
            new Fact<>(new AdvertisingMedium("m1", "媒体A"), new Visit("m1", "媒体経由")),
            new Fact<>(new AdvertisingMedium("m3", "停止済み媒体"), new Visit("m3", "停止後")),
            new Fact<>(null, new Visit(null, "紹介")));
  }

  @Test
  void costsOnlyKeepEveryCategoryAndReadNoFacts() {
    costs(
        cost(AdvertisingCategory.SALES, "m1", "媒体A", 100),
        cost(AdvertisingCategory.RECRUITMENT, "m2", "求人B", 50));
    var compared = comparison.costs(1L, "2026-09", budget(), selected -> selected);
    assertThat(compared.report())
        .extracting(Cost::category)
        .containsExactly(AdvertisingCategory.SALES, AdvertisingCategory.RECRUITMENT);
    verify(lookup, never()).findAll(any(), any());
  }

  @Test
  void countsCostAndFactMediumNamesAgainstTheBudget() {
    costs(cost(AdvertisingCategory.SALES, "m1", "あいうえお", 100));
    when(lookup.findAll(1L, Set.of("m2")))
        .thenReturn(Map.of("m2", new MediumReference("m2", "かきくけこ", true)));
    var basis =
        new Basis<Visit>(
            AdvertisingCategory.SALES,
            (store, from, to, limit) -> List.of(new Visit("m2", "")),
            Visit::mediumId);
    var settings = new AppProperties.OperationalReport();
    settings.setMaxCharacters(5);
    assertThat(comparison.costs(1L, "2026-09", new DocumentBudget(settings), selected -> selected))
        .isNotNull();
    assertThatThrownBy(
            () ->
                comparison.compare(1L, "2026-09", basis, new DocumentBudget(settings), (c, f) -> f))
        .isInstanceOf(ServiceUnavailableException.class);
  }

  @Test
  void rejectsAMalformedMonthBeforeReadingAnything() {
    var basis =
        new Basis<Visit>(
            AdvertisingCategory.SALES, (store, from, to, limit) -> List.of(), Visit::mediumId);
    assertThatThrownBy(() -> comparison.compare(1L, "2026-13", basis, budget(), (c, f) -> f))
        .isInstanceOf(ServiceException.class);
    verify(costs, never()).snapshot(any(), any());
  }
}
