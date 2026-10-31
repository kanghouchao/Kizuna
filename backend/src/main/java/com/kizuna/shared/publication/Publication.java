package com.kizuna.shared.publication;

import com.kizuna.shared.exception.ServiceException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * 公開コンテンツの公開設定。掲出述語と並びはここだけで定義し、種別ごとの前提は利用側が AND で重ねる。 問い合わせ用の述語と並びは、埋め込み先の属性名を {@code publication}
 * とする前提で組む。
 */
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

  public static <T> Specification<T> shownSpecification(OffsetDateTime at) {
    return (root, query, cb) -> {
      var p = root.get("publication");
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

  public static Sort order() {
    return Sort.by(
        Sort.Order.asc("publication.displayOrder").nullsLast(),
        Sort.Order.desc("publication.publishFrom").nullsLast(),
        Sort.Order.asc("id"));
  }

  private static OffsetDateTime minute(OffsetDateTime value) {
    return value == null
        ? null
        : value.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
  }
}
