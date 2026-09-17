package com.kizuna.order.api.dto;

import com.kizuna.order.domain.OrderStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SelfRemunerationSummary(
    String orderId,
    String enrollmentId,
    Long storeId,
    String storeName,
    LocalDate businessDate,
    OffsetDateTime completedAt,
    OrderStatus status,
    boolean completionInvalidated,
    int agreedRemuneration,
    int plannedRemuneration,
    int accruedRemuneration) {}
