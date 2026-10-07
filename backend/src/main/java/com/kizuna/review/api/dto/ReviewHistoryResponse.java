package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewHistoryView;
import com.kizuna.review.domain.ReviewInput;
import com.kizuna.review.domain.ReviewValues.Operation;
import com.kizuna.review.domain.ReviewValues.PermissionStatus;
import com.kizuna.review.domain.ReviewValues.Status;
import java.time.OffsetDateTime;

public record ReviewHistoryResponse(
    String id,
    Operation type,
    OffsetDateTime createdAt,
    ReviewResponse.Actor actor,
    Long beforeVersion,
    Long afterVersion,
    Status beforeStatus,
    Status afterStatus,
    PermissionStatus beforePermissionStatus,
    PermissionStatus afterPermissionStatus,
    String reason,
    String relatedReviewId,
    String permissionRecordId) {
  public static ReviewHistoryResponse of(ReviewHistoryView h) {
    return new ReviewHistoryResponse(
        h.getId(),
        h.getType(),
        ReviewInput.time(h.getCreatedAt()),
        new ReviewResponse.Actor(h.getActorId().toString(), h.getActorName()),
        h.getBeforeVersion(),
        h.getAfterVersion(),
        h.getBeforeStatus(),
        h.getAfterStatus(),
        h.getBeforePermissionStatus(),
        h.getAfterPermissionStatus(),
        h.getReason(),
        h.getRelatedReviewId(),
        h.getPermissionRecordId());
  }
}
