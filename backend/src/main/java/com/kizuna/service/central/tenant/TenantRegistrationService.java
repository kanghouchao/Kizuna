package com.kizuna.service.central.tenant;

/** Interface for tenant registration token management. */
public interface TenantRegistrationService {
  String createToken(Long tenantId);

  Long validate(String token);

  void consume(String token);
}
