package com.kizuna.service.api.dto;

import com.kizuna.service.domain.ConsentDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record OwnConsentRequest(
    @NotNull(message = "条件版を指定してください") @Positive(message = "条件版は 1 以上で指定してください") Long termsVersion,
    @NotNull(message = "意思版を指定してください") @PositiveOrZero(message = "意思版は零以上で指定してください")
        Long consentVersion,
    @NotNull(message = "受諾または拒否を指定してください") ConsentDecision decision) {}
