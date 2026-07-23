package com.kizuna.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

/** {@link CommonExceptionHandler} の AuthenticationException 型別文言マッピングの単体テスト。 */
class CommonExceptionHandlerTest {

  private final CommonExceptionHandler handler = new CommonExceptionHandler();

  @Test
  @DisplayName("DisabledException は 401 かつ固定文言「アカウントが無効化されています」")
  void disabledExceptionReturnsFixedMessage() {
    ResponseEntity<Map<String, Object>> response =
        handler.handle(new DisabledException("internal detail not for wire"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).containsEntry("error", "アカウントが無効化されています");
  }

  @Test
  @DisplayName("BadCredentialsException は 401 かつ固定文言「メールアドレスまたはパスワードが正しくありません」")
  void badCredentialsExceptionReturnsFixedMessage() {
    ResponseEntity<Map<String, Object>> response =
        handler.handle(new BadCredentialsException("internal detail not for wire"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).containsEntry("error", "メールアドレスまたはパスワードが正しくありません");
  }

  @Test
  @DisplayName("その他の AuthenticationException は 401 かつ汎用固定文言「認証に失敗しました」で内部 message は透過しない")
  void otherAuthenticationExceptionReturnsGenericFixedMessage() {
    ResponseEntity<Map<String, Object>> response =
        handler.handle(new InsufficientAuthenticationException("internal detail not for wire"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).containsEntry("error", "認証に失敗しました");
    assertThat(response.getBody()).doesNotContainValue("internal detail not for wire");
  }
}
