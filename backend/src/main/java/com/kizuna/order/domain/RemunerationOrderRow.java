package com.kizuna.order.domain;

import java.time.LocalDate;

/** 報酬明細の受注 1 行（読み側 projection）。 */
public record RemunerationOrderRow(
    String orderId,
    LocalDate businessDate,
    String serviceSummary,
    int accruedRemuneration,
    boolean completionInvalidated) {}
