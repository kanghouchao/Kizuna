package com.kizuna.settings.application;

import java.util.List;
import java.util.Optional;

public interface SystemConfigService {
  List<SystemConfigView> getAllConfigs();

  List<SystemConfigView> getConfigsByCategory(String category);

  /** 値が null の更新は値を変えない（空文字は値を消す）。 */
  SystemConfigView updateConfig(String configKey, String configValue);

  /** 設定値を取得する（キャッシュされる。バックエンド内部からの設定参照はこのメソッドを使うこと） */
  Optional<String> getConfigValue(String configKey);

  /** SMTP 設定の型付きスナップショット（キー名の知識は settings 側に閉じる）。 */
  SmtpSettings smtpSettings();

  /** LINE ログインチャネル資格情報の型付きスナップショット（キー名の知識は settings 側に閉じる）。 */
  LineChannelSettings lineChannelSettings();

  /** ポイント制度の型付きスナップショット（キー名の知識は settings 側に閉じる）。 */
  PointSettings pointSettings();

  /** 会員ランクの昇格閾値の型付きスナップショット（キー名の知識は settings 側に閉じる）。 */
  MemberRankSettings memberRankSettings();

  /** 応募者の保持期間の型付きスナップショット（キー名の知識は settings 側に閉じる）。 */
  ApplicantRetentionSettings applicantRetentionSettings();

  /** ポイント期限前案内の型付きスナップショット（キー名の知識は settings 側に閉じる）。 */
  PointExpiryNoticeSettings pointExpiryNoticeSettings();
}
