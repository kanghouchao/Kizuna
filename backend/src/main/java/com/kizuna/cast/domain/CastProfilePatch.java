package com.kizuna.cast.domain;

/** キャストの部分更新コマンド。null のフィールドは「変更しない」を意味する。 */
public record CastProfilePatch(
    String name,
    String photoUrl,
    String introduction,
    Integer age,
    Integer height,
    Integer bust,
    Integer waist,
    Integer hip,
    Integer displayOrder) {}
