package com.kizuna.cast.application;

/** 新規登録で招待を受諾する本人アカウントの入力。email は保存時に正規化しない（重複照合だけ小文字で引く）。 */
public record CastNewUserAcceptCommand(String email, String password, String displayName) {}
