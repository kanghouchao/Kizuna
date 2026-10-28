package com.kizuna.order.domain;

/** キャスト本人が報酬を持つ店舗 1 行（読み側 projection）。 */
public record RemunerationStoreRow(Long storeId, String storeName) {}
