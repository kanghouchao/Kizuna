package com.kizuna.auth.api.dto;

import java.util.List;

/**
 * GET /platform/me の応答。JSON キーは Jackson 設定により snake_case（display_name / user_type /
 * store_scope_type / store_ids）。
 *
 * <p>{@code capabilities} は保持束の能力並集（enum 名の昇順）。{@code console} はログイン後の着地先（central / store /
 * none）で、能力目録から導出する — フロントエンドに能力→コンソールの対応表を複製させない。
 */
public record PlatformMeResponse(
    String email,
    String displayName,
    String userType,
    List<String> capabilities,
    String console,
    String storeScopeType,
    List<Long> storeIds) {}
