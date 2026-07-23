package com.kizuna.shared.storescope;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** {@link StoreScope} の claim 解決と授権判定の単体テスト（集合作用域）。 */
class StoreScopeTest {

  private Authentication authWith(Jwt jwt) {
    return new JwtAuthenticationToken(jwt);
  }

  private Jwt jwtWithClaim(String name, Object value) {
    return jwtBuilder().claim(name, value).build();
  }

  private Jwt.Builder jwtBuilder() {
    return Jwt.withTokenValue("token").header("alg", "HS256").subject("user@example.com");
  }

  @Test
  @DisplayName("ALL_STORES を解決する（allStores=true / storeIds 空）")
  void resolvesAllStores() {
    StoreScope scope =
        StoreScope.fromAuthentication(authWith(jwtWithClaim("storeScopeType", "ALL_STORES")));

    assertThat(scope).isNotNull();
    assertThat(scope.allStores()).isTrue();
    assertThat(scope.storeIds()).isEmpty();
  }

  @Test
  @DisplayName("SPECIFIC_STORES を解決する（Nimbus decode 相当の Long リストから解決）")
  void resolvesSpecificStores() {
    Jwt jwt =
        jwtBuilder()
            .claim("storeScopeType", "SPECIFIC_STORES")
            .claim("storeIds", List.of(1L, 2L))
            .build();

    StoreScope scope = StoreScope.fromAuthentication(authWith(jwt));

    assertThat(scope).isNotNull();
    assertThat(scope.allStores()).isFalse();
    assertThat(scope.storeIds()).containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  @DisplayName("authorizes: 含む店舗は true、含まない店舗と null は false")
  void authorizesRespectsSpecificSet() {
    StoreScope scope = new StoreScope(false, Set.of(1L));

    assertThat(scope.authorizes(1L)).isTrue();
    assertThat(scope.authorizes(2L)).isFalse();
    assertThat(scope.authorizes(null)).isFalse();
  }

  @Test
  @DisplayName("authorizes: ALL_STORES は任意店舗で true、ただし null は false")
  void allStoresAuthorizesAnyNonNullStore() {
    StoreScope scope = new StoreScope(true, Set.of());

    assertThat(scope.authorizes(1L)).isTrue();
    assertThat(scope.authorizes(999L)).isTrue();
    assertThat(scope.authorizes(null)).isFalse();
  }

  @Test
  @DisplayName("scopeType claim 欠落は null")
  void returnsNullWhenScopeTypeMissing() {
    assertThat(StoreScope.fromAuthentication(authWith(jwtBuilder().build()))).isNull();
  }

  @Test
  @DisplayName("未知の scopeType は null")
  void returnsNullForUnknownScopeType() {
    assertThat(
            StoreScope.fromAuthentication(
                authWith(jwtWithClaim("storeScopeType", "SOMETHING_ELSE"))))
        .isNull();
  }

  @Test
  @DisplayName("非 JwtAuthenticationToken（フォームログイン等の他認証方式）は null（fail-closed の等価断言）")
  void returnsNullWhenNotJwtAuthenticationToken() {
    Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated("user", "credentials", List.of());

    assertThat(StoreScope.fromAuthentication(auth)).isNull();
  }

  @Test
  @DisplayName("authentication が null の場合は null")
  void returnsNullWhenAuthenticationNull() {
    assertThat(StoreScope.fromAuthentication(null)).isNull();
  }

  @Test
  @DisplayName("SPECIFIC_STORES で storeIds が空は null")
  void returnsNullWhenSpecificButStoreIdsEmpty() {
    Jwt jwt =
        jwtBuilder()
            .claim("storeScopeType", "SPECIFIC_STORES")
            .claim("storeIds", List.of())
            .build();

    assertThat(StoreScope.fromAuthentication(authWith(jwt))).isNull();
  }

  @Test
  @DisplayName("SPECIFIC_STORES で数値でない要素が混入すると null")
  void returnsNullWhenSpecificContainsNonNumericElement() {
    Jwt jwt =
        jwtBuilder()
            .claim("storeScopeType", "SPECIFIC_STORES")
            .claim("storeIds", List.of(1L, "x"))
            .build();

    assertThat(StoreScope.fromAuthentication(authWith(jwt))).isNull();
  }
}
