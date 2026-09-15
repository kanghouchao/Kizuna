package com.kizuna.service.api.dto;

import com.kizuna.service.domain.ChargeType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ServiceUpdateRequest(
    @NotBlank(message = "名称を入力してください") String name,
    Integer durationMinutes,
    ChargeType chargeType,
    @NotNull(message = "必須項目を指定してください") @Min(value = 0, message = "零以上の整数円を指定してください") Integer price,
    @NotNull(message = "必須項目を指定してください") @Min(value = 0, message = "零以上の整数円を指定してください")
        Integer remuneration,
    @NotNull(message = "必須項目を指定してください") @Min(value = 1, message = "版本は 1 以上で指定してください")
        Long expectedVersion) {}
