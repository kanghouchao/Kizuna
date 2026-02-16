package com.kizuna.service.tenant;

import com.kizuna.model.dto.tenant.tenantconfig.TenantConfigResponse;
import com.kizuna.model.dto.tenant.tenantconfig.TenantConfigUpdateRequest;

public interface ConfigService {
  TenantConfigResponse get();

  TenantConfigResponse update(TenantConfigUpdateRequest request);
}
