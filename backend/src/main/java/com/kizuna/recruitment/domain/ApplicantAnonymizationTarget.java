package com.kizuna.recruitment.domain;

import java.time.OffsetDateTime;

public record ApplicantAnonymizationTarget(
    String id, Long storeId, OffsetDateTime transitionedAt) {}
