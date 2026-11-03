package com.kizuna.cast.application;

import java.time.OffsetDateTime;

/** 招待照会の結果。status は VALID・EXPIRED・USED・UNAVAILABLE のいずれか。店舗が消えていれば storeName は null。 */
public record CastInvitationDetail(
    String storeName, String castName, String status, OffsetDateTime expiresAt) {}
