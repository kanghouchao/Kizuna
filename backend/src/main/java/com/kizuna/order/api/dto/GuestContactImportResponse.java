package com.kizuna.order.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.kizuna.customer.contact.GuestContactImport;
import java.time.OffsetDateTime;

public record GuestContactImportResponse(
    @JsonUnwrapped GuestContactImport contact,
    @JsonInclude(JsonInclude.Include.ALWAYS) Long recordedBy,
    OffsetDateTime recordedAt) {}
