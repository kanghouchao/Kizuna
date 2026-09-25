package com.kizuna.order.api.dto;

import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BusinessContactPermissionRequest(
    @NotNull(message = "連絡先の種類を選択してください") ContactType type,
    @NotNull(message = "今回の連絡可否を選択してください") ContactPermissionStatus status,
    @NotBlank(message = "出所を入力してください") @Size(max = 200, message = "出所は200文字以内で入力してください")
        String source,
    @NotBlank(message = "根拠を入力してください") @Size(max = 2000, message = "根拠は2000文字以内で入力してください")
        String reason) {}
