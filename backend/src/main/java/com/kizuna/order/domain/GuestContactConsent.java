package com.kizuna.order.domain;

import java.time.OffsetDateTime;

public record GuestContactConsent(
    String version,
    String businessText,
    String marketingText,
    boolean businessAllowed,
    boolean marketingAllowed,
    OffsetDateTime acquiredAt) {}
