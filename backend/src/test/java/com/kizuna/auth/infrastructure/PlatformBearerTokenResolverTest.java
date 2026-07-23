package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

/** {@link PlatformBearerTokenResolver} の単体テスト。 */
class PlatformBearerTokenResolverTest {

  private final PlatformBearerTokenResolver resolver = new PlatformBearerTokenResolver();

  private static MockHttpServletRequest requestWithBearer(String method, String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.addHeader("Authorization", "Bearer broken-token-value");
    return request;
  }

  @ParameterizedTest(name = "{0} は免除対象で Bearer を解決しない(null)こと")
  @ValueSource(
      strings = {
        "/platform/login",
        "/platform/cast-invitations/abc123",
        "/platform/cast-invitations/abc123/acceptance",
        "/platform/stores/lookup",
        "/store/config/public",
        "/store/casts/public",
        "/store/shifts/public"
      })
  @DisplayName("免除対象の公開端点では壊れた Bearer があっても null を返すこと")
  void resolvesNullForExemptPublicEndpoints(String uri) {
    MockHttpServletRequest request = requestWithBearer("GET", uri);

    assertThat(resolver.resolve(request)).isNull();
  }

  @Test
  @DisplayName("既存ユーザーの招待受諾(/acceptance/existing)は免除対象でなく Bearer を解決すること")
  void resolvesTokenForAcceptAsExistingUser() {
    MockHttpServletRequest request =
        requestWithBearer("POST", "/platform/cast-invitations/abc123/acceptance/existing");

    assertThat(resolver.resolve(request)).isEqualTo("broken-token-value");
  }

  @Test
  @DisplayName("保護端点では従来どおり Bearer を解決すること(免除が保護端点まで漏れていないこと)")
  void resolvesTokenForProtectedEndpoint() {
    MockHttpServletRequest request = requestWithBearer("GET", "/platform/me");

    assertThat(resolver.resolve(request)).isEqualTo("broken-token-value");
  }

  @Test
  @DisplayName("logout は免除対象でなく Bearer を解決すること(controller が自前でヘッダを読むため無害)")
  void resolvesTokenForLogout() {
    MockHttpServletRequest request = requestWithBearer("POST", "/platform/logout");

    assertThat(resolver.resolve(request)).isEqualTo("broken-token-value");
  }

  @Test
  @DisplayName("Authorization ヘッダが無ければ免除対象外の端点でも null を返すこと")
  void resolvesNullWhenNoAuthorizationHeader() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/platform/me");

    assertThat(resolver.resolve(request)).isNull();
  }
}
