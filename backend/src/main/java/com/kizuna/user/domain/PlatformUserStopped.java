package com.kizuna.user.domain;

/**
 * プラットフォームユーザーが停止された（enabled=false）ことを表すドメインイベント。
 *
 * <p>auth モジュールが発行済み JWT をユーザー単位で即時失効させるために購読する。イベント経由にしているのは、user.application から
 * auth.infrastructure の {@code TokenBlacklistService} を直接注入すると user→auth の依存が生まれ、既存の auth→user
 * 依存と合わせてモジュール環になってしまうため（{@code ModularityTests} が red になる）。user が発行し auth が購読することで、 依存の向きは
 * auth→user のまま保たれる。
 */
public record PlatformUserStopped(String email) {}
