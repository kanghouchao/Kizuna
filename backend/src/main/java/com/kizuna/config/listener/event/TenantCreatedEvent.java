package com.kizuna.config.listener.event;

import com.kizuna.model.entity.central.tenant.Tenant;
import org.springframework.context.ApplicationEvent;

/** Event published when a Tenant is created. */
public class TenantCreatedEvent extends ApplicationEvent {

  private final Tenant tenant;

  public TenantCreatedEvent(Tenant tenant) {
    super(tenant);
    this.tenant = tenant;
  }

  public Tenant getTenant() {
    return tenant;
  }
}
