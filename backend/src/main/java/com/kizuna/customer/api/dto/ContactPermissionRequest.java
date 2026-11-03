package com.kizuna.customer.api.dto;

import com.kizuna.customer.application.CustomerCommands.PermissionChange;
import com.kizuna.customer.domain.ContactPermissionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ContactPermissionRequest(
    @NotNull ContactPermissionStatus status,
    @NotBlank @Size(max = 200) String source,
    @NotBlank @Size(max = 2000) String reason) {
  public ContactPermissionRequest {
    if (source != null) source = source.strip();
    if (reason != null) reason = reason.strip();
  }

  public PermissionChange command() {
    return new PermissionChange(status, source, reason);
  }
}
