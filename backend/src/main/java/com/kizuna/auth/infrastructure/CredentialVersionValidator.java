package com.kizuna.auth.infrastructure;

import com.kizuna.shared.web.JwtClaims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * トークンが搭載した資格情報の版（{@link TokenClaims#CREDENTIAL_VERSION}）を現在の版と照合する（ADR 0022）。
 *
 * <p>版か利用者 ID（{@link JwtClaims#USER_ID}）の claim が欠落・型不正なら拒否し、Redis 断連は 500
 * に貫通させる（fail-closed）。失敗の内部理由は応答に載せない。
 */
@Component
@RequiredArgsConstructor
public class CredentialVersionValidator implements OAuth2TokenValidator<Jwt> {

  /** 失敗コード。リフレッシュの拒否原因の区別に使い、Bearer の 401 応答には載らない。 */
  static final String CREDENTIAL_CHANGED = "credential_changed";

  private static final OAuth2Error STALE = new OAuth2Error(CREDENTIAL_CHANGED);
  private static final OAuth2Error INVALID_TOKEN = new OAuth2Error("invalid_token");

  private final CredentialVersionService credentialVersionService;

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    if (!(jwt.getClaims().get(TokenClaims.CREDENTIAL_VERSION) instanceof Number claimed
        && jwt.getClaims().get(JwtClaims.USER_ID) instanceof Number userId)) {
      return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }
    return credentialVersionService.isCurrent(userId.longValue(), claimed.longValue())
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(STALE);
  }
}
