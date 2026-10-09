package com.kizuna.remuneration.api.dto;

import java.time.OffsetDateTime;
import tools.jackson.databind.JsonNode;

public record RemunerationChangeResponse(
    String id,
    Long actorId,
    String action,
    String reason,
    JsonNode beforeValue,
    JsonNode afterValue,
    OffsetDateTime createdAt) {}
