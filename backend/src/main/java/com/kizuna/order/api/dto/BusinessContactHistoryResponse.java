package com.kizuna.order.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.order.domain.BusinessContactHistory;
import com.kizuna.order.domain.BusinessContactState;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record BusinessContactHistoryResponse(
    String id,
    ContactType type,
    String action,
    BusinessContactState before,
    BusinessContactState after,
    Long recordedBy,
    OffsetDateTime recordedAt) {
  public static BusinessContactHistoryResponse from(BusinessContactHistory h) {
    return new BusinessContactHistoryResponse(
        h.getId(),
        h.getType(),
        h.getAction(),
        h.getBefore(),
        h.getAfter(),
        h.getRecordedBy(),
        h.getRecordedAt());
  }
}
