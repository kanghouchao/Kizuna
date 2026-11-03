package com.kizuna.cast.api.dto;

import com.kizuna.cast.domain.CastEnrollmentSnapshot;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

public record CastEnrollmentSnapshotResponse(
    String id, Long actorId, OffsetDateTime recordedAt, Map<String, String> customFields) {

  public static CastEnrollmentSnapshotResponse of(CastEnrollmentSnapshot row) {
    return new CastEnrollmentSnapshotResponse(
        row.getId(), row.getActorId(), row.getRecordedAt(), new HashMap<>(row.getCustomFields()));
  }
}
