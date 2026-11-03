package com.kizuna.settings.api.dto;

import com.kizuna.settings.application.SystemConfigView;
import java.time.OffsetDateTime;

public record SystemConfigResponse(
    Long id,
    String configKey,
    String configValue,
    String category,
    String description,
    String valueType,
    Boolean secret,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static SystemConfigResponse of(SystemConfigView view) {
    return new SystemConfigResponse(
        view.id(),
        view.configKey(),
        view.configValue(),
        view.category(),
        view.description(),
        view.valueType(),
        view.secret(),
        view.createdAt(),
        view.updatedAt());
  }
}
