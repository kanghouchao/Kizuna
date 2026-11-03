package com.kizuna.shared.publication;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.Paging;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.criteria.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.core.PropertyPath;
import org.springframework.data.core.TypedPropertyPath;
import org.springframework.data.domain.Pageable;
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

  /** 公開一覧の問い合わせ。前提は店舗を明示した種別側の条件で、入口は店舗条件を補わない。 */
  public record Listing<T>(Specification<T> specification, Pageable pageable) {}

  /** 並びは公開設定が決めるので、呼出側の sort は捨てて規範の並びに差し替える。 */
  public static <T> Listing<T> listing(
      TypedPropertyPath<T, Publication> publication,
      TypedPropertyPath<T, ?> id,
      Specification<T> prerequisites,
      OffsetDateTime at,
      Pageable pageable) {
    return new Listing<>(
        prerequisites.and(shownSpecification(publication, at)),
        Paging.totalOrder(pageable, order(publication, id), Sort.Order.asc(id)));
  }

  /** 監査値の公開設定。未設定は他の監査値と同じく空文字で残し、キーは常に揃える。 */
  public Map<String, String> auditValues() {
    var values = new LinkedHashMap<String, String>();
    values.put("published", String.valueOf(published));
    values.put("publish_from", Objects.toString(publishFrom, ""));
    values.put("publish_until", Objects.toString(publishUntil, ""));
    values.put("display_order", Objects.toString(displayOrder, ""));
    return values;
  }

  private static OffsetDateTime minute(OffsetDateTime value) {
    return value == null
        ? null
        : value.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
  }
}
