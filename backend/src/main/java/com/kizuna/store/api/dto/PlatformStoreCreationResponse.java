package com.kizuna.store.api.dto;

/** 店舗作成の応答。呼出側が一覧を引き直さずに新しい店舗を名指しできるよう id だけを返す。 */
public record PlatformStoreCreationResponse(Long id) {}
