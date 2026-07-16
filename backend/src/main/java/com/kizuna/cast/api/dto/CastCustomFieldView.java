package com.kizuna.cast.api.dto;

/** 公開詳細ページ向けのカスタムフィールド1件（key・label・value）。 */
public record CastCustomFieldView(String key, String label, String value) {}
