package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/** {@link PlatformAuthenticationEntryPoint} の単体テスト（固定文言の応答・内部情報非漏洩）。 */
class PlatformAuthenticationEntryPointTest {

  private final PlatformAuthenticationEntryPoint entryPoint =
      new PlatformAuthenticationEntryPoint();

  @Test
  @DisplayName("401 + 固定文言 JSON を返し、WWW-Authenticate に内部理由を載せないこと")
  void commenceReturns401WithFixedMessageAndNoLeakingHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    entryPoint.commence(
        request,
        response,
        new InvalidBearerTokenException("Jwt expired at 2020-01-01T00:00:00Z（内部理由の例）"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
    assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"認証に失敗しました\"}");
    assertThat(response.getHeader("WWW-Authenticate"))
        .as("内部理由（例外メッセージ）を一切含まない固定値であること")
        .isEqualTo("Bearer");
  }
}
