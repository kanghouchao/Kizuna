package com.kizuna.service.api.dto;

import com.kizuna.service.domain.ChargeType;
import com.kizuna.service.domain.ConsentStatus;
import com.kizuna.service.domain.ServiceKind;

public record OwnServiceConditionSummary(
    String id,
    String storeId,
    ServiceKind kind,
    String name,
    Integer durationMinutes,
    ChargeType chargeType,
    int price,
    int remuneration,
    long termsVersion,
    ConsentStatus consentStatus,
    Long consentVersion) {}
