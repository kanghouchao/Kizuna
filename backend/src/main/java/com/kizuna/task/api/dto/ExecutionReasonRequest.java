package com.kizuna.task.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExecutionReasonRequest(
    @NotBlank(message = "理由を入力してください") @Size(max = 500, message = "理由は500文字以内で入力してください")
        String reason) {}
