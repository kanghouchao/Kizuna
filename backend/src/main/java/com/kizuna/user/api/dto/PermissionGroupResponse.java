package com.kizuna.user.api.dto;

import java.util.List;

/** 権限目録の業務領域ごとの束。group は PermissionGroup enum 名、permissions は空にならない。 */
public record PermissionGroupResponse(
    String group, String label, List<PermissionResponse> permissions) {}
