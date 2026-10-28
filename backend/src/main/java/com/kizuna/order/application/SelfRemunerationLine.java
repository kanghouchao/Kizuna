package com.kizuna.order.application;

import java.time.OffsetDateTime;

public record SelfRemunerationLine(
    String kind,
    String name,
    int price,
    int remuneration,
    String lineId,
    Integer durationMinutes,
    String serviceId,
    String revisionId,
    Long revisionNumber,
    String adoptionBasis,
    OffsetDateTime adoptedAt,
    String chargeType,
    Long termsVersion,
    String consentEventId,
    Long consentVersion) {}
