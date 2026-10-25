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
import com.kizuna.shared.storescope.StoreScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.security.access.AccessDeniedException;

class AdvertisingReportReaderTest {
  EntityManager em = mock(EntityManager.class);
  AdvertisingReportReader reader = new AdvertisingReportReader(em);
  StoreScope scope = new StoreScope(false, Set.of(1L));

  @Test
  void foreignAndUnresolvedScopesFailBeforeReading() {
    assertThatThrownBy(() -> reader.groupWide(null, List.of(1L), "2026-01", "2026-12", 20))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> reader.groupWide(scope, List.of(1L, 2L), "2026-01", "2026-12", 20))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> reader.store(2L, List.of(1L), "2026-01", "2026-12", 20))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> reader.store(1L, List.of(1L, 2L), "2026-01", "2026-12", 20))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(em);
  }

  @Test
  void emptySelectionDoesNotReadAndInvalidBudgetsFailBeforeDatabaseAccess() {
    assertThat(reader.groupWide(scope, List.of(), "2026-01", "2026-12", 20).costs()).isEmpty();
    for (int limit : List.of(0, 100_001))
      assertThatThrownBy(() -> reader.groupWide(scope, List.of(1L), "2026-01", "2026-12", limit))
          .isInstanceOf(ServiceUnavailableException.class);
    verifyNoInteractions(em);
  }

  @Test
  void storePathReadsTheNamedStore() {
    var query = query();
    when(query.getResultList()).thenReturn(List.of(cost(10)));
    assertThat(reader.store(1L, List.of(1L), "2026-01", "2026-12", 20).costs()).hasSize(1);
    verify(query).setParameter("stores", List.of(1L));
  }

  @Test
  void boundedMinimalFactsRetainTheSavedMonthCategoryVersionAndAmount() {
    var query = query();
    when(query.getResultList()).thenReturn(List.of(cost(0), cost(123)));
    var result = reader.groupWide(scope, List.of(1L), "2026-01", "2026-12", 2);
    assertThat(result.costs()).hasSize(2);
    assertThat(result.costs().getLast().month()).isEqualTo("2026-02");
    assertThat(result.costs().getLast().category()).isEqualTo("RECRUITMENT");
    assertThat(result.costs().getLast().version()).isEqualTo(7);
    assertThat(result.costs().getLast().amount()).isEqualTo(123);
    verify(query).setMaxResults(3);
    verify(query).setParameter("stores", List.of(1L));
    verify(query).setParameter("from", "2026-01");
    verify(query).setParameter("to", "2026-12");
    assertThatThrownBy(() -> reader.groupWide(scope, List.of(1L), "2026-01", "2026-12", 1))
        .isInstanceOf(ServiceUnavailableException.class);
  }

  @Test
  void invalidFactsCannotBecomeZeroOrPartialSuccess() {
    var query = query();
    for (Integer amount : new Integer[] {null, -1}) {
      when(query.getResultList()).thenReturn(List.of(cost(amount)));
      assertThatThrownBy(() -> reader.groupWide(scope, List.of(1L), "2026-01", "2026-12", 20))
          .isInstanceOf(ServiceUnavailableException.class);
    }
    when(query.getResultList())
        .thenReturn(List.of(new AdvertisingReportReader.Cost(1L, "01", 1L, "2026-02", null, 0)));
    assertThatThrownBy(() -> reader.groupWide(scope, List.of(1L), "2026-01", "2026-12", 20))
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
}
