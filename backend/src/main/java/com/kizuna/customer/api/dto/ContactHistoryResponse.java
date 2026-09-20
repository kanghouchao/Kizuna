package com.kizuna.customer.api.dto;

import com.kizuna.customer.domain.ContactAction;
import com.kizuna.customer.domain.ContactPurpose;
import com.kizuna.customer.domain.ContactState;
import java.time.OffsetDateTime;

public record ContactHistoryResponse(
    String id,
    String contactId,
    String originCustomerId,
    ContactAction action,
    Long actorId,
    OffsetDateTime occurredAt,
    ContactState before,
    ContactState after,
    String operationId,
    ContactPurpose purpose,
    String source,
    String reason,
    String sourceContactId) {}
