package com.kizuna.point.application;

import com.kizuna.point.domain.PointEntryType;
import java.time.LocalDate;

/** 会員本人に見せるポイント明細 1 行。記帳日は業務のタイムゾーンで畳んだ日付。 */
public record MemberPointEntry(
    LocalDate occurredOn,
    String storeName,
    PointEntryType entryType,
    int amount,
    LocalDate expiresOn) {}
