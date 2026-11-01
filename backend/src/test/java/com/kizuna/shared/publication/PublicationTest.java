package com.kizuna.shared.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.kizuna.shared.exception.ServiceException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.time.OffsetDateTime;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class PublicationTest {
  private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-11-01T20:00:00+09:00");
  private static final OffsetDateTime UNTIL = OffsetDateTime.parse("2026-11-02T03:00:00+09:00");

  @Test
  void periodIncludesItsStartAndExcludesItsEnd() {
    var p = new Publication(true, FROM, UNTIL, null);
    assertThat(p.shownAt(FROM.minusNanos(1000))).isFalse();
    assertThat(p.shownAt(FROM)).isTrue();
    assertThat(p.shownAt(UNTIL.minusNanos(1000))).isTrue();
    assertThat(p.shownAt(UNTIL)).isFalse();
  }

  @Test
  void openEndsAreUnboundedAndUnpublishedIsNeverShown() {
    assertThat(new Publication(true, null, null, null).shownAt(FROM)).isTrue();
    assertThat(new Publication(true, FROM, null, null).shownAt(UNTIL.plusYears(10))).isTrue();
    assertThat(new Publication(true, null, UNTIL, null).shownAt(FROM.minusYears(10))).isTrue();
    assertThat(new Publication(false, FROM, UNTIL, 1).shownAt(FROM)).isFalse();
    assertThat(Publication.HIDDEN.shownAt(FROM)).isFalse();
    assertThat(Publication.HIDDEN)
        .extracting(Publication::publishFrom, Publication::publishUntil, Publication::displayOrder)
        .containsOnlyNulls();
  }

  @Test
  void periodMustStartBeforeItEnds() {
    assertThatThrownBy(() -> new Publication(true, FROM, FROM, null))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> new Publication(false, UNTIL, FROM, null))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void datesAreStoredInUtcToTheMinute() {
    var p =
        new Publication(
            true,
            OffsetDateTime.parse("2026-11-01T20:00:59.999+09:00"),
            OffsetDateTime.parse("2026-11-01T20:01:30+09:00"),
            null);
    assertThat(p.publishFrom()).isEqualTo(OffsetDateTime.parse("2026-11-01T11:00:00Z"));
    assertThat(p.publishUntil()).isEqualTo(OffsetDateTime.parse("2026-11-01T11:01:00Z"));
    assertThatThrownBy(
            () ->
                new Publication(
                    true,
                    OffsetDateTime.parse("2026-11-01T20:00:10+09:00"),
                    OffsetDateTime.parse("2026-11-01T20:00:50+09:00"),
                    null))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  void orderResolvesNestedPathsUnderTheHostAttribute() {
    assertThat(Publication.order(Host::getSettings, Host::getKey))
        .extracting(Sort.Order::getProperty, Sort.Order::getDirection, Sort.Order::getNullHandling)
        .containsExactly(
            tuple("settings.displayOrder", Sort.Direction.ASC, Sort.NullHandling.NULLS_LAST),
            tuple("settings.publishFrom", Sort.Direction.DESC, Sort.NullHandling.NULLS_LAST),
            tuple("key", Sort.Direction.ASC, Sort.NullHandling.NATIVE));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shownSpecificationReadsFieldsUnderTheHostAttribute() {
    Root<Host> root = mock();
    Path<Object> settings = mock();
    doReturn(settings).when(root).get("settings");
    Publication.<Host>shownSpecification(Host::getSettings, FROM)
        .toPredicate(root, mock(), mock(CriteriaBuilder.class));
    verify(root).get("settings");
    verifyNoMoreInteractions(root);
    verify(settings).get("published");
  }

  @Getter
  private static class Host {
    private Long key;
    private Publication settings;
  }
}
