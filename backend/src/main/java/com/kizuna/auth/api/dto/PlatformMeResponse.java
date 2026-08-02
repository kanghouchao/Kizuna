package com.kizuna.auth.api.dto;

import java.util.List;

/**
 * GET /platform/me の応答。JSON キーは Jackson 設定により snake_case（display_name / user_type / store_bridge /
 * store_scope_type / store_ids）。
 *
 * <p>{@code permissions} は保持ロールの権限並集（enum 名の昇順）。{@code console} はログイン後の着地先（platform / store /
 * none）で、権限目録から導出する — フロントエンドに権限→コンソールの対応表を複製させない。
 *
 * <p>{@code storeBridge} は店舗文脈（X-Store-ID）を確立できるか。JWT の {@code storeBridge} claim を設定するのと同一の私有
 * {@code PlatformAuthService.hasStoreConsole()} から導出する — {@code console}
 * と同一原則で、フロントエンドに権限→コンソールの対応表を複製させない。
 *
 * <p>{@code lineLinked} は LINE アカウントを連携済みか。LINE ユーザー ID 自体は本人にも返さない（連携の有無だけが 画面の分岐に必要な情報）。
 */
public record PlatformMeResponse(
    String email,
    String displayName,
    String userType,
    List<String> permissions,
    String console,
    boolean storeBridge,
    String storeScopeType,
    List<Long> storeIds,
    boolean lineLinked) {}
