package com.kizuna.reporting;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.kizuna.advertising.reporting.AdvertisingReportReader;
import com.kizuna.order.reporting.OperationalFacts;
import com.kizuna.remuneration.reporting.RemunerationReportReader;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shift.remuneration.AttendanceFacts;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** 集計の読み手は端点の後ろでもう一度権限を確かめる。単店の読みは店舗の木の権限だけで、店舗集合を跨ぐ読みはグループの木の権限だけで開く。 */
@SpringJUnitConfig(ReportReaderTreeAuthorizationTest.Config.class)
class ReportReaderTreeAuthorizationTest {
  @Configuration
  @EnableMethodSecurity
  static class Config {
    @Bean
    RemunerationReportReader remunerationReportReader() {
      return new RemunerationReportReader(mock(EntityManager.class), mock(AttendanceFacts.class));
    }

    @Bean
    AdvertisingReportReader advertisingReportReader() {
      return new AdvertisingReportReader(mock(EntityManager.class));
    }
  }

  private static final LocalDate DAY = LocalDate.of(2026, 9, 1);
  private static final OperationalFacts NO_STORES =
      new OperationalFacts(OffsetDateTime.parse("2026-10-01T12:00:00+09:00"), List.of(), List.of());
  private static final StoreScope SCOPE = new StoreScope(false, Set.of(1L));

  @Autowired RemunerationReportReader remuneration;
  @Autowired AdvertisingReportReader advertising;

  @Test
  @WithMockUser(authorities = {"PERM_REMUNERATION_VIEW", "PERM_ADVERTISING_COST_VIEW"})
  void storeTreePermissionsOpenOnlyTheStoreReads() {
    assertThatCode(() -> remuneration.store(1L, NO_STORES, DAY, DAY, 20))
        .doesNotThrowAnyException();
    assertThatCode(() -> advertising.store(1L, List.of(), "2026-09", "2026-09", 20))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> remuneration.groupWide(SCOPE, NO_STORES, DAY, DAY, 20))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> advertising.groupWide(SCOPE, List.of(), "2026-09", "2026-09", 20))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @WithMockUser(authorities = {"PERM_REMUNERATION_SET_VIEW", "PERM_ADVERTISING_COST_SET_VIEW"})
  void groupTreePermissionsOpenOnlyTheStoreSetReads() {
    assertThatCode(() -> remuneration.groupWide(SCOPE, NO_STORES, DAY, DAY, 20))
        .doesNotThrowAnyException();
    assertThatCode(() -> advertising.groupWide(SCOPE, List.of(), "2026-09", "2026-09", 20))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> remuneration.store(1L, NO_STORES, DAY, DAY, 20))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> advertising.store(1L, List.of(), "2026-09", "2026-09", 20))
        .isInstanceOf(AccessDeniedException.class);
  }
}
