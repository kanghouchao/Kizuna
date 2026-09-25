package com.kizuna.order.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GuestContactConsentRequest(
    @NotBlank String version,
    @NotNull @AssertTrue(message = "今回の予約に関する業務連絡への同意が必要です") Boolean businessAllowed,
    @NotNull Boolean marketingAllowed) {}
