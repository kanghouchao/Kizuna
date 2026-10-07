package com.kizuna.task.api.dto;

import com.kizuna.task.execution.TaskCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TaskExecutionRequest(
    @NotBlank(message = "処理名を指定してください") @Size(max = 60, message = "処理名は60文字以内で指定してください")
        String taskName,
    @NotBlank(message = "実行キーを指定してください") @Size(max = 120, message = "実行キーは120文字以内で指定してください")
        String logicalKey,
    @NotNull(message = "実行主体を指定してください") @Positive(message = "実行主体の指定が不正です") Long serviceUserId,
    @Positive(message = "店舗の指定が不正です") Long storeId,
    @NotNull(message = "対象開始日を指定してください") LocalDate periodStart,
    @NotNull(message = "対象終了日を指定してください") LocalDate periodEnd) {
  public TaskCommand command() {
    return new TaskCommand(taskName, logicalKey, serviceUserId, storeId, periodStart, periodEnd);
  }
}
