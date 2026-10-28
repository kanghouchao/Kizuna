package com.kizuna.order.application;

import com.kizuna.order.domain.RemunerationOrderRow;
import org.springframework.data.domain.Page;

public record SelfMonthlyRemuneration(
    Long storeId,
    String storeName,
    String month,
    long totalRemuneration,
    Page<RemunerationOrderRow> orders) {}
