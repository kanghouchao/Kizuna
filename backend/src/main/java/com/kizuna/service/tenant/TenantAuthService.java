package com.kizuna.service.tenant;

import com.kizuna.model.dto.auth.Token;
import com.kizuna.model.dto.tenant.TenantRegisterRequest;
import com.kizuna.model.entity.central.tenant.Tenant;

/** Authentication service interface. */
public interface TenantAuthService {

  /**
   * Login a user and generate a JWT token.
   *
   * @param username the username
   * @param password the password
   * @return the JWT token
   */
  Token login(String username, String password);

  /**
   * Save a new tenant user request.
   *
   * @param tenantId the tenant ID
   * @param tenant the tenant user request
   */
  Tenant register(Long tenantId, TenantRegisterRequest tenant);
}
