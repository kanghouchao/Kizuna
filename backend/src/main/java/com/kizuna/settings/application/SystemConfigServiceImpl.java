package com.kizuna.settings.application;

import com.kizuna.settings.api.dto.SystemConfigMapper;
import com.kizuna.settings.api.dto.SystemConfigResponse;
import com.kizuna.settings.api.dto.SystemConfigUpdateRequest;
import com.kizuna.settings.domain.SystemConfig;
import com.kizuna.settings.domain.SystemConfigRepository;
import com.kizuna.shared.exception.ServiceException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

  private final SystemConfigRepository systemConfigRepository;
  private final SystemConfigMapper systemConfigMapper;

  @Override
  @Transactional(readOnly = true)
  public List<SystemConfigResponse> getAllConfigs() {
    return systemConfigRepository.findAll().stream()
        .map(this::toMaskedResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<SystemConfigResponse> getConfigsByCategory(String category) {
    return systemConfigRepository.findByCategory(category).stream()
        .map(this::toMaskedResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  // smtpSettings のような集約キャッシュも確実に無効化するため全消去（設定更新は低頻度の管理操作）
  @CacheEvict(value = "systemConfigValues", allEntries = true)
  public SystemConfigResponse updateConfig(SystemConfigUpdateRequest request) {
    SystemConfig config =
        systemConfigRepository
            .findByConfigKey(request.getConfigKey())
            .orElseThrow(() -> new ServiceException("設定キーが見つかりません: " + request.getConfigKey()));

    validateValue(config, request.getConfigValue());
    systemConfigMapper.updateEntityFromRequest(request, config);
    SystemConfig saved = systemConfigRepository.save(config);
    return toMaskedResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(value = "systemConfigValues", key = "#configKey")
  public Optional<String> getConfigValue(String configKey) {
    return systemConfigRepository.findByConfigKey(configKey).map(SystemConfig::getConfigValue);
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(value = "systemConfigValues", key = "'smtp:settings'")
  public SmtpSettings smtpSettings() {
    int port = 25;
    String rawPort = rawValue("smtp_port");
    if (!rawPort.isBlank()) {
      try {
        port = Integer.parseInt(rawPort.trim());
      } catch (NumberFormatException e) {
        // 不正値は既定ポートで送信を試みる（更新時に NUMBER 検証済みのため通常は到達しない）
      }
    }
    return new SmtpSettings(
        rawValue("smtp_host"),
        port,
        rawValue("smtp_username"),
        rawValue("smtp_password"),
        rawValue("smtp_from"));
  }

  /** キャッシュプロキシを経由しない内部読み取り（smtpSettings 自体がキャッシュされる）。 */
  private String rawValue(String configKey) {
    return systemConfigRepository
        .findByConfigKey(configKey)
        .map(SystemConfig::getConfigValue)
        .orElse("");
  }

  /** value_type に応じて設定値を検証する */
  private void validateValue(SystemConfig config, String value) {
    if (value == null || value.isBlank()) {
      return; // 未設定（空）は許容する
    }
    if ("BOOLEAN".equals(config.getValueType())
        && !"true".equals(value)
        && !"false".equals(value)) {
      throw new ServiceException("真偽値（true / false）を指定してください: " + config.getConfigKey());
    }
    if ("NUMBER".equals(config.getValueType())) {
      try {
        Long.parseLong(value.trim());
      } catch (NumberFormatException e) {
        throw new ServiceException("数値を指定してください: " + config.getConfigKey());
      }
    }
  }

  /** 秘匿設定の値をマスクしてレスポンスへ変換する */
  private SystemConfigResponse toMaskedResponse(SystemConfig config) {
    SystemConfigResponse response = systemConfigMapper.toResponse(config);
    if (Boolean.TRUE.equals(config.getSecret())) {
      response.setConfigValue(null);
    }
    return response;
  }
}
