package com.kizuna.remuneration.api.dto;

import com.kizuna.remuneration.application.RemunerationViews.ChangeView;
import java.time.OffsetDateTime;
import tools.jackson.databind.JsonNode;

public record RemunerationChangeResponse(
    String id,
    Long actorId,
    String action,
    String reason,
    JsonNode beforeValue,
    JsonNode afterValue,
    OffsetDateTime createdAt) {
  public static RemunerationChangeResponse of(ChangeView v) {
    return new RemunerationChangeResponse(
        v.id(),
        v.actorId(),
        v.action(),
        v.reason(),
        v.beforeValue(),
        v.afterValue(),
        v.createdAt());
  }
}
