package com.kizuna.order.api.dto;

import java.time.LocalDate;

public record MonthlyRemunerationOrderSummary(
    String orderId,
    LocalDate businessDate,
    String serviceSummary,
    int accruedRemuneration,
    boolean completionInvalidated) {}
