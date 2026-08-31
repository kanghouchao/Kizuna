package com.kizuna.user.api.dto;

/** サービスID応答に埋め込むロールへの参照（id と名称）。一覧・詳細の両応答が共有する。 */
public record ServiceIdentityRoleRef(Long id, String name) {}
