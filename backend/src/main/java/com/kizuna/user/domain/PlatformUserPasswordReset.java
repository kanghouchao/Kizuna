package com.kizuna.user.domain;

/**
 * プラットフォームユーザーのパスワードが再設定されたことを表すドメインイベント。
 *
 * <p>auth モジュールが再設定前に発行済みの JWT を即時失効させるために購読する。イベント経由にしている理由（モジュール環の回避）は {@link
 * PlatformUserStopped} を参照。
 *
 * <p>失効境界（resetAtSeconds）は再設定自身が運ぶ — callback の実行時刻で刻むと、遅延した古い callback が
 * 新しい再設定より後の時刻を書き、最新パスワードで得た正当トークンを誤失効させる。
 */
public record PlatformUserPasswordReset(String email, long resetAtSeconds) {}
