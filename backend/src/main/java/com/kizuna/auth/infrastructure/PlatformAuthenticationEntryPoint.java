package com.kizuna.auth.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * resource-server の認証失敗（decoder/validator/converter のいずれの拒否も）は filter 層の {@code
 * AuthenticationEntryPoint} が応答し、{@code @ControllerAdvice}（{@link
 * com.kizuna.shared.exception.CommonExceptionHandler}）を経由しない。同ハンドラの汎用フォールバック文言と同一の 401 JSON
 * をここで直接返し、Bearer 認証エラーのワイヤ契約を一致させる。
 *
 * <p>{@code WWW-Authenticate} ヘッダには {@link org.springframework.security.oauth2.core.OAuth2Error} の
 * {@code description}（署名不一致・期限切れ時刻等の内部理由）を一切載せない。攻撃者への手がかりになるため。
 */
@Component
public class PlatformAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final String BODY = "{\"error\":\"認証に失敗しました\"}";

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setHeader("WWW-Authenticate", "Bearer");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(BODY);
  }
}
