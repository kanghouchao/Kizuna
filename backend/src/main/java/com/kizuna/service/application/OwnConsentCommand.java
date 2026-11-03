package com.kizuna.service.application;

import com.kizuna.service.domain.ConsentDecision;

public record OwnConsentCommand(long termsVersion, long consentVersion, ConsentDecision decision) {}
