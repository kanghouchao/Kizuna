package com.kizuna.service.central.config;

import com.kizuna.model.dto.central.config.SystemConfigResponse;
import com.kizuna.model.dto.central.config.SystemConfigUpdateRequest;
import java.util.List;

public interface SystemConfigService {
  List<SystemConfigResponse> getAllConfigs();

  List<SystemConfigResponse> getConfigsByCategory(String category);

  SystemConfigResponse updateConfig(SystemConfigUpdateRequest request);
}
