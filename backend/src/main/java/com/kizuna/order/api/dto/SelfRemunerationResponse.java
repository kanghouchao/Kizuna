package com.kizuna.order.api.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

public record SelfRemunerationResponse(
    @JsonUnwrapped SelfRemunerationSummary summary,
    long version,
    List<SelfRemunerationItem> items) {}
