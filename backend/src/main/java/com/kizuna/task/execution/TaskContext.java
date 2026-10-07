package com.kizuna.task.execution;

import java.time.LocalDate;

public record TaskContext(
    Long executionId,
    Long serviceUserId,
    Long storeId,
    LocalDate periodStart,
    LocalDate periodEnd) {}
