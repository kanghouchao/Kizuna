package com.kizuna.order.api.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.kizuna.order.domain.GuestContactConsent;
import java.util.List;

public record OrderApplicationDetailResponse(
    @JsonUnwrapped OrderApplicationResponse application,
    GuestContactConsent contactConsent,
    List<BusinessContactPermissionResponse> businessContactPermissions,
    List<GuestContactImportResponse> contactImports) {}
