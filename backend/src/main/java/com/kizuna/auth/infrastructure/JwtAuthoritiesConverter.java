package com.kizuna.auth.infrastructure;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

/**
 * {@code authorities} claim の値を prefix なしで権限にし、principal は {@code sub}（email）とする。
 *
 * <p>claim が空のトークンは認証を確立せず 401 にする。標準の変換は空でも認証済みにし {@code FACTOR_BEARER} も足すので、claim 由来の権限だけで判定する。
 */
@Component
public class JwtAuthoritiesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  private final JwtGrantedAuthoritiesConverter authoritiesConverter =
      new JwtGrantedAuthoritiesConverter();
  private final JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();

  public JwtAuthoritiesConverter() {
    authoritiesConverter.setAuthorityPrefix("");
    authoritiesConverter.setAuthoritiesClaimName(TokenClaims.AUTHORITIES);
    delegate.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    if (authoritiesConverter.convert(jwt).isEmpty()) {
      throw new InvalidBearerTokenException("authorities claim が空のトークンは認証を確立しない");
    }
    return delegate.convert(jwt);
  }
}
