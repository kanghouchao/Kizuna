package com.kizuna.remuneration.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kizuna.order.reporting.OperationalFacts;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shift.remuneration.AttendanceFacts;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.security.access.AccessDeniedException;

/** 報酬根拠の読口が、受注事実の店舗一覧を引数の授権店舗集合で照合してから読むことの単体テスト。 */
class RemunerationReportScopeTest {
  static final LocalDate FROM = LocalDate.of(2026, 9, 1), TO = LocalDate.of(2026, 9, 30);

  EntityManager em = mock(EntityManager.class);
  AttendanceFacts attendances = mock(AttendanceFacts.class);
  RemunerationReportReader reader = new RemunerationReportReader(em, attendances);
  OperationalFacts facts =
      new OperationalFacts(
          OffsetDateTime.parse("2026-10-01T12:00:00+09:00"),
          List.of(new OperationalFacts.Store(1L, "一号店"), new OperationalFacts.Store(2L, "二号店")),
          List.of());

  @Test
  void unresolvedOrPartialScopeFailsBeforeReading() {
    assertThatThrownBy(() -> reader.read(null, facts, FROM, TO, 20))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> reader.read(new StoreScope(false, Set.of(1L)), facts, FROM, TO, 20))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(em, attendances);
  }

  @Test
  void authorizedScopeReadsEveryListedStore() {
    query(RemunerationReportFacts.Bonus.class);
    query(RemunerationReportReader.Term.class);
    var result = reader.read(new StoreScope(false, Set.of(1L, 2L)), facts, FROM, TO, 20);
    assertThat(result.days()).isEmpty();
    verify(attendances).forReport(List.of(1L, 2L), FROM, TO, 20);
  }

  @SuppressWarnings("unchecked")
  private <T> void query(Class<T> type) {
    TypedQuery<T> query = mock(TypedQuery.class, Answers.RETURNS_SELF);
    when(query.getResultList()).thenReturn(List.of());
    when(em.createQuery(anyString(), eq(type))).thenReturn(query);
  }
}
