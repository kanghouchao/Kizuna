package com.kizuna.auth.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * 発行時に搭載された資格情報の版（{@link CredentialVersionService#CLAIM}）を現在の版と相等比較する {@code
 * OAuth2TokenValidator}（ADR 0022）。claim の欠落・型不正も拒否する（移行期なしの fail-closed）。
 *
 * <p>失敗時の {@link OAuth2Error} には内部理由を含めない。Redis 断連の例外は貫通して 500 になる（fail-closed）。
 */
@Component
@RequiredArgsConstructor
public class CredentialVersionValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error INVALID_TOKEN = new OAuth2Error("invalid_token");

  private final CredentialVersionService credentialVersionService;

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    if (jwt.getClaims().get(CredentialVersionService.CLAIM) instanceof Number claimed
        && credentialVersionService.isCurrent(jwt.getSubject(), claimed.longValue())) {
      return OAuth2TokenValidatorResult.success();
    }
    return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
  }
}
