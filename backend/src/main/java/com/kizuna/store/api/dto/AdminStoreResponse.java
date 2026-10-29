package com.kizuna.store.api.dto;

import com.kizuna.store.domain.StoreStatus;

/** 授権店舗一覧の要素。JSON キーは id / name / short_name / status。status は店舗設定が準備中・稼働中を示すために使う。 */
public record AdminStoreResponse(Long id, String name, String shortName, StoreStatus status) {}
