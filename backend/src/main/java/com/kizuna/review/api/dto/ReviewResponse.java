package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewDetailsView;
import com.kizuna.review.domain.ReviewInput;
import com.kizuna.review.domain.ReviewPermissionView;
import com.kizuna.review.domain.ReviewValues.PermissionStatus;
import com.kizuna.review.domain.ReviewValues.ReceivedVia;
import com.kizuna.review.domain.ReviewValues.Status;
import java.time.OffsetDateTime;
import java.util.List;

public record ReviewResponse(
    String id,
    String intakeSource,
    String displayName,
    ReceivedVia receivedVia,
    OffsetDateTime receivedAt,
    OffsetDateTime createdAt,
    Status status,
    PermissionStatus permissionStatus,
    Long version,
    String body,
    String originOrderId,
    OffsetDateTime originCheckedAt,
    Long originOrderVersion,
    Actor recordedBy,
    String supersedesId,
    String supersededById,
    ReviewPermissionResponse permission,
    boolean publicationEligible,
    List<String> publicationBlockers,
    String publicationConnection) {
  public record Actor(String id, String displayName) {}

  public static ReviewResponse of(ReviewDetailsView r, ReviewPermissionView permission) {
    return new ReviewResponse(
        r.getId(),
        "STAFF_RECORDED",
        r.getDisplayName(),
        r.getReceivedVia(),
        r.getReceivedAt(),
        ReviewInput.time(r.getCreatedAt()),
        r.getStatus(),
        r.getPermissionStatus(),
        r.getVersion(),
        r.getBody(),
        r.getOriginOrderId(),
        r.getOriginCheckedAt(),
        r.getOriginOrderVersion(),
        new Actor(r.getRecordedBy().toString(), r.getRecorderName()),
        r.getSupersedesId(),
        r.getSupersededById(),
        ReviewPermissionResponse.of(permission),
        r.publicationBlockers().isEmpty(),
        r.publicationBlockers(),
        "NOT_CONFIGURED");
  }
}
