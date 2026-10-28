package com.kizuna.order.application;

import com.kizuna.order.domain.RemunerationOrderRow;
import org.springframework.data.domain.Page;

public record MonthlyRemuneration(
    Long personId,
    String name,
    String month,
    long totalRemuneration,
    Page<RemunerationOrderRow> orders) {}
