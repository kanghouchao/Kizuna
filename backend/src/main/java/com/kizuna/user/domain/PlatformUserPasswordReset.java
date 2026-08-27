package com.kizuna.user.domain;

/**
 * プラットフォームユーザーのパスワードが再設定されたことを表すドメインイベント。
 *
 * <p>auth モジュールが再設定前に発行済みの JWT を即時失効させるために購読する。イベント経由にしている理由（モジュール環の回避）は {@link
 * PlatformUserStopped} を参照。
 */
public record PlatformUserPasswordReset(String email) {}
