package com.kizuna.notificationdelivery.api.dto;

import com.kizuna.notificationdelivery.domain.DeliveryResolution;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record DeliveryResolutionRequest(
    @NotNull @PositiveOrZero Long version,
    @NotNull DeliveryResolution outcome,
    @NotBlank @Size(max = 500) String reason) {}
