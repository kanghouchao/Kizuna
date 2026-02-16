package com.kizuna.service.central.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    // Arrange
    SystemConfig config = SystemConfig.builder().configKey("key1").configValue("value1").build();
    SystemConfigResponse response =
        SystemConfigResponse.builder().configKey("key1").configValue("value1").build();

    when(systemConfigRepository.findAll()).thenReturn(List.of(config));
    when(systemConfigMapper.toResponse(config)).thenReturn(response);

    // Act
    List<SystemConfigResponse> result = systemConfigService.getAllConfigs();

    // Assert
    assertEquals(1, result.size());
    assertEquals("key1", result.get(0).getConfigKey());
    verify(systemConfigRepository, times(1)).findAll();
  }

  @Test
  @DisplayName("カテゴリ指定で設定を取得できること")
  void getConfigsByCategory() {
    // Arrange
    String category = "SMTP";
    SystemConfig config = SystemConfig.builder().configKey("smtp_host").category(category).build();
    SystemConfigResponse response =
        SystemConfigResponse.builder().configKey("smtp_host").category(category).build();

    when(systemConfigRepository.findByCategory(category)).thenReturn(List.of(config));
    when(systemConfigMapper.toResponse(config)).thenReturn(response);

    // Act
    List<SystemConfigResponse> result = systemConfigService.getConfigsByCategory(category);

    // Assert
    assertEquals(1, result.size());
    assertEquals(category, result.get(0).getCategory());
    verify(systemConfigRepository, times(1)).findByCategory(category);
  }

  @Test
  @DisplayName("設定を更新できること")
  void updateConfig() {
    // Arrange
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

    // Act
    SystemConfigResponse result = systemConfigService.updateConfig(request);

    // Assert
    assertNotNull(result);
    assertEquals(newValue, result.getConfigValue());
    verify(systemConfigMapper, times(1)).updateEntityFromRequest(request, existingConfig);
    verify(systemConfigRepository, times(1)).save(existingConfig);
  }

  @Test
  @DisplayName("存在しない設定キーの更新で例外が発生すること")
  void updateConfig_NotFound() {
    // Arrange
    String key = "unknown_key";
    SystemConfigUpdateRequest request = SystemConfigUpdateRequest.builder().configKey(key).build();

    when(systemConfigRepository.findByConfigKey(key)).thenReturn(Optional.empty());

    // Act & Assert
    assertThrows(ServiceException.class, () -> systemConfigService.updateConfig(request));
  }
}
