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
        "/platform/cast-invitations/view",
        "/platform/cast-invitations/acceptance",
        "/platform/members",
        "/platform/line/config",
        "/platform/line/login",
        "/platform/line/register",
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
  @DisplayName("ゲスト予約申請の POST は免除対象で、壊れた Bearer があっても null を返すこと")
  void resolvesNullForGuestOrderApplicationPost() {
    // 公開店面を見ている来訪者は会員・キャストの陳腐な token cookie を持ちうる。免除しないと申請が 401 になる
    MockHttpServletRequest request = requestWithBearer("POST", "/store/order-applications/public");

    assertThat(resolver.resolve(request)).isNull();
  }

  @Test
  @DisplayName("ゲスト予約申請の免除は POST 限定で、同一パスの他メソッドには及ばないこと")
  void doesNotExemptOtherMethodsOnTheGuestOrderApplicationPath() {
    // パスだけで免除すると、同一パスに認証必須の handler を足した日にその Bearer まで捨てられ、
    // その端点は誰にも通せない恒久的な 401/403 になる
    MockHttpServletRequest request = requestWithBearer("GET", "/store/order-applications/public");

    assertThat(resolver.resolve(request)).isEqualTo("broken-token-value");
  }

  @Test
  @DisplayName("既存ユーザーの招待受諾(/acceptance/existing)は免除対象でなく Bearer を解決すること")
  void resolvesTokenForAcceptAsExistingUser() {
    MockHttpServletRequest request =
        requestWithBearer("POST", "/platform/cast-invitations/acceptance/existing");

    assertThat(resolver.resolve(request)).isEqualTo("broken-token-value");
  }

  @Test
  @DisplayName("招待の免除は view/acceptance の 2 端点限定で、トークンを載せたパスには及ばないこと")
  void doesNotExemptArbitraryCastInvitationPath() {
    // 免除はかつて /platform/cast-invitations/* だった。トークンをパスから本文へ移した今、
    // 招待トークンらしき 1 セグメントが免除に当たってはならない（当たればパス運搬が復活しても気付けない）。
    MockHttpServletRequest request = requestWithBearer("GET", "/platform/cast-invitations/abc123");

    assertThat(resolver.resolve(request)).isEqualTo("broken-token-value");
  }

  @Test
  @DisplayName("保護端点では従来どおり Bearer を解決すること(免除が保護端点まで漏れていないこと)")
  void resolvesTokenForProtectedEndpoint() {
    MockHttpServletRequest request = requestWithBearer("GET", "/platform/me");

    assertThat(resolver.resolve(request)).isEqualTo("broken-token-value");
  }

  @Test
  @DisplayName("LINE 連携(/platform/me/line)は免除対象でなく Bearer を解決すること(免除すると常に匿名となり連携先を特定できない)")
  void resolvesTokenForLineLink() {
    MockHttpServletRequest request = requestWithBearer("POST", "/platform/me/line");

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
