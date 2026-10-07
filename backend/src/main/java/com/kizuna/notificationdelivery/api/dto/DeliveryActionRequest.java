package com.kizuna.notificationdelivery.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record DeliveryActionRequest(
    @NotNull @PositiveOrZero Long version, @NotBlank @Size(max = 500) String reason) {}
