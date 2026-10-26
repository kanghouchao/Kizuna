package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

/** {@link ExemptingBearerTokenResolver} の単体テスト。 */
class ExemptingBearerTokenResolverTest {

  private final ExemptingBearerTokenResolver resolver = new ExemptingBearerTokenResolver();

  private static MockHttpServletRequest requestWithBearer(String method, String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.addHeader("Authorization", "Bearer broken-token-value");
    return request;
  }

  @ParameterizedTest(name = "{0} は免除対象で Bearer を解決しない(null)こと")
  @ValueSource(
      strings = {
        "/auth/login",
        "/auth/line/config",
        "/auth/line/login",
        "/auth/line/register",
        "/stores/lookup",
        "/config/public",
        "/casts/public",
        "/shifts/public"
      })
  @DisplayName("免除対象の公開端点では壊れた Bearer があっても null を返すこと")
  void resolvesNullForExemptPublicEndpoints(String uri) {
    MockHttpServletRequest request = requestWithBearer("GET", uri);

    assertThat(resolver.resolve(request)).isNull();
  }

  @ParameterizedTest(name = "POST {0} は免除対象で Bearer を解決しない(null)こと")
  @ValueSource(strings = {"/cast-invitations/view", "/cast-invitations/acceptance", "/members"})
  @DisplayName("招待の照会・新規受諾と会員の自助登録の POST は、壊れた Bearer があっても null を返すこと")
  void resolvesNullForAnonymousPosts(String uri) {
    MockHttpServletRequest request = requestWithBearer("POST", uri);

    assertThat(resolver.resolve(request)).isNull();
  }

  @ParameterizedTest(name = "GET {0} は免除対象でなく Bearer を解決すること")
  @ValueSource(strings = {"/cast-invitations/view", "/cast-invitations/acceptance", "/members"})
  @DisplayName("匿名 POST の免除は同一パスの他メソッドに及ばないこと")
  void doesNotExemptOtherMethodsOnAnonymousPostPaths(String uri) {
    // 接頭辞の無い /members などは認証付きの一覧を足しやすく、パスだけで免除するとその Bearer まで捨てる
    MockHttpServletRequest request = requestWithBearer("GET", uri);

    assertThat(resolver.resolve(request)).isEqualTo("broken-token-value");
  }

  @Test
  @DisplayName("ゲスト予約申請の POST は免除対象で、壊れた Bearer があっても null を返すこと")
  void resolvesNullForGuestOrderApplicationPost() {
    // 公開サイトを見ている来訪者は会員・キャストの陳腐な token cookie を持ちうる。免除しないと申請が 401 になる
    MockHttpServletRequest request = requestWithBearer("POST", "/order-applications/public");

    assertThat(resolver.resolve(request)).isNull();
  }

  @Test
  @DisplayName("ゲスト予約申請の免除は POST 限定で、同一パスの他メソッドには及ばないこと")
  void doesNotExemptOtherMethodsOnTheGuestOrderApplicationPath() {
    // パスだけで免除すると、同一パスに認証必須の handler を足した日にその Bearer まで捨てられ、
    // その端点は誰にも通せない恒久的な 401/403 になる
    MockHttpServletRequest request = requestWithBearer("GET", "/order-applications/public");

    assertThat(resolver.resolve(request)).isEqualTo("broken-token-value");
  }

  @Test
  @DisplayName("初期設定の登録 POST は免除対象で、壊れた Bearer があっても null を返すこと")
  void resolvesNullForInitialSetupPost() {
    MockHttpServletRequest request = requestWithBearer("POST", "/auth/initial-setup");

    assertThat(resolver.resolve(request)).isNull();
  }

  @Test
  @DisplayName("初期設定の免除は POST 限定で、同一パスの GET には及ばないこと")
  void doesNotExemptInitialSetupGet() {
    MockHttpServletRequest request = requestWithBearer("GET", "/auth/initial-setup");

    assertThat(resolver.resolve(request)).isEqualTo("broken-token-value");
  }

  @Test
  @DisplayName("既存ユーザーの招待受諾(/acceptance/existing)は免除対象でなく Bearer を解決すること")
  void resolvesTokenForAcceptAsExistingUser() {
    MockHttpServletRequest request =
        requestWithBearer("POST", "/cast-invitations/acceptance/existing");

    assertThat(resolver.resolve(request)).isEqualTo("broken-token-value");
  }

  @Test
  @DisplayName("招待の免除は view/acceptance の 2 端点限定で、トークンを載せたパスには及ばないこと")
  void doesNotExemptArbitraryCastInvitationPath() {
    // 招待トークンはパスでなく本文で運ぶ。トークンらしき 1 セグメントが免除に当たると、
    // トークンをパスに載せる handler が紛れ込んでも気付けない。
    MockHttpServletRequest request = requestWithBearer("GET", "/cast-invitations/abc123");

    assertThat(resolver.resolve(request)).isEqualTo("broken-token-value");
  }

  @Test
  @DisplayName("保護端点では従来どおり Bearer を解決すること(免除が保護端点まで漏れていないこと)")
  void resolvesTokenForProtectedEndpoint() {
    MockHttpServletRequest request = requestWithBearer("GET", "/me");

    assertThat(resolver.resolve(request)).isEqualTo("broken-token-value");
  }

  @Test
  @DisplayName("LINE 連携(/me/line)は免除対象でなく Bearer を解決すること(免除すると常に匿名となり連携先を特定できない)")
  void resolvesTokenForLineLink() {
    MockHttpServletRequest request = requestWithBearer("POST", "/me/line");

    assertThat(resolver.resolve(request)).isEqualTo("broken-token-value");
  }

  @Test
  @DisplayName("logout は免除対象でなく Bearer を解決すること(controller が自前でヘッダを読むため無害)")
  void resolvesTokenForLogout() {
    MockHttpServletRequest request = requestWithBearer("POST", "/auth/logout");

    assertThat(resolver.resolve(request)).isEqualTo("broken-token-value");
  }

  @Test
  @DisplayName("Authorization ヘッダが無ければ免除対象外の端点でも null を返すこと")
  void resolvesNullWhenNoAuthorizationHeader() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");

    assertThat(resolver.resolve(request)).isNull();
  }
}
