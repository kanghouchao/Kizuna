package com.kizuna.auth.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** 種別 claim が期待する種別と一致するトークンだけを通す。欠落も拒否する。 */
@RequiredArgsConstructor
class TokenTypeValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error INVALID_TOKEN = new OAuth2Error("invalid_token");

  private final TokenType expected;

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    return expected.claimValue().equals(jwt.getClaimAsString(TokenType.CLAIM))
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
  }
}
