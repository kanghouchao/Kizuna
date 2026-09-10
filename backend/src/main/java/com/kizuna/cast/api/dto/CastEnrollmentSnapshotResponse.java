package com.kizuna.cast.api.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record CastEnrollmentSnapshotResponse(
    String id, Long actorId, OffsetDateTime recordedAt, Map<String, String> customFields) {}
