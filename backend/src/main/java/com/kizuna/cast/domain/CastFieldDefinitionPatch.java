package com.kizuna.cast.domain;

/** カスタムフィールド定義の部分更新コマンド。null のフィールドは「変更しない」。key は不変のため含めない。 */
public record CastFieldDefinitionPatch(String label, Integer displayOrder, Boolean isPublic) {}
