package com.kizuna.order.api.dto;

import com.kizuna.customer.domain.ContactType;
import jakarta.validation.constraints.NotNull;

public record GuestContactImportRequest(
    @NotNull ContactType type, String contactId, @NotNull Boolean importMarketingConsent) {}
