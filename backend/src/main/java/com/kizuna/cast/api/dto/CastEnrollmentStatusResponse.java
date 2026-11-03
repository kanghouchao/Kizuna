package com.kizuna.cast.api.dto;

import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentStatus;
import java.time.OffsetDateTime;

public record CastEnrollmentStatusResponse(
    String id, CastEnrollmentStatus status, OffsetDateTime endedAt) {

  public static CastEnrollmentStatusResponse of(CastEnrollment enrollment) {
    return new CastEnrollmentStatusResponse(
        enrollment.getId(), enrollment.getStatus(), enrollment.getEndedAt());
  }
}
