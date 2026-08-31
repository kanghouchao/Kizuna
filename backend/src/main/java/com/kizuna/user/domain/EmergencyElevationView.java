package com.kizuna.user.domain;

import java.time.OffsetDateTime;

/**
 * 緊急昇格の履歴 1 行分の読み側 projection。記録本体は id 参照しか持たないため、監査の読みやすさに要る 発動者・店舗・撤回者の表示名は join で導出する。
 *
 * <p>昇格トークンの生値は発動応答にしか現れない（取り直す口を作らない）ので、この projection にも存在しない。
 */
public interface EmergencyElevationView {

  Long getId();

  String getActivatorName();

  Long getTargetStoreId();

  String getStoreName();

  String getReason();

  OffsetDateTime getActivatedAt();

  OffsetDateTime getExpiresAt();

  EmergencyElevationStatus getStatus();

  /** 撤回者の表示名。撤回されていない記録では欠落する。 */
  String getRevokerName();

  OffsetDateTime getRevokedAt();
}
