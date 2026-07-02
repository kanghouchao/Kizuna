package com.kizuna.service.central.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.exception.ServiceException;
import com.kizuna.mapper.central.SystemConfigMapper;
import com.kizuna.model.dto.central.config.SystemConfigResponse;
import com.kizuna.model.dto.central.config.SystemConfigUpdateRequest;
import com.kizuna.model.entity.central.SystemConfig;
import com.kizuna.repository.central.SystemConfigRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceImplTest {

  @Mock private SystemConfigRepository systemConfigRepository;
  @Mock private SystemConfigMapper systemConfigMapper;

  @InjectMocks private SystemConfigServiceImpl systemConfigService;

  @Test
  @DisplayName("全設定を取得できること")
  void getAllConfigs() {
    // 準備
    SystemConfig config = SystemConfig.builder().configKey("key1").configValue("value1").build();
    SystemConfigResponse response =
        SystemConfigResponse.builder().configKey("key1").configValue("value1").build();

    when(systemConfigRepository.findAll()).thenReturn(List.of(config));
    when(systemConfigMapper.toResponse(config)).thenReturn(response);

    // 実行
    List<SystemConfigResponse> result = systemConfigService.getAllConfigs();

    // 検証
    assertEquals(1, result.size());
    assertEquals("key1", result.get(0).getConfigKey());
    verify(systemConfigRepository, times(1)).findAll();
  }

  @Test
  @DisplayName("カテゴリ指定で設定を取得できること")
  void getConfigsByCategory() {
    // 準備
    String category = "SMTP";
    SystemConfig config = SystemConfig.builder().configKey("smtp_host").category(category).build();
    SystemConfigResponse response =
        SystemConfigResponse.builder().configKey("smtp_host").category(category).build();

    when(systemConfigRepository.findByCategory(category)).thenReturn(List.of(config));
    when(systemConfigMapper.toResponse(config)).thenReturn(response);

    // 実行
    List<SystemConfigResponse> result = systemConfigService.getConfigsByCategory(category);

    // 検証
    assertEquals(1, result.size());
    assertEquals(category, result.get(0).getCategory());
    verify(systemConfigRepository, times(1)).findByCategory(category);
  }

  @Test
  @DisplayName("設定を更新できること")
  void updateConfig() {
    // 準備
    String key = "maintenance_mode";
    String newValue = "true";
    SystemConfigUpdateRequest request =
        SystemConfigUpdateRequest.builder().configKey(key).configValue(newValue).build();

    SystemConfig existingConfig =
        SystemConfig.builder().configKey(key).configValue("false").build();
    existingConfig.setId(1L);
    SystemConfig updatedConfig =
        SystemConfig.builder().configKey(key).configValue(newValue).build();
    updatedConfig.setId(1L);
    SystemConfigResponse response =
        SystemConfigResponse.builder().configKey(key).configValue(newValue).build();

    when(systemConfigRepository.findByConfigKey(key)).thenReturn(Optional.of(existingConfig));
    when(systemConfigRepository.save(existingConfig)).thenReturn(updatedConfig);
    when(systemConfigMapper.toResponse(updatedConfig)).thenReturn(response);

    // 実行
    SystemConfigResponse result = systemConfigService.updateConfig(request);

    // 検証
    assertNotNull(result);
    assertEquals(newValue, result.getConfigValue());
    verify(systemConfigMapper, times(1)).updateEntityFromRequest(request, existingConfig);
    verify(systemConfigRepository, times(1)).save(existingConfig);
  }

  @Test
  @DisplayName("真偽値型の設定に不正な値を指定すると例外が発生すること")
  void updateConfig_invalidBoolean() {
    SystemConfigUpdateRequest request =
        SystemConfigUpdateRequest.builder()
            .configKey("maintenance_mode")
            .configValue("yes")
            .build();
    SystemConfig config =
        SystemConfig.builder().configKey("maintenance_mode").valueType("BOOLEAN").build();
    when(systemConfigRepository.findByConfigKey("maintenance_mode"))
        .thenReturn(Optional.of(config));

    assertThrows(ServiceException.class, () -> systemConfigService.updateConfig(request));
  }

  @Test
  @DisplayName("数値型の設定に不正な値を指定すると例外が発生すること")
  void updateConfig_invalidNumber() {
    SystemConfigUpdateRequest request =
        SystemConfigUpdateRequest.builder().configKey("smtp_port").configValue("abc").build();
    SystemConfig config = SystemConfig.builder().configKey("smtp_port").valueType("NUMBER").build();
    when(systemConfigRepository.findByConfigKey("smtp_port")).thenReturn(Optional.of(config));

    assertThrows(ServiceException.class, () -> systemConfigService.updateConfig(request));
  }

  @Test
  @DisplayName("秘匿設定の値はレスポンスでマスクされること")
  void getAllConfigs_masksSecret() {
    SystemConfig config =
        SystemConfig.builder()
            .configKey("smtp_password")
            .configValue("secret-value")
            .secret(true)
            .build();
    SystemConfigResponse response =
        SystemConfigResponse.builder()
            .configKey("smtp_password")
            .configValue("secret-value")
            .build();
    when(systemConfigRepository.findAll()).thenReturn(List.of(config));
    when(systemConfigMapper.toResponse(config)).thenReturn(response);

    List<SystemConfigResponse> result = systemConfigService.getAllConfigs();

    assertEquals(1, result.size());
    assertNull(result.get(0).getConfigValue());
  }

  @Test
  @DisplayName("getConfigValue で設定値を取得できること")
  void getConfigValue() {
    SystemConfig config =
        SystemConfig.builder().configKey("maintenance_mode").configValue("true").build();
    when(systemConfigRepository.findByConfigKey("maintenance_mode"))
        .thenReturn(Optional.of(config));

    assertEquals(Optional.of("true"), systemConfigService.getConfigValue("maintenance_mode"));
  }

  @Test
  @DisplayName("存在しないキーの getConfigValue は空を返すこと")
  void getConfigValue_missing() {
    when(systemConfigRepository.findByConfigKey("unknown")).thenReturn(Optional.empty());

    assertEquals(Optional.empty(), systemConfigService.getConfigValue("unknown"));
  }

  @Test
  @DisplayName("存在しない設定キーの更新で例外が発生すること")
  void updateConfig_NotFound() {
    // 準備
    String key = "unknown_key";
    SystemConfigUpdateRequest request = SystemConfigUpdateRequest.builder().configKey(key).build();

    when(systemConfigRepository.findByConfigKey(key)).thenReturn(Optional.empty());

    // 実行・検証
    assertThrows(ServiceException.class, () -> systemConfigService.updateConfig(request));
  }
}
