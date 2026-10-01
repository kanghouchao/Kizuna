package com.kizuna.order.api.dto;

import org.springframework.data.domain.Page;

public record PlatformMonthlyRemunerationResponse(
    Long storeId,
    String storeName,
    Long personId,
    String name,
    String month,
    long totalRemuneration,
    Page<MonthlyRemunerationOrderSummary> orders) {}
