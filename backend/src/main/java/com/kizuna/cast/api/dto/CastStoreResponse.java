package com.kizuna.cast.api.dto;

/** 本人（キャスト）所属店舗セレクタの1件。JSON キーは Jackson 設定により snake_case（store_id/store_name）。 */
public record CastStoreResponse(Long storeId, String storeName) {}
