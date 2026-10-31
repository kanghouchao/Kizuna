package com.kizuna.review.domain;

import com.kizuna.review.domain.ReviewValues.PermissionStatus;
import com.kizuna.review.domain.ReviewValues.ReceivedVia;
import com.kizuna.review.domain.ReviewValues.Status;
import com.kizuna.shared.publication.Publication;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.domain.Specification;

public interface ReviewSummaryView {
  String getId();

  String getDisplayName();

  ReceivedVia getReceivedVia();

  OffsetDateTime getReceivedAt();

  OffsetDateTime getCreatedAt();

  Status getStatus();

  PermissionStatus getPermissionStatus();

  Long getVersion();

  Publication getPublication();

  default boolean displayedAt(OffsetDateTime now) {
    return getStatus() == Status.APPROVED
        && getPermissionStatus() == PermissionStatus.GRANTED
        && getPublication().shownAt(now);
  }

  /** {@link #displayedAt} と同じ掲出の条件を、店舗を絞った問い合わせとして表す。 */
  static Specification<ReviewRecord> displayed(Long storeId, OffsetDateTime at) {
    Specification<ReviewRecord> prerequisites =
        (root, q, cb) ->
            cb.and(
                cb.equal(root.get("storeId"), storeId),
                cb.equal(root.get("status"), Status.APPROVED),
                cb.equal(root.get("permissionStatus"), PermissionStatus.GRANTED));
    return prerequisites.and(Publication.shownSpecification(ReviewRecord::getPublication, at));
  }
}
