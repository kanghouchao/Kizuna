package com.kizuna.store.domain;

/** 店舗の編集可能項目（ドメインは変更できない）。 */
public record StorePatch(String name, String shortName) {}
