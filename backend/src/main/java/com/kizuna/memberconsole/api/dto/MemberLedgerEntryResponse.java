package com.kizuna.memberconsole.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 職員が見る明細 1 行。本人向けと違い理由と実行者の表示名を持つ。 */
public record MemberLedgerEntryResponse(
    OffsetDateTime occurredAt,
    String entryType,
    int amount,
    LocalDate expiresOn,
    String storeName,
    String reason,
    String actorName) {}
