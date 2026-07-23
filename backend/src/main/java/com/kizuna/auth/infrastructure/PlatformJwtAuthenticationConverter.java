package com.kizuna.auth.infrastructure;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

/**
 * {@code authorities} claim（値は {@code PERM_*} / {@code ROLE_CAST} / {@code ROLE_MEMBER} 等の文字列）を
 * prefix を付けずそのまま {@link GrantedAuthority} へ変換する。principal name は {@code sub} （email）で標準 {@link
 * JwtAuthenticationConverter} の既定どおり。
 *
 * <p>{@code authorities} claim が空（欠落・空配列）の token は認証を確立しない。標準の {@link
 * JwtGrantedAuthoritiesConverter} は空権限でも「認証済み」トークンを作ってしまい、{@code isAuthenticated()} のみを要求する端点が
 * 受理してしまう。安全側に倒し、ここで fail-closed に例外へ変換する（decoder の失敗と同じ経路で {@link
 * PlatformAuthenticationEntryPoint} が 401 を返す）。
 *
 * <p>判定は {@code authorities} claim 由来の権限のみを見る（{@link #authoritiesConverter} を直接呼ぶ）。標準 {@link
 * JwtAuthenticationConverter#convert} は claim が空でも認証因子表示用の {@code FACTOR_BEARER} 権限を必ず追加するため、 変換後の
 * {@code token.getAuthorities()} で空判定すると常に非空になり判定が機能しない。
 */
@Component
public class PlatformJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private final JwtGrantedAuthoritiesConverter authoritiesConverter =
      new JwtGrantedAuthoritiesConverter();
  private final JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();

  public PlatformJwtAuthenticationConverter() {
    authoritiesConverter.setAuthorityPrefix("");
    authoritiesConverter.setAuthoritiesClaimName("authorities");
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
