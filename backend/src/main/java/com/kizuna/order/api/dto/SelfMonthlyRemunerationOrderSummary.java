package com.kizuna.order.api.dto;

import java.time.LocalDate;

public record SelfMonthlyRemunerationOrderSummary(
    String orderId,
    LocalDate businessDate,
    String serviceSummary,
    int accruedRemuneration,
    boolean completionInvalidated) {}
