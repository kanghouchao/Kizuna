package com.kizuna.order.api.dto;

import java.time.LocalDate;
import org.springframework.data.domain.Page;

public record SelfDailyRemunerationResponse(
    Long storeId,
    String storeName,
    LocalDate businessDate,
    long totalRemuneration,
    Page<SelfMonthlyRemunerationOrderSummary> orders) {}
