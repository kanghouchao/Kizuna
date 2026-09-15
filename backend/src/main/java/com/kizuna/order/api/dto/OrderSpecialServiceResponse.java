package com.kizuna.order.api.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.kizuna.order.domain.SpecialServiceSnapshot;

public record OrderSpecialServiceResponse(
    @JsonUnwrapped SpecialServiceSnapshot snapshot,
    boolean requiresAttention,
    String currentConsentStatus) {}
