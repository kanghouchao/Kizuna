package com.kizuna.user.api.dto;

import java.util.List;

/** 権限目録の一つの木の中の区分見出しごとの束。group は PermissionGroup enum 名、permissions は空にならない。 */
public record PermissionGroupResponse(
    String group, String label, List<PermissionResponse> permissions) {}
