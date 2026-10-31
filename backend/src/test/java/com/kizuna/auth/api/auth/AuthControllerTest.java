package com.kizuna.auth.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.auth.application.AuthService;
import com.kizuna.auth.application.AuthSessionService;
import com.kizuna.auth.application.IssuedSession;
import com.kizuna.auth.infrastructure.IssuedJwt;
import com.kizuna.auth.infrastructure.RefreshTokenCookies;
import com.kizuna.auth.infrastructure.RefreshTokenRejectedException;
import com.kizuna.auth.infrastructure.RefreshTokenRejectedException.Reason;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * ログイン・リフレッシュ・ログアウトの HTTP 契約（cookie の属性、401 の原因ヘッダ、403・409・415）を固定する。本番の SecurityConfig を載せ、 CSRF
 * トークンの無い POST と期限切れの Bearer が手前で止まらないことまで確かめる。
 */
@WebMvcTest(AuthController.class)
@Import({AuthControllerTest.Config.class, CommonExceptionHandler.class, StoreScopeHolder.class})
class AuthControllerTest {

  @TestConfiguration
  @EnableMethodSecurity(jsr250Enabled = true)
  static class Config {
    @Bean
    RefreshTokenCookies refreshTokenCookies() {
      return new RefreshTokenCookies(new AppProperties());
    }
  }

  private static final Instant REFRESH_EXPIRES_AT = Instant.now().plus(14, ChronoUnit.DAYS);
  private static final IssuedSession SESSION =
      new IssuedSession(
          7L,
          new IssuedJwt("access-token", Instant.ofEpochMilli(1_700_000_000_000L)),
          new IssuedJwt("refresh-token", REFRESH_EXPIRES_AT));

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;
  @MockitoBean private AuthSessionService authSessionService;
  @MockitoBean private SystemConfigService systemConfigService;

  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreScopeResolver storeScopeResolver;

  private static final String LOGIN_BODY = "{\"email\":\"a@kizuna.test\",\"password\":\"pass\"}";

  @Test
  @DisplayName("ログインはリフレッシュトークンを HttpOnly cookie で発行し、本文に利用者 ID と期限を載せる")
  void loginIssuesRefreshCookie() throws Exception {
    when(authService.login("a@kizuna.test", "pass")).thenReturn(SESSION);

    MockHttpServletResponse response =
        mockMvc
            .perform(
                post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("access-token"))
            .andExpect(jsonPath("$.expires_at").value(1_700_000_000_000L))
            .andExpect(jsonPath("$.user_id").value(7))
            .andExpect(jsonPath("$.refresh_expires_at").value(REFRESH_EXPIRES_AT.toEpochMilli()))
            .andReturn()
            .getResponse();

    assertIssuedCookie(response.getHeader(HttpHeaders.SET_COOKIE));
    assertThat(response.getContentAsString()).doesNotContain("refresh-token");
  }

  @ParameterizedTest
  @EnumSource(
      value = Kind.class,
      names = {"TEXT", "FORM"})
  @DisplayName("JSON 以外のログインは 415 で、cookie を発行しない（フォームによる偽装送信を止める）")
  void loginRejectsNonJson(Kind kind) throws Exception {
    MockHttpServletResponse response =
        mockMvc
            .perform(post("/auth/login").contentType(kind.type).content(kind.body))
            .andExpect(status().isUnsupportedMediaType())
            .andReturn()
            .getResponse();

    assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    verifyNoInteractions(authService);
  }

  enum Kind {
    TEXT(MediaType.TEXT_PLAIN, LOGIN_BODY),
    FORM(MediaType.APPLICATION_FORM_URLENCODED, "email=a%40kizuna.test&password=pass");

    final MediaType type;
    final String body;

    Kind(MediaType type, String body) {
      this.type = type;
      this.body = body;
    }
  }

  private static MockHttpServletRequestBuilder refresh() {
    return post("/auth/refresh")
        .header("X-Requested-With", "XMLHttpRequest")
        .cookie(new Cookie(RefreshTokenCookies.NAME, "cookie-token"));
  }

  @Test
  @DisplayName("リフレッシュは新しいトークンを返し、cookie を差し替える")
  void refreshReplacesCookie() throws Exception {
    when(authService.refresh("cookie-token", 7L)).thenReturn(SESSION);

    MockHttpServletResponse response =
        mockMvc
            .perform(refresh().header("X-Session-User-Id", "7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("access-token"))
            .andExpect(jsonPath("$.user_id").value(7))
            .andExpect(jsonPath("$.refresh_expires_at").value(REFRESH_EXPIRES_AT.toEpochMilli()))
            .andReturn()
            .getResponse();

    assertIssuedCookie(response.getHeader(HttpHeaders.SET_COOKIE));
  }

  @Test
  @DisplayName("X-Session-User-Id が無ければ照合しない")
  void refreshWithoutSessionUser() throws Exception {
    when(authService.refresh("cookie-token", null)).thenReturn(SESSION);

    mockMvc.perform(refresh()).andExpect(status().isOk());
  }

  @ParameterizedTest
  @EnumSource(Reason.class)
  @DisplayName("拒否されたリフレッシュは 401 で原因をヘッダに載せ、cookie を消す")
  void refreshRejection(Reason reason) throws Exception {
    when(authService.refresh(any(), any())).thenThrow(new RefreshTokenRejectedException(reason));

    MockHttpServletResponse response =
        mockMvc
            .perform(refresh())
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("X-Auth-Failure", reason.headerValue()))
            .andExpect(jsonPath("$.error").value(reason.message()))
            .andReturn()
            .getResponse();

    assertClearedCookie(response.getHeader(HttpHeaders.SET_COOKIE));
  }

  @Test
  @DisplayName("X-Auth-Failure の値は契約の 4 種")
  void failureHeaderValues() {
    assertThat(Reason.values())
        .extracting(Reason::headerValue)
        .containsExactly("credential_changed", "missing", "expired", "invalid");
  }

  @Test
  @DisplayName("cookie の本人がタブの本人と違えば 409 で、cookie は残す")
  void refreshConflictKeepsCookie() throws Exception {
    when(authService.refresh("cookie-token", 8L))
        .thenThrow(new ConflictException("別のアカウントでログインされました。画面を開き直してください。"));

    MockHttpServletResponse response =
        mockMvc
            .perform(refresh().header("X-Session-User-Id", "8"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("別のアカウントでログインされました。画面を開き直してください。"))
            .andReturn()
            .getResponse();

    assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
  }

  @Test
  @DisplayName("X-Requested-With の無いリフレッシュ・ログアウトは 403 で、service に到達しない")
  void cookieEndpointsRequireXmlHttpRequest() throws Exception {
    mockMvc
        .perform(post("/auth/refresh").cookie(new Cookie(RefreshTokenCookies.NAME, "c")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/auth/logout")
                .header("X-Requested-With", "fetch")
                .header("Authorization", "Bearer a"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(authService, authSessionService);
  }

  @Test
  @DisplayName("ログアウトは cookie の sid が失効させた sid と同じときだけ cookie を消す")
  void logoutClearsCookieOnlyForTheRevokedSession() throws Exception {
    when(authSessionService.logout("Bearer a", "mine")).thenReturn(true);
    when(authSessionService.logout("Bearer a", "theirs")).thenReturn(false);

    MockHttpServletResponse cleared = logout("mine");
    MockHttpServletResponse kept = logout("theirs");

    assertThat(cleared.getStatus()).isEqualTo(204);
    assertClearedCookie(cleared.getHeader(HttpHeaders.SET_COOKIE));
    assertThat(kept.getStatus()).isEqualTo(204);
    assertThat(kept.getHeader(HttpHeaders.SET_COOKIE)).isNull();
  }

  @Test
  @DisplayName("Bearer も cookie も無いログアウトも 204")
  void logoutWithoutCredentials() throws Exception {
    mockMvc
        .perform(post("/auth/logout").header("X-Requested-With", "XMLHttpRequest"))
        .andExpect(status().isNoContent());

    verify(authSessionService).logout(null, null);
  }

  private MockHttpServletResponse logout(String cookie) throws Exception {
    return mockMvc
        .perform(
            post("/auth/logout")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Authorization", "Bearer a")
                .cookie(new Cookie(RefreshTokenCookies.NAME, cookie)))
        .andReturn()
        .getResponse();
  }

  private static void assertIssuedCookie(String setCookie) {
    assertThat(setCookie)
        .startsWith("refresh_token=refresh-token;")
        .contains("Path=/api/auth", "HttpOnly", "SameSite=Strict")
        .doesNotContain("Domain=");
    long maxAge = Long.parseLong(setCookie.replaceAll(".*Max-Age=(\\d+).*", "$1"));
    assertThat(maxAge).isBetween(14L * 24 * 3600 - 60, 14L * 24 * 3600);
  }

  private static void assertClearedCookie(String setCookie) {
    assertThat(setCookie)
        .startsWith("refresh_token=;")
        .contains("Max-Age=0", "Path=/api/auth", "HttpOnly", "SameSite=Strict");
  }
}
