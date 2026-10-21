package com.kizuna.shared.storescope;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class PermissionHolderGuardTest {

  private final PermissionHolderGuard guard = new PermissionHolderGuard();

  private static JwtAuthenticationToken jwtWith(String... authorities) {
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject("user").build();
    return new JwtAuthenticationToken(
        jwt, List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
  }

  @Test
  @DisplayName("PERM_ 権限を 1 つでも持てば true")
  void trueWithAnyPermission() {
    assertThat(guard.check(jwtWith("PERM_ORDER_MANAGE"))).isTrue();
    assertThat(guard.check(jwtWith("ROLE_CAST", "PERM_STORE_VIEW"))).isTrue();
  }

  @Test
  @DisplayName("本人種別標識だけ・権限無し・未認証は false（CAST / MEMBER は店舗文脈を名乗れない）")
  void falseWithoutPermission() {
    assertThat(guard.check(jwtWith("ROLE_CAST"))).isFalse();
    assertThat(guard.check(jwtWith())).isFalse();
    assertThat(guard.check(null)).isFalse();
    assertThat(
            guard.check(
                new AnonymousAuthenticationToken(
                    "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))))
        .isFalse();
  }
}
