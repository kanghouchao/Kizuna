package com.kizuna.member.api.dto;

import com.kizuna.member.application.MemberHome;

/** 会員ポータルホームの応答。本人の会員コードと表示名のみ（JSON キーは snake_case）。 */
public record MemberHomeResponse(String memberCode, String displayName) {
  public static MemberHomeResponse of(MemberHome home) {
    return new MemberHomeResponse(home.memberCode(), home.displayName());
  }
}
