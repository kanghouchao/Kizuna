package com.kizuna.auth.infrastructure;

/**
 * LINE が検証した本人同一性。
 *
 * @param lineUserId id_token の sub（LINE ユーザー ID）。身分の同一性はこの値だけで判定する
 * @param displayName id_token の name（LINE プロフィール表示名。登録画面の初期値に使う）
 */
public record LineIdentity(String lineUserId, String displayName) {}
