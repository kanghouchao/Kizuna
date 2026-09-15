package com.kizuna.service.application;

import java.time.OffsetDateTime;

public record SpecialServiceTerms(
    String serviceId,
    String revisionId,
    long revisionNumber,
    long termsVersion,
    String name,
    String chargeType,
    int price,
    int remuneration,
    String consentEventId,
    Long consentVersion,
    OffsetDateTime occurredAt,
    boolean serviceDeleted) {}
