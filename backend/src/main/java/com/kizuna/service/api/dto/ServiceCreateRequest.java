package com.kizuna.service.api.dto;

import com.kizuna.service.domain.ChargeType;
import com.kizuna.service.domain.ServiceKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ServiceCreateRequest(
    @NotNull(message = "必須項目を指定してください") ServiceKind kind,
    @NotBlank(message = "名称を入力してください") String name,
    Integer durationMinutes,
    ChargeType chargeType,
    @NotNull(message = "必須項目を指定してください") @Min(value = 0, message = "零以上の整数円を指定してください") Integer price,
    @NotNull(message = "必須項目を指定してください") @Min(value = 0, message = "零以上の整数円を指定してください")
        Integer remuneration) {}
