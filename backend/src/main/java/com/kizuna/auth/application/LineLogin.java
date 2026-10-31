package com.kizuna.auth.application;

/** LINE ログインの結果。連携済みなら {@code session}、未登録なら登録チケットと LINE 表示名を持つ。 */
public record LineLogin(IssuedSession session, String registrationTicket, String displayName) {}
