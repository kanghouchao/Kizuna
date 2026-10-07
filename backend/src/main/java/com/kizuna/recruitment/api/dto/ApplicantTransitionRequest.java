package com.kizuna.recruitment.api.dto;

import com.kizuna.recruitment.domain.ApplicantStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ApplicantTransitionRequest(
    @NotNull(message = "必須項目を指定してください") @PositiveOrZero(message = "版は0以上で指定してください") Long version,
    @NotNull(message = "必須項目を指定してください") ApplicantStatus status,
    @NotBlank(message = "必須項目を入力してください") @Size(max = 1000, message = "1000文字または件以内で指定してください")
        String reason) {}
