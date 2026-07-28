package com.kizuna.user.api.dto;

import java.util.List;

/**
 * ロールの応答。permissions は権限コード（PermissionCode enum 名）の昇順で、権限行の id は API 面に出さない。system は平台既定ロール（変更・削除不可）。
 * version は楽観ロックの往復用（編集リクエストがそのまま返送し、不一致は 409）。
 */
public record RoleResponse(
    Long id, String name, boolean system, List<String> permissions, long version) {}
