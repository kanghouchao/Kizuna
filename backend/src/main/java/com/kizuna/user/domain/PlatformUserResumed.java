package com.kizuna.user.domain;

/**
 * プラットフォームユーザーが再開された（enabled=true）ことを表すドメインイベント。
 *
 * <p>auth モジュールが停止時に登録したユーザー単位ブラックリストを即時解除するために購読する。イベント経由にしているのは、user.application から
 * auth.infrastructure の {@code TokenBlacklistService} を直接注入すると user→auth の依存が生まれ、既存の auth→user
 * 依存と合わせてモジュール環になってしまうため（{@code ModularityTests} が red になる）。user が発行し auth が購読することで、 依存の向きは
 * auth→user のまま保たれる。
 */
public record PlatformUserResumed(String email) {}
