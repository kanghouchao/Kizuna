package com.kizuna.service.api.dto;

import com.kizuna.service.domain.ChargeType;
import com.kizuna.service.domain.ServiceKind;
import java.time.OffsetDateTime;

public record ServiceResponse(
    String id,
    ServiceKind kind,
    String name,
    Integer durationMinutes,
    ChargeType chargeType,
    int price,
    int remuneration,
    long version,
    boolean deleted,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
