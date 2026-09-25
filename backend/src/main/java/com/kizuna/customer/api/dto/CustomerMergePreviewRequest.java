package com.kizuna.customer.api.dto;

import com.kizuna.customer.domain.MergePreferences;
import com.kizuna.customer.domain.MergeProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CustomerMergePreviewRequest(
    @NotBlank @Size(max = 64) String mergedCustomerId,
    @Valid @JsonDeserialize(using = MergeInputDeserializer.Profile.class) MergeProfile profile,
    @Valid @JsonDeserialize(using = MergeInputDeserializer.Preferences.class)
        MergePreferences preferredContacts) {}
