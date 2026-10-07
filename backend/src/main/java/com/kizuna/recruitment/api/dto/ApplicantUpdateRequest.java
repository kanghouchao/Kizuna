package com.kizuna.recruitment.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ApplicantUpdateRequest(
    @NotNull(message = "必須項目を指定してください") @PositiveOrZero(message = "版は0以上で指定してください") Long version,
    @NotNull(message = "必須項目を指定してください") @Valid ApplicantIntakeRequest intake) {}
