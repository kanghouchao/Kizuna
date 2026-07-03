package com.kizuna.auth.application;

import com.kizuna.auth.api.dto.Token;

/** Authentication service interface. */
public interface CentralAuthService {

  /**
   * Logs in a user for Central Authentication.
   *
   * @param username the username
   * @param password the password
   * @return the JWT token
   */
  Token login(String username, String password);
}
