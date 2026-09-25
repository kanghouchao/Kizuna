package com.kizuna.order.api.dto;

import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BusinessContactPermissionRequest(
    @NotNull ContactType type,
    @NotNull ContactPermissionStatus status,
    @NotBlank @Size(max = 200) String source,
    @NotBlank @Size(max = 2000) String reason) {}
