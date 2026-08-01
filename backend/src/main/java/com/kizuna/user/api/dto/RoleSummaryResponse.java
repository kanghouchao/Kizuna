package com.kizuna.user.api.dto;

/**
 * ロール一覧 1 件の要約応答。権限は個数（permission_count）のみで、権限コードの列挙と楽観ロック用 version は詳細（GET
 * /platform/roles/{id}）が持つ。system は平台既定ロール（変更・削除不可）。
 */
public record RoleSummaryResponse(Long id, String name, boolean system, long permissionCount) {}
