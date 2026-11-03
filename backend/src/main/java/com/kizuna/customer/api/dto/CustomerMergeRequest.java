package com.kizuna.customer.api.dto;

import com.kizuna.customer.domain.MergePreferences;
import com.kizuna.customer.domain.MergeProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CustomerMergeRequest(
    @NotBlank @Size(max = 64) String mergedCustomerId,
    @NotBlank String previewToken,
    @Valid MergeProfile profile,
    @NotNull @Valid MergePreferences preferredContacts,
    @NotNull @AssertTrue Boolean warningsAcknowledged,
    @NotBlank @Size(max = 500) String operationReason) {
  public CustomerMergeRequest {
    operationReason = operationReason == null ? null : operationReason.strip();
  }

  public CustomerMergePreviewRequest preview() {
    return new CustomerMergePreviewRequest(mergedCustomerId, profile, preferredContacts);
  }
}
