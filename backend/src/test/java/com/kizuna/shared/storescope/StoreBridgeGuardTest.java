package com.kizuna.shared.storescope;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** {@link StoreBridgeGuard} の storeBridge claim 判定（fail-closed）の単体テスト。 */
class StoreBridgeGuardTest {

  private final StoreBridgeGuard guard = new StoreBridgeGuard();

  private Authentication authWith(Jwt jwt) {
    return new JwtAuthenticationToken(jwt);
  }

  private Jwt jwtWithStoreBridge(Boolean value) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token").header("alg", "HS256").subject("user@example.com");
    if (value != null) {
      builder.claim("storeBridge", value);
    }
    return builder.build();
  }

  @Test
  @DisplayName("storeBridge=true claim を持つ認証は true")
  void trueWhenClaimTrue() {
    assertThat(guard.check(authWith(jwtWithStoreBridge(true)))).isTrue();
  }

  @Test
  @DisplayName("storeBridge=false claim を持つ認証は false")
  void falseWhenClaimFalse() {
    assertThat(guard.check(authWith(jwtWithStoreBridge(false)))).isFalse();
  }

  @Test
  @DisplayName("storeBridge claim 欠落（旧トークン）は false（fail-closed）")
  void falseWhenClaimMissing() {
    assertThat(guard.check(authWith(jwtWithStoreBridge(null)))).isFalse();
  }

  @Test
  @DisplayName("非 JwtAuthenticationToken（フォームログイン等の他認証方式）は false（fail-closed の等価断言）")
  void falseWhenNotJwtAuthenticationToken() {
    Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated("user", "credentials", List.of());

    assertThat(guard.check(auth)).isFalse();
  }

  @Test
  @DisplayName("authentication が null の場合は false")
  void falseWhenAuthenticationNull() {
    assertThat(guard.check(null)).isFalse();
  }
}
