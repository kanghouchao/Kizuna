package com.kizuna.task.api.dto;

import com.kizuna.task.execution.TaskCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TaskExecutionRequest(
    @NotBlank(message = "処理名を指定してください") @Size(max = 60, message = "処理名は60文字以内で指定してください")
        String taskName,
    @NotBlank(message = "実行キーを指定してください") @Size(max = 120, message = "実行キーは120文字以内で指定してください")
        String logicalKey,
    @NotNull(message = "実行主体を指定してください") @Positive(message = "実行主体の指定が不正です") Long serviceUserId,
    // 店舗の選択肢（TaskOptions.StoreOption）と同じく文字列で受け渡す。JS の安全な整数を超える id でも桁が落ちない。
    @Pattern(regexp = "[1-9][0-9]{0,17}", message = "店舗の指定が不正です") String storeId,
    @NotNull(message = "対象開始日を指定してください") LocalDate periodStart,
    @NotNull(message = "対象終了日を指定してください") LocalDate periodEnd) {
  public TaskCommand command() {
    return new TaskCommand(
        taskName,
        logicalKey,
        serviceUserId,
        storeId == null ? null : Long.valueOf(storeId),
        periodStart,
        periodEnd);
  }
}
