package com.kizuna.service.api.dto;

import com.kizuna.service.domain.ServiceRevision.Operation;
import java.time.OffsetDateTime;

public record ServiceRevisionResponse(
    String id,
    long version,
    Operation operation,
    String actorId,
    OffsetDateTime occurredAt,
    ServiceSummary before,
    ServiceSummary after) {}
