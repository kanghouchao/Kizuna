package com.kizuna.service.central.config;

import com.kizuna.exception.ServiceException;
import com.kizuna.mapper.central.SystemConfigMapper;
import com.kizuna.model.dto.central.config.SystemConfigResponse;
import com.kizuna.model.dto.central.config.SystemConfigUpdateRequest;
import com.kizuna.model.entity.central.SystemConfig;
import com.kizuna.repository.central.SystemConfigRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
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
        .map(systemConfigMapper::toResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<SystemConfigResponse> getConfigsByCategory(String category) {
    return systemConfigRepository.findByCategory(category).stream()
        .map(systemConfigMapper::toResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public SystemConfigResponse updateConfig(SystemConfigUpdateRequest request) {
    SystemConfig config =
        systemConfigRepository
            .findByConfigKey(request.getConfigKey())
            .orElseThrow(() -> new ServiceException("設定キーが見つかりません: " + request.getConfigKey()));

    systemConfigMapper.updateEntityFromRequest(request, config);
    SystemConfig saved = systemConfigRepository.save(config);
    return systemConfigMapper.toResponse(saved);
  }
}
