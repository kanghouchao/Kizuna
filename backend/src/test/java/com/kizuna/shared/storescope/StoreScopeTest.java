package com.kizuna.shared.storescope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

/** {@link StoreScope} の claim 解決と授権判定の単体テスト（集合作用域）。 */
class StoreScopeTest {

  private Authentication authWith(Claims claims) {
    Authentication auth = mock(Authentication.class);
    when(auth.getDetails()).thenReturn(claims);
    return auth;
  }

  @Test
  @DisplayName("ALL_STORES を解決する（allStores=true / storeIds 空）")
  void resolvesAllStores() {
    Claims claims = mock(Claims.class);
    when(claims.get("storeScopeType", String.class)).thenReturn("ALL_STORES");

    StoreScope scope = StoreScope.fromAuthentication(authWith(claims));

    assertThat(scope).isNotNull();
    assertThat(scope.allStores()).isTrue();
    assertThat(scope.storeIds()).isEmpty();
  }

  @Test
  @DisplayName("SPECIFIC_STORES を解決する（Integer 混在 List を Long へ強制変換）")
  void resolvesSpecificStoresCoercingIntegersToLong() {
    Claims claims = mock(Claims.class);
    when(claims.get("storeScopeType", String.class)).thenReturn("SPECIFIC_STORES");
    when(claims.get("storeIds")).thenReturn(List.of(1, 2)); // jjwt は数値配列を Integer で返しうる

    StoreScope scope = StoreScope.fromAuthentication(authWith(claims));

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
    Claims claims = mock(Claims.class);
    when(claims.get("storeScopeType", String.class)).thenReturn(null);

    assertThat(StoreScope.fromAuthentication(authWith(claims))).isNull();
  }

  @Test
  @DisplayName("未知の scopeType は null")
  void returnsNullForUnknownScopeType() {
    Claims claims = mock(Claims.class);
    when(claims.get("storeScopeType", String.class)).thenReturn("SOMETHING_ELSE");

    assertThat(StoreScope.fromAuthentication(authWith(claims))).isNull();
  }

  @Test
  @DisplayName("details が Claims でない場合は null")
  void returnsNullWhenDetailsNotClaims() {
    Authentication auth = mock(Authentication.class);
    when(auth.getDetails()).thenReturn("not-claims");

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
    Claims claims = mock(Claims.class);
    when(claims.get("storeScopeType", String.class)).thenReturn("SPECIFIC_STORES");
    when(claims.get("storeIds")).thenReturn(List.of());

    assertThat(StoreScope.fromAuthentication(authWith(claims))).isNull();
  }

  @Test
  @DisplayName("SPECIFIC_STORES で数値でない要素が混入すると null")
  void returnsNullWhenSpecificContainsNonNumericElement() {
    Claims claims = mock(Claims.class);
    when(claims.get("storeScopeType", String.class)).thenReturn("SPECIFIC_STORES");
    when(claims.get("storeIds")).thenReturn(List.of(1, "x"));

    assertThat(StoreScope.fromAuthentication(authWith(claims))).isNull();
  }
}
