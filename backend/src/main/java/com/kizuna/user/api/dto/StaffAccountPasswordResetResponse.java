package com.kizuna.user.api.dto;

/**
 * パスワード再設定の応答。仮パスワードの生値をこの一度だけ返す。JSON キーは Jackson 設定により snake_case（temporary_password）。
 *
 * <p>専用の型にしてあるのは、生値が一覧・詳細 DTO の型に存在してはならないためである（api-guidelines §6 — 実行時の抑制では漏出を防げない）。
 */
public record StaffAccountPasswordResetResponse(String temporaryPassword) {}
