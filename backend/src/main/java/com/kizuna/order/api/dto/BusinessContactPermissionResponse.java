package com.kizuna.order.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.order.contact.BusinessContactDecision;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record BusinessContactPermissionResponse(
    ContactType type,
    String value,
    ContactPermissionStatus status,
    String source,
    String reason,
    Long recordedBy,
    OffsetDateTime recordedAt,
    BusinessContactDecision decision) {}
