package com.kizuna.service.tenant;

import com.kizuna.mapper.tenant.TenantConfigMapper;
import com.kizuna.model.dto.tenant.tenantconfig.TenantConfigResponse;
import com.kizuna.model.dto.tenant.tenantconfig.TenantConfigUpdateRequest;
import com.kizuna.model.entity.tenant.TenantConfig;
import com.kizuna.repository.tenant.TenantConfigRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.tenancy.TenantContext;
import com.kizuna.shared.tenancy.TenantScoped;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

  private final TenantConfigRepository tenantConfigRepository;
  private final TenantContext tenantContext;
  private final TenantConfigMapper tenantConfigMapper;

  @Override
  @TenantScoped
  @Transactional(readOnly = true)
  public TenantConfigResponse get() {
    Long tenantId = tenantContext.getTenantId();
    TenantConfig config =
        tenantConfigRepository
            .findByTenantId(tenantId)
            .orElseThrow(() -> new ServiceException("テナント設定が見つかりません"));
    return tenantConfigMapper.toResponse(config);
  }

  @Override
  @TenantScoped
  @Transactional
  public TenantConfigResponse update(TenantConfigUpdateRequest request) {
    Long tenantId = tenantContext.getTenantId();
    TenantConfig config =
        tenantConfigRepository
            .findByTenantId(tenantId)
            .orElseThrow(() -> new ServiceException("テナント設定が見つかりません"));
    tenantConfigMapper.updateEntityFromRequest(request, config);
    return tenantConfigMapper.toResponse(tenantConfigRepository.saveAndFlush(config));
  }
}
