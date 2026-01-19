package com.kizuna.config.interceptor;

import org.springframework.stereotype.Component;

@Component
public class TenantContext {
  private final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

  public boolean isTenant() {
    Long tenantId = CURRENT_TENANT.get();
    return tenantId != null;
  }

  public void setTenantId(Long tenantId) {
    CURRENT_TENANT.set(tenantId);
  }

  public Long getTenantId() {
    return CURRENT_TENANT.get();
  }

  public void clear() {
    CURRENT_TENANT.remove();
  }
}
