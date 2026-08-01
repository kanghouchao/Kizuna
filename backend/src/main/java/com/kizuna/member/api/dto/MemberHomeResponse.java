package com.kizuna.member.api.dto;

/** 会員ポータルホームの応答。本人の会員コードと表示名のみ（JSON キーは snake_case）。 */
public record MemberHomeResponse(String memberCode, String displayName) {}
