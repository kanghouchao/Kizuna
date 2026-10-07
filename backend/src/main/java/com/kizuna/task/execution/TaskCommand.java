package com.kizuna.task.execution;

import com.kizuna.shared.exception.ServiceException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record TaskCommand(
    String taskName,
    String logicalKey,
    Long serviceUserId,
    Long storeId,
    LocalDate periodStart,
    LocalDate periodEnd) {
  public TaskCommand {
    if (taskName == null
        || !taskName.matches("[A-Z][A-Z0-9_]{0,59}")
        || logicalKey == null
        || !logicalKey.matches("[a-zA-Z0-9.:_-]{1,120}")
        || serviceUserId == null
        || serviceUserId <= 0
        || storeId != null && storeId <= 0
        || periodStart == null
        || periodEnd == null
        || periodEnd.isBefore(periodStart)
        || ChronoUnit.DAYS.between(periodStart, periodEnd) > 366) {
      throw new ServiceException("処理名・実行キー・主体・対象期間の指定が不正です");
    }
  }
}
