package com.kizuna.order.application;

import com.kizuna.order.domain.RemunerationOrderRow;
import java.time.LocalDate;
import org.springframework.data.domain.Page;

public record SelfDailyRemuneration(
    Long storeId,
    String storeName,
    LocalDate businessDate,
    long totalRemuneration,
    Page<RemunerationOrderRow> orders) {}
