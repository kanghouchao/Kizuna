package com.kizuna.service.api.dto;

import com.kizuna.service.domain.ChargeType;
import com.kizuna.service.domain.ServiceKind;

public record ServiceSummary(
    String id,
    ServiceKind kind,
    String name,
    Integer durationMinutes,
    ChargeType chargeType,
    int price,
    int remuneration,
    long version,
    boolean deleted) {}
