package com.kizuna.shared.publication;

import com.kizuna.shared.exception.ServiceException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.criteria.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.data.core.PropertyPath;
import org.springframework.data.core.TypedPropertyPath;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/** 公開コンテンツの公開設定。掲出述語と並びはここだけで定義し、種別ごとの前提は利用側が AND で重ねる。 */
@Embeddable
public record Publication(
    @Column(nullable = false) boolean published,
    OffsetDateTime publishFrom,
    OffsetDateTime publishUntil,
    Integer displayOrder) {
  public static final Publication HIDDEN = new Publication(false, null, null, null);

  public Publication {
    publishFrom = minute(publishFrom);
    publishUntil = minute(publishUntil);
    if (publishFrom != null && publishUntil != null && !publishFrom.isBefore(publishUntil))
      throw new ServiceException("公開期間の開始は終了より前にしてください");
  }

  public boolean shownAt(OffsetDateTime now) {
    return published
        && (publishFrom == null || !now.isBefore(publishFrom))
        && (publishUntil == null || now.isBefore(publishUntil));
  }

  public static <T> Specification<T> shownSpecification(
      TypedPropertyPath<T, Publication> publication, OffsetDateTime at) {
    return (root, query, cb) -> {
      Path<?> p = root;
      for (PropertyPath segment : publication) p = p.get(segment.getSegment());
      return cb.and(
          cb.isTrue(p.get("published")),
          cb.or(
              cb.isNull(p.get("publishFrom")),
              cb.lessThanOrEqualTo(p.<OffsetDateTime>get("publishFrom"), at)),
          cb.or(
              cb.isNull(p.get("publishUntil")),
              cb.greaterThan(p.<OffsetDateTime>get("publishUntil"), at)));
    };
  }

  public static <T> Sort order(
      TypedPropertyPath<T, Publication> publication, TypedPropertyPath<T, ?> id) {
    return Sort.by(
        Sort.Order.asc(publication.then(Publication::displayOrder)).nullsLast(),
        Sort.Order.desc(publication.then(Publication::publishFrom)).nullsLast(),
        Sort.Order.asc(id));
  }

  private static OffsetDateTime minute(OffsetDateTime value) {
    return value == null
        ? null
        : value.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
  }
}
