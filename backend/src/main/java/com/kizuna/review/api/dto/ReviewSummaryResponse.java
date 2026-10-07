package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewInput;
import com.kizuna.review.domain.ReviewSummaryView;
import com.kizuna.review.domain.ReviewValues.PermissionStatus;
import com.kizuna.review.domain.ReviewValues.ReceivedVia;
import com.kizuna.review.domain.ReviewValues.Status;
import java.time.OffsetDateTime;

public record ReviewSummaryResponse(
    String id,
    String intakeSource,
    String displayName,
    ReceivedVia receivedVia,
    OffsetDateTime receivedAt,
    OffsetDateTime createdAt,
    Status status,
    PermissionStatus permissionStatus,
    Long version) {
  public static ReviewSummaryResponse of(ReviewSummaryView r) {
    return new ReviewSummaryResponse(
        r.getId(),
        "STAFF_RECORDED",
        r.getDisplayName(),
        r.getReceivedVia(),
        r.getReceivedAt(),
        ReviewInput.time(r.getCreatedAt()),
        r.getStatus(),
        r.getPermissionStatus(),
        r.getVersion());
  }
}
