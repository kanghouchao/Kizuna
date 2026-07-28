package com.kizuna.user.api.dto;

/** 権限目録 1 件の応答。code は PermissionCode enum 名、console は所属コンソール（PLATFORM / STORE / SHARED）。 */
public record PermissionResponse(String code, String console) {}
