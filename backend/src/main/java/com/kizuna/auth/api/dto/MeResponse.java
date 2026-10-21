package com.kizuna.auth.api.dto;

import java.util.List;

/**
 * GET /me の応答。JSON キーは Jackson 設定により snake_case（display_name / user_type / line_linked）。
 *
 * <p>{@code permissions} は保持ロールの権限並集（enum 名の昇順）。画面の出し分けはこれと本人種別だけで決まり、 「どのコンソールに着地するか」のような導出値は持たない。
 *
 * <p>{@code lineLinked} は LINE アカウントを連携済みか。LINE ユーザー ID 自体は本人にも返さない（連携の有無だけが 画面の分岐に必要な情報）。
 */
public record MeResponse(
    String email,
    String displayName,
    String userType,
    List<String> permissions,
    boolean lineLinked) {}
