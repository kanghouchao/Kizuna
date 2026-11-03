package com.kizuna.cast.application;

/** カスタムフィールド定義の作成入力。isPublic の null は非公開として扱う。 */
public record CastFieldDefinitionCreateCommand(String key, String label, Boolean isPublic) {}
