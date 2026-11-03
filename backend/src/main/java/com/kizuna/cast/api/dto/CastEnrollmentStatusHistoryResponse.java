package com.kizuna.cast.api.dto;

import com.kizuna.cast.domain.CastEnrollmentStatus;
import com.kizuna.cast.domain.CastEnrollmentStatusHistory;
import java.time.OffsetDateTime;

public record CastEnrollmentStatusHistoryResponse(
    String id,
    CastEnrollmentStatus previousStatus,
    CastEnrollmentStatus newStatus,
    Long actorId,
    OffsetDateTime recordedAt) {

  public static CastEnrollmentStatusHistoryResponse of(CastEnrollmentStatusHistory row) {
    return new CastEnrollmentStatusHistoryResponse(
        row.getId(),
        row.getPreviousStatus(),
        row.getNewStatus(),
        row.getActorId(),
        row.getRecordedAt());
  }
}
