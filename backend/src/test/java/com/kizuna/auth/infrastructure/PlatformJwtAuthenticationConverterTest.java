package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/** {@link PlatformJwtAuthenticationConverter} の単体テスト。 */
class PlatformJwtAuthenticationConverterTest {

  private final PlatformJwtAuthenticationConverter converter =
      new PlatformJwtAuthenticationConverter();

  private Jwt.Builder jwtBuilder() {
    return Jwt.withTokenValue("token").header("alg", "HS256").subject("user@kizuna.test");
  }

  @Test
  @DisplayName("authorities claim（PERM_* 等）を prefix 無しでそのまま GrantedAuthority へ変換し、principal は sub")
  void convertsAuthoritiesWithoutPrefix() {
    Jwt jwt = jwtBuilder().claim("authorities", List.of("PERM_ORDER_MANAGE", "ROLE_CAST")).build();

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token.getName()).isEqualTo("user@kizuna.test");
    // 標準 JwtAuthenticationConverter が認証因子表示用の FACTOR_BEARER 権限を必ず追加するため、
    // claim 由来の権限を包含することのみ検証する（完全一致は課さない）。
    assertThat(token.getAuthorities().stream().map(GrantedAuthority::getAuthority))
        .contains("PERM_ORDER_MANAGE", "ROLE_CAST");
  }

  @Test
  @DisplayName("authorities claim が欠落した token は認証を確立しない（InvalidBearerTokenException）")
  void rejectsTokenWithMissingAuthoritiesClaim() {
    Jwt jwt = jwtBuilder().build();

    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(InvalidBearerTokenException.class);
  }

  @Test
  @DisplayName("authorities claim が空配列の token は認証を確立しない（InvalidBearerTokenException）")
  void rejectsTokenWithEmptyAuthoritiesClaim() {
    Jwt jwt = jwtBuilder().claim("authorities", List.of()).build();

    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(InvalidBearerTokenException.class);
  }
}
