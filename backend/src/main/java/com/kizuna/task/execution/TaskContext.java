package com.kizuna.task.execution;

import java.time.LocalDate;

public record TaskContext(
    Long executionId, Long serviceUserId, LocalDate periodStart, LocalDate periodEnd) {}
