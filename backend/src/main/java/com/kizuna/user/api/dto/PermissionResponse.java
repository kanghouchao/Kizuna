package com.kizuna.user.api.dto;

/** 権限目録 1 件の応答。code は PermissionCode enum 名、label は画面に出す日本語名。 */
public record PermissionResponse(String code, String label) {}
