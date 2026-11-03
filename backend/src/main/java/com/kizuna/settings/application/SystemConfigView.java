package com.kizuna.settings.application;

import com.kizuna.settings.domain.SystemConfig;
import java.time.OffsetDateTime;

/** 設定の現在の状態。秘匿設定の値は application の外へ出さず、configValue を null にして渡す。 */
public record SystemConfigView(
    Long id,
    String configKey,
    String configValue,
    String category,
    String description,
    String valueType,
    Boolean secret,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  static SystemConfigView masked(SystemConfig config) {
    return new SystemConfigView(
        config.getId(),
        config.getConfigKey(),
        Boolean.TRUE.equals(config.getSecret()) ? null : config.getConfigValue(),
        config.getCategory(),
        config.getDescription(),
        config.getValueType(),
        config.getSecret(),
        config.getCreatedAt(),
        config.getUpdatedAt());
  }
}
