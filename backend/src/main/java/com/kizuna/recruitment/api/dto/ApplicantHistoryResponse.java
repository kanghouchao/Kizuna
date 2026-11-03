package com.kizuna.recruitment.api.dto;

import com.kizuna.recruitment.domain.ApplicantStatus;
import com.kizuna.recruitment.domain.ApplicantStatusHistory;
import java.time.OffsetDateTime;

public record ApplicantHistoryResponse(
    String id,
    ApplicantStatus previousStatus,
    ApplicantStatus newStatus,
    Long actorId,
    OffsetDateTime createdAt,
    String reason) {
  public static ApplicantHistoryResponse from(ApplicantStatusHistory history) {
    return new ApplicantHistoryResponse(
        history.getId(),
        history.getPreviousStatus(),
        history.getNewStatus(),
        history.getActorId(),
        history.getCreatedAt(),
        history.getReason());
  }
}
