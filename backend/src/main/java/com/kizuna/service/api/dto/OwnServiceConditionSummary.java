package com.kizuna.service.api.dto;

import com.kizuna.service.application.OwnServiceCondition;
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
    Long consentVersion) {
  public static OwnServiceConditionSummary of(OwnServiceCondition c) {
    return new OwnServiceConditionSummary(
        c.id(),
        c.storeId().toString(),
        c.kind(),
        c.name(),
        c.durationMinutes(),
        c.chargeType(),
        c.price(),
        c.remuneration(),
        c.termsVersion(),
        c.consentStatus(),
        c.consentVersion());
  }
}
