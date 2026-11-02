package com.kizuna.review.api.dto;

import com.kizuna.review.domain.ReviewCommand;
import com.kizuna.review.domain.ReviewValues.Basis;
import java.time.OffsetDateTime;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ReviewPermissionRequest(
    @JsonDeserialize(using = ReviewVersionDeserializer.class) Long version,
    Basis basisType,
    OffsetDateTime grantedAt,
    String evidenceNote,
    String dedupeKey) {
  public ReviewCommand.Grant grant(String id) {
    return new ReviewCommand.Grant(id, version, basisType, grantedAt, evidenceNote, dedupeKey);
  }
}
