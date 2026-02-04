package com.kizuna.service.tenant;

import com.kizuna.model.dto.tenant.siteconfig.SiteConfigResponse;
import com.kizuna.model.dto.tenant.siteconfig.SiteConfigUpdateRequest;

public interface SiteConfigService {
  SiteConfigResponse get();

  SiteConfigResponse update(SiteConfigUpdateRequest request);
}
