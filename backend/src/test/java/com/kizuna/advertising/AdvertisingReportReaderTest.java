package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.reporting.AdvertisingReportReader;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.storescope.StoreContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AdvertisingReportReaderTest {
  EntityManager em = mock(EntityManager.class);
  StoreContext context = mock(StoreContext.class);
  AdvertisingReportReader reader = new AdvertisingReportReader(em, context);

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void foreignAndUnresolvedScopesFailBeforeReadingEvenWithPlatformStoreContext() {
    assertThatThrownBy(() -> reader.read(true, List.of(1L), "2026-01", "2026-12", 20))
        .isInstanceOf(AccessDeniedException.class);
    authorize();
    when(context.hasStoreId()).thenReturn(true);
    when(context.getStoreId()).thenReturn(2L);
    assertThatThrownBy(() -> reader.read(true, List.of(2L), "2026-01", "2026-12", 20))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> reader.read(false, List.of(1L), "2026-01", "2026-12", 20))
        .isInstanceOf(AccessDeniedException.class);
    when(context.hasStoreId()).thenReturn(false);
    assertThatThrownBy(() -> reader.read(false, List.of(1L), "2026-01", "2026-12", 20))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(em);
  }

  @Test
  void emptySelectionDoesNotReadAndInvalidBudgetsFailBeforeDatabaseAccess() {
    authorize();
    assertThat(reader.read(true, List.of(), "2026-01", "2026-12", 20).costs()).isEmpty();
    for (int limit : List.of(0, 100_001))
      assertThatThrownBy(() -> reader.read(true, List.of(1L), "2026-01", "2026-12", limit))
          .isInstanceOf(ServiceUnavailableException.class);
    verifyNoInteractions(em);
  }

  @Test
  void boundedMinimalFactsRetainTheSavedMonthCategoryVersionAndAmount() {
    authorize();
    var query = query();
    when(query.getResultList()).thenReturn(List.of(cost(0), cost(123)));
    var result = reader.read(true, List.of(1L), "2026-01", "2026-12", 2);
    assertThat(result.costs()).hasSize(2);
    assertThat(result.costs().getLast().month()).isEqualTo("2026-02");
    assertThat(result.costs().getLast().category()).isEqualTo("RECRUITMENT");
    assertThat(result.costs().getLast().version()).isEqualTo(7);
    assertThat(result.costs().getLast().amount()).isEqualTo(123);
    verify(query).setMaxResults(3);
    verify(query).setParameter("stores", List.of(1L));
    verify(query).setParameter("from", "2026-01");
    verify(query).setParameter("to", "2026-12");
    assertThatThrownBy(() -> reader.read(true, List.of(1L), "2026-01", "2026-12", 1))
        .isInstanceOf(ServiceUnavailableException.class);
  }

  @Test
  void invalidFactsCannotBecomeZeroOrPartialSuccess() {
    authorize();
    var query = query();
    for (Integer amount : new Integer[] {null, -1}) {
      when(query.getResultList()).thenReturn(List.of(cost(amount)));
      assertThatThrownBy(() -> reader.read(true, List.of(1L), "2026-01", "2026-12", 20))
          .isInstanceOf(ServiceUnavailableException.class);
    }
    when(query.getResultList())
        .thenReturn(List.of(new AdvertisingReportReader.Cost(1L, "01", 1L, "2026-02", null, 0)));
    assertThatThrownBy(() -> reader.read(true, List.of(1L), "2026-01", "2026-12", 20))
        .isInstanceOf(ServiceUnavailableException.class);
  }

  @SuppressWarnings("unchecked")
  TypedQuery<AdvertisingReportReader.Cost> query() {
    TypedQuery<AdvertisingReportReader.Cost> query = mock(TypedQuery.class, Answers.RETURNS_SELF);
    when(em.createQuery(anyString(), eq(AdvertisingReportReader.Cost.class))).thenReturn(query);
    return query;
  }

  AdvertisingReportReader.Cost cost(Integer amount) {
    return new AdvertisingReportReader.Cost(
        1L, "0001", 7L, "2026-02", AdvertisingCategory.RECRUITMENT, amount);
  }

  void authorize() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(
                Jwt.withTokenValue("test")
                    .header("alg", "none")
                    .subject("actor")
                    .claim("storeScopeType", "SPECIFIC_STORES")
                    .claim("storeIds", List.of(1L))
                    .build()));
  }
}
