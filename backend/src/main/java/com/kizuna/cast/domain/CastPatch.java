package com.kizuna.cast.domain;

/** キャストの部分更新コマンド。null のフィールドは「変更しない」を意味する（従来の IGNORE 戦略と同じ意味論）。 */
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
    Integer displayOrder) {}
