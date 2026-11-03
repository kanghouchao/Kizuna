package com.kizuna.customer.api.dto;

import com.kizuna.customer.application.CustomerCommands.MergeSelection;
import com.kizuna.customer.domain.MergePreferences;
import com.kizuna.customer.domain.MergeProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerMergePreviewRequest(
    @NotBlank @Size(max = 64) String mergedCustomerId,
    @Valid MergeProfile profile,
    @Valid MergePreferences preferredContacts) {
  public MergeSelection command() {
    return new MergeSelection(mergedCustomerId, profile, preferredContacts);
  }
}
