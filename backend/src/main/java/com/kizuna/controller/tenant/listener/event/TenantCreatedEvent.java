package com.kizuna.controller.tenant.listener.event;

import com.kizuna.model.entity.central.tenant.Tenant;
import org.springframework.context.ApplicationEvent;

public class TenantCreatedEvent extends ApplicationEvent {

  private final String token;

  private final Tenant tenant;

  public TenantCreatedEvent(Tenant tenant, String token) {
    super(tenant);
    this.tenant = tenant;
    this.token = token;
  }

  public String getToken() {
    return token;
  }

  public Tenant getTenant() {
    return tenant;
  }
}
