package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewChange;
import com.kizuna.review.domain.ReviewValues.Basis;
import com.kizuna.review.domain.ReviewValues.Operation;
import java.time.OffsetDateTime;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ReviewPermissionRequest(
    @JsonDeserialize(using = ReviewVersionDeserializer.class) Long version,
    Basis basisType,
    OffsetDateTime grantedAt,
    String evidenceNote,
    String dedupeKey) {
  public ReviewChange change(String id) {
    return new ReviewChange(
        id,
        version,
        Operation.PERMISSION_GRANTED,
        null,
        dedupeKey,
        basisType,
        grantedAt,
        evidenceNote);
  }
}
