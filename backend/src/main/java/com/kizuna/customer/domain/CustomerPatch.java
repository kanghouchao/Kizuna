package com.kizuna.customer.domain;

/** 顧客の部分更新コマンド。null のフィールドは「変更しない」を意味する（従来の IGNORE 戦略と同じ意味論）。 */
public record CustomerPatch(
    String name,
    String phoneNumber,
    String phoneNumber2,
    String address,
    String buildingName,
    String classification,
    Boolean hasPet,
    String rank,
    String lineId,
    String usageAreas,
    String ngType,
    String ngContent) {}
