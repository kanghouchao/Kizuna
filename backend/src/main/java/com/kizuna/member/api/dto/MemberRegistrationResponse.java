package com.kizuna.member.api.dto;

/** 会員登録の応答。発行された会員コードを返す（JSON キーは snake_case: member_code）。 */
public record MemberRegistrationResponse(String memberCode) {}
