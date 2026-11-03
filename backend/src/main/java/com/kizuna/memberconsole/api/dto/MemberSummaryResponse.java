package com.kizuna.memberconsole.api.dto;

import com.kizuna.member.application.MemberDirectoryService.MemberSummary;
import java.time.OffsetDateTime;

/** 会員一覧 1 行。退会済みは {@code withdrawnAt} を持つ。 */
public record MemberSummaryResponse(
    long id,
    String memberCode,
    String displayName,
    String email,
    String rank,
    OffsetDateTime registeredAt,
    OffsetDateTime withdrawnAt) {
  public static MemberSummaryResponse of(MemberSummary row) {
    return new MemberSummaryResponse(
        row.id(),
        row.memberCode(),
        row.displayName(),
        row.email(),
        row.rank(),
        row.registeredAt(),
        row.withdrawnAt());
  }
}
