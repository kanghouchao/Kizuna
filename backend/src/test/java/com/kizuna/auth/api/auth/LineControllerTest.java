package com.kizuna.auth.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.auth.application.IssuedSession;
import com.kizuna.auth.application.LineAuthService;
import com.kizuna.auth.application.LineLogin;
import com.kizuna.auth.infrastructure.IssuedJwt;
import com.kizuna.auth.infrastructure.RefreshTokenCookies;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.storescope.StoreExistenceCheck;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.application.StoreScopeResolver;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

/** LINE のログイン・登録がトークンを返すときだけリフレッシュトークンの cookie を発行することを固定する。 */
@WebMvcTest(LineController.class)
@Import({LineControllerTest.Config.class, CommonExceptionHandler.class, StoreScopeHolder.class})
class LineControllerTest {

  @TestConfiguration
  @EnableMethodSecurity(jsr250Enabled = true)
  static class Config {
    @Bean
    RefreshTokenCookies refreshTokenCookies() {
      return new RefreshTokenCookies(new AppProperties());
    }
  }

  private static final Instant REFRESH_EXPIRES_AT = Instant.now().plusSeconds(3600);
  private static final IssuedSession SESSION =
      new IssuedSession(
          7L,
          new IssuedJwt("access-token", Instant.ofEpochMilli(1_700_000_000_000L)),
          new IssuedJwt("refresh-token", REFRESH_EXPIRES_AT));

  @Autowired private MockMvc mockMvc;

  @MockitoBean private LineAuthService lineAuthService;
  @MockitoBean private SystemConfigService systemConfigService;
  @MockitoBean private StoreExistenceCheck storeExistenceCheck;
  @MockitoBean private StoreScopeResolver storeScopeResolver;

  private static final String AUTHORIZATION =
      "{\"code\":\"c\",\"redirect_uri\":\"https://kizuna.test/cb\",\"code_verifier\":\"v\"}";

  @Test
  @DisplayName("連携済みの LINE ログインは cookie を発行し、本文に利用者 ID と期限を載せる")
  void registeredLoginIssuesCookie() throws Exception {
    when(lineAuthService.login(any())).thenReturn(new LineLogin(SESSION, null, null));

    MockHttpServletResponse response =
        mockMvc
            .perform(
                post("/auth/line/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(AUTHORIZATION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registered").value(true))
            .andExpect(jsonPath("$.token").value("access-token"))
            .andExpect(jsonPath("$.user_id").value(7))
            .andExpect(jsonPath("$.refresh_expires_at").value(REFRESH_EXPIRES_AT.toEpochMilli()))
            .andReturn()
            .getResponse();

    assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
        .startsWith("refresh_token=refresh-token;")
        .contains("HttpOnly");
  }

  @Test
  @DisplayName("未登録の LINE ログインは cookie を発行せず、トークン系の項目を返さない")
  void unregisteredLoginIssuesNoCookie() throws Exception {
    when(lineAuthService.login(any())).thenReturn(new LineLogin(null, "ticket", "LINE太郎"));

    MockHttpServletResponse response =
        mockMvc
            .perform(
                post("/auth/line/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(AUTHORIZATION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registered").value(false))
            .andExpect(jsonPath("$.registration_ticket").value("ticket"))
            .andExpect(jsonPath("$.user_id").doesNotExist())
            .andExpect(jsonPath("$.refresh_expires_at").doesNotExist())
            .andReturn()
            .getResponse();

    assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
  }

  @Test
  @DisplayName("LINE 登録は 201 で cookie を発行する")
  void registerIssuesCookie() throws Exception {
    when(lineAuthService.register(any())).thenReturn(SESSION);

    MockHttpServletResponse response =
        mockMvc
            .perform(
                post("/auth/line/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"registration_ticket\":\"t\",\"display_name\":\"太郎\","
                            + "\"email\":\"taro@kizuna.test\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user_id").value(7))
            .andReturn()
            .getResponse();

    assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
        .startsWith("refresh_token=refresh-token;");
  }
}
