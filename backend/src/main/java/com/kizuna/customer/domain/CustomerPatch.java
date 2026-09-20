package com.kizuna.customer.domain;

/** 顧客の部分更新コマンド。null のフィールドは「変更しない」を意味する。 */
public record CustomerPatch(
    String name,
    String address,
    String buildingName,
    String classification,
    Boolean hasPet,
    String usageAreas,
    String ngType,
    String ngContent) {}
