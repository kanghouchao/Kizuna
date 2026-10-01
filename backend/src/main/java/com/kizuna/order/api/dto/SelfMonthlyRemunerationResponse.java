package com.kizuna.order.api.dto;

import org.springframework.data.domain.Page;

public record SelfMonthlyRemunerationResponse(
    Long storeId,
    String storeName,
    String month,
    long totalRemuneration,
    Page<SelfMonthlyRemunerationOrderSummary> orders) {}
