package com.kizuna.cast.domain;

import java.util.Map;

/** キャストの部分更新コマンド。null のフィールドは「変更しない」を意味する。 */
public record CastPatch(
    String name,
    String status,
    String photoUrl,
    String introduction,
    Integer age,
    Integer height,
    Integer bust,
    Integer waist,
    Integer hip,
    Integer displayOrder,
    Map<String, String> customFields) {}
