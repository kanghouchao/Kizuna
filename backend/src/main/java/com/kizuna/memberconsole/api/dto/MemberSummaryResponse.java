package com.kizuna.memberconsole.api.dto;

import java.time.OffsetDateTime;

/** 会員一覧 1 行。退会済みは {@code withdrawnAt} を持つ。 */
public record MemberSummaryResponse(
    long id,
    String memberCode,
    String displayName,
    String email,
    String rank,
    OffsetDateTime registeredAt,
    OffsetDateTime withdrawnAt) {}
