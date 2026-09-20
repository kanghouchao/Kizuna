package com.kizuna.customer.api.dto;

import com.kizuna.customer.domain.ContactType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ContactRequest(@NotNull ContactType type, @NotBlank @Size(max = 320) String value) {}
