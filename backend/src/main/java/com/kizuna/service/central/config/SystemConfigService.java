package com.kizuna.service.central.config;

import com.kizuna.model.dto.central.config.SystemConfigResponse;
import com.kizuna.model.dto.central.config.SystemConfigUpdateRequest;
import java.util.List;
import java.util.Optional;

public interface SystemConfigService {
  List<SystemConfigResponse> getAllConfigs();

  List<SystemConfigResponse> getConfigsByCategory(String category);

  SystemConfigResponse updateConfig(SystemConfigUpdateRequest request);

  /** 設定値を取得する（キャッシュされる。バックエンド内部からの設定参照はこのメソッドを使うこと） */
  Optional<String> getConfigValue(String configKey);
}
