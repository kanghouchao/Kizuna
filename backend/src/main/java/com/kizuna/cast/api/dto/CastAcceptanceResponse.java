package com.kizuna.cast.api.dto;

/** 招待受諾の完了応答。JSON キーは Jackson 設定により snake_case（store_name）。 完了画面の「◯◯（店舗名）との連携が完了しました」表示に使う。 */
public record CastAcceptanceResponse(String storeName) {}
