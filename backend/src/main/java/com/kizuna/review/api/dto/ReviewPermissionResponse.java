package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewInput;
import com.kizuna.review.domain.ReviewPermissionView;
import com.kizuna.review.domain.ReviewValues.Basis;
import java.time.OffsetDateTime;

public record ReviewPermissionResponse(
    String id,
    String scope,
    Basis basisType,
    OffsetDateTime grantedAt,
    String evidenceNote,
    ReviewResponse.Actor recordedBy,
    OffsetDateTime recordedAt,
    Revocation revocation) {
  public record Revocation(
      OffsetDateTime withdrawalReceivedAt,
      String reason,
      ReviewResponse.Actor recordedBy,
      OffsetDateTime recordedAt) {}

  public static ReviewPermissionResponse of(ReviewPermissionView p) {
    if (p == null) return null;
    return new ReviewPermissionResponse(
        p.getId(),
        "OWN_STORE_WEBSITE",
        p.getBasisType(),
        p.getGrantedAt(),
        p.getEvidenceNote(),
        new ReviewResponse.Actor(p.getRecordedBy().toString(), p.getRecorderName()),
        ReviewInput.time(p.getCreatedAt()),
        p.getRevokedAt() == null
            ? null
            : new Revocation(
                p.getWithdrawalReceivedAt(),
                p.getRevocationReason(),
                new ReviewResponse.Actor(p.getRevokedBy().toString(), p.getRevokerName()),
                p.getRevokedAt()));
  }
}
