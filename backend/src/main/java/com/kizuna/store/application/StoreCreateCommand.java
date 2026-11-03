package com.kizuna.store.application;

/** 店舗の新規登録の命令。ドメインは登録後に変えられない。 */
public record StoreCreateCommand(String name, String shortName, String domain) {}
