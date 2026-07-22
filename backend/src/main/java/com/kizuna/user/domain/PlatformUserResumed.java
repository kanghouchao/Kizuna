package com.kizuna.user.domain;

/**
 * プラットフォームユーザーが再開された（enabled=true）ことを表すドメインイベント。
 *
 * <p>auth モジュールが停止時に登録したユーザー単位ブラックリストを即時解除するために購読する。イベント経由にしている理由（モジュール環の回避）は {@link
 * PlatformUserStopped} を参照。
 */
public record PlatformUserResumed(String email) {}
