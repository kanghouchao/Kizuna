package com.kizuna.user.api.dto;

import java.util.List;

/** 権限目録の木（グループ / 店舗）ごとの束。tree は PermissionTree enum 名、groups は空にならない。 */
public record PermissionTreeResponse(
    String tree, String label, List<PermissionGroupResponse> groups) {}
