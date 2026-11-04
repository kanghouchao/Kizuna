package com.kizuna.membernotification.api.dto;

import com.kizuna.customer.domain.ContactPermissionStatus;
import jakarta.validation.constraints.NotNull;

/** 一つの用途についての本人の選択。許可か拒否で、未確認は集約が 400 で拒む（未確認は行の不在で表す）。 */
public record MemberNotificationSettingRequest(
    @NotNull(message = "許可か拒否を選んでください") ContactPermissionStatus status) {}
