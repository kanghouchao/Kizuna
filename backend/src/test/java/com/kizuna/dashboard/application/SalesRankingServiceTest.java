package com.kizuna.dashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.reporting.dashboard.SalesByStoreReader;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.store.dashboard.StoreDashboardReader;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SalesRankingServiceTest {

  @Test
  @DisplayName("空の店舗集合は売上も稼働状態も読まずに空の一覧を返す（全店と読み替えない）")
  void emptyScopeIsEmptyWithoutReading() {
    var businessDates = mock(BusinessDateService.class);
    var sales = mock(SalesByStoreReader.class);
    var stores = mock(StoreDashboardReader.class);
    when(businessDates.currentBusinessDate()).thenReturn(LocalDate.of(2026, 11, 12));

    var ranking =
        new SalesRankingService(businessDates, sales, stores).read(new StoreScope(Set.of()));

    assertThat(ranking.businessDate()).isEqualTo(LocalDate.of(2026, 11, 12));
    assertThat(ranking.stores()).isEmpty();
    verifyNoInteractions(sales, stores);
  }
}
