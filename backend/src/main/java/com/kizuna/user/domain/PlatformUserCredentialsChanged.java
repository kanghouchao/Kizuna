package com.kizuna.user.domain;

/**
 * プラットフォームユーザーの資格情報の版が変わった（パスワード変更・再設定・停止）ことを表すドメインイベント。
 *
 * <p>auth モジュールが版キャッシュへ commit 後に反映するために購読する。イベント経由にしているのは、user.application から auth の失効機構を 直接注入すると
 * user→auth の依存が生まれ、既存の auth→user 依存と合わせてモジュール環になってしまうため（{@code ModularityTests} が red になる）。
 *
 * <p>credentialVersion はイベント自身が運ぶ確定値。JPA の {@code @Version} と違い flush 時でなく行動メソッド内で増えるため、発行時点で
 * 確定している。callback 側で読み直させると commit 済みトランザクションの EntityManager が古い管理中インスタンスを返す罠がある。
 */
public record PlatformUserCredentialsChanged(String email, long credentialVersion) {}
