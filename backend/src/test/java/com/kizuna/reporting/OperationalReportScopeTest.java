package com.kizuna.reporting;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kizuna.order.reporting.OperationalReportReader;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.storescope.StoreContext;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class OperationalReportScopeTest {
  AppProperties properties = new AppProperties();
  EntityManager manager = mock(EntityManager.class);
  OperationalReportReader reader =
      new OperationalReportReader(manager, mock(StoreContext.class), properties, Clock.systemUTC());

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void unresolvedEmptyAndForeignStoreAreRejectedBeforeQuery() {
    assertDenied(null);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(
                Jwt.withTokenValue("test")
                    .header("alg", "none")
                    .subject("actor")
                    .claim("storeScopeType", "SPECIFIC_STORES")
                    .claim("storeIds", List.of())
                    .build()));
    assertDenied(null);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(
                Jwt.withTokenValue("test")
                    .header("alg", "none")
                    .subject("actor")
                    .claim("storeScopeType", "SPECIFIC_STORES")
                    .claim("storeIds", List.of(1L))
                    .build()));
    assertDenied(2L);
    assertDenied(Long.MAX_VALUE);
    verifyNoInteractions(manager);
  }

  @Test
  void configurationHardLimitRejectsBeforeDatabaseAccess() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(
                Jwt.withTokenValue("test")
                    .header("alg", "none")
                    .subject("actor")
                    .claim("storeScopeType", "ALL_STORES")
                    .build()));
    for (int maximum : List.of(0, 100_001)) {
      properties.getOperationalReport().setMaxOrders(maximum);
      assertThatThrownBy(
              () -> reader.platform(null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
          .isInstanceOf(ServiceUnavailableException.class);
    }
    verifyNoInteractions(manager);
  }

  void assertDenied(Long storeId) {
    assertThatThrownBy(
            () -> reader.platform(storeId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        .isInstanceOf(AccessDeniedException.class);
  }
}
