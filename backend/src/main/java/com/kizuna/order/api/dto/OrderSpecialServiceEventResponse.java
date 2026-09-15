package com.kizuna.order.api.dto;

import com.kizuna.order.domain.OrderSpecialServiceEvent;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderSpecialServiceEventResponse(
    String id,
    OffsetDateTime occurredAt,
    String actorId,
    String kind,
    String consentEventId,
    List<OrderSpecialServiceResponse> before,
    List<OrderSpecialServiceResponse> after,
    int previousTotalFee,
    int totalFee,
    String resolution,
    String rejectionEventId) {
  public static OrderSpecialServiceEventResponse of(OrderSpecialServiceEvent e) {
    return new OrderSpecialServiceEventResponse(
        e.getId(),
        e.getOccurredAt(),
        e.getActorId().toString(),
        e.getKind(),
        e.getConsentEventId(),
        e.getBefore().stream()
            .map(s -> new OrderSpecialServiceResponse(s.snapshot(), s.requiresAttention(), null))
            .toList(),
        e.getAfter().stream()
            .map(s -> new OrderSpecialServiceResponse(s.snapshot(), s.requiresAttention(), null))
            .toList(),
        e.getPreviousTotalFee(),
        e.getTotalFee(),
        e.getResolution(),
        e.getRejectionEventId());
  }
}
