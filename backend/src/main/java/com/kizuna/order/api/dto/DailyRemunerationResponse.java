package com.kizuna.order.api.dto;

import java.time.LocalDate;
import org.springframework.data.domain.Page;

public record DailyRemunerationResponse(
    Long personId,
    String name,
    LocalDate businessDate,
    long totalRemuneration,
    Page<MonthlyRemunerationOrderSummary> orders) {}
