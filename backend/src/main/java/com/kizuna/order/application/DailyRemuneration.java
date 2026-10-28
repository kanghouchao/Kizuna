package com.kizuna.order.application;

import com.kizuna.order.domain.RemunerationOrderRow;
import java.time.LocalDate;
import org.springframework.data.domain.Page;

public record DailyRemuneration(
    Long personId,
    String name,
    LocalDate businessDate,
    long totalRemuneration,
    Page<RemunerationOrderRow> orders) {}
