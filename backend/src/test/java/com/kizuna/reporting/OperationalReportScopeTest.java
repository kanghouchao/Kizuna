package com.kizuna.reporting;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kizuna.order.reporting.OperationalReportReader;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.storescope.StoreScope;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class OperationalReportScopeTest {
  AppProperties properties = new AppProperties();
  EntityManager manager = mock(EntityManager.class);
  OperationalReportReader reader =
      new OperationalReportReader(manager, properties, Clock.systemUTC());

  @Test
  void unresolvedScopeIsRejectedBeforeQuery() {
    assertThatThrownBy(
            () -> reader.groupWide(null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(manager);
  }

  @Test
  void configurationHardLimitRejectsBeforeDatabaseAccess() {
    var scope = new StoreScope(true, Set.of());
    for (int maximum : List.of(0, 100_001)) {
      properties.getOperationalReport().setMaxOrders(maximum);
      assertThatThrownBy(
              () -> reader.groupWide(scope, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
          .isInstanceOf(ServiceUnavailableException.class);
    }
    verifyNoInteractions(manager);
  }
}
