package com.kizuna.user.api.dto;

/** 最初の管理者の登録の応答。トークンは発行せず、ログインは呼出側が既存のログイン API で行う。 */
public record InitialSetupCreationResponse(Long id) {}
