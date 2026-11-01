package com.kizuna.shared.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.kizuna.shared.exception.ServiceException;
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
    assertThat(Publication.order(Host::getPublication, Host::getId))
        .extracting(Sort.Order::getProperty, Sort.Order::getDirection, Sort.Order::getNullHandling)
        .containsExactly(
            tuple("publication.displayOrder", Sort.Direction.ASC, Sort.NullHandling.NULLS_LAST),
            tuple("publication.publishFrom", Sort.Direction.DESC, Sort.NullHandling.NULLS_LAST),
            tuple("id", Sort.Direction.ASC, Sort.NullHandling.NATIVE));
  }

  @Getter
  private static class Host {
    private Long id;
    private Publication publication;
  }
}
