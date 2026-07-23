package com.kizuna.shared.storescope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

/** {@link StoreBridgeGuard} の storeBridge claim 判定（fail-closed）の単体テスト。 */
class StoreBridgeGuardTest {

  private final StoreBridgeGuard guard = new StoreBridgeGuard();

  private Authentication authWith(Claims claims) {
    Authentication auth = mock(Authentication.class);
    when(auth.getDetails()).thenReturn(claims);
    return auth;
  }

  @Test
  @DisplayName("storeBridge=true claim を持つ認証は true")
  void trueWhenClaimTrue() {
    Claims claims = mock(Claims.class);
    when(claims.get("storeBridge", Boolean.class)).thenReturn(true);

    assertThat(guard.check(authWith(claims))).isTrue();
  }

  @Test
  @DisplayName("storeBridge=false claim を持つ認証は false")
  void falseWhenClaimFalse() {
    Claims claims = mock(Claims.class);
    when(claims.get("storeBridge", Boolean.class)).thenReturn(false);

    assertThat(guard.check(authWith(claims))).isFalse();
  }

  @Test
  @DisplayName("storeBridge claim 欠落（旧トークン）は false（fail-closed）")
  void falseWhenClaimMissing() {
    Claims claims = mock(Claims.class);
    when(claims.get("storeBridge", Boolean.class)).thenReturn(null);

    assertThat(guard.check(authWith(claims))).isFalse();
  }

  @Test
  @DisplayName("details が Claims でない場合は false")
  void falseWhenDetailsNotClaims() {
    Authentication auth = mock(Authentication.class);
    when(auth.getDetails()).thenReturn("not-claims");

    assertThat(guard.check(auth)).isFalse();
  }

  @Test
  @DisplayName("authentication が null の場合は false")
  void falseWhenAuthenticationNull() {
    assertThat(guard.check(null)).isFalse();
  }
}
