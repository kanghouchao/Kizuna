package com.kizuna.auth.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** ログアウト済みのセッション（{@link TokenBlacklistService#blacklistSession}）のトークンを拒否する。sid の欠落も拒否する。 */
@Component
@RequiredArgsConstructor
public class SessionBlacklistValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error INVALID_TOKEN = new OAuth2Error("invalid_token");

  private final TokenBlacklistService tokenBlacklistService;

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    String sessionId = jwt.getClaimAsString(TokenType.SESSION_ID_CLAIM);
    if (sessionId == null || tokenBlacklistService.isSessionBlacklisted(sessionId)) {
      return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }
    return OAuth2TokenValidatorResult.success();
  }
}
