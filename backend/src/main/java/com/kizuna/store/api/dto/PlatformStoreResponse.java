package com.kizuna.store.api.dto;

/** 授権店舗一覧の要素（統一ログイン）。JSON キーは id / name。 */
public record PlatformStoreResponse(Long id, String name) {}
