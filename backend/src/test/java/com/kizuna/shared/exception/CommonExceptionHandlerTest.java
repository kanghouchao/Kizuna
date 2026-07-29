package com.kizuna.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.user.api.dto.PlatformStaffCreateRequest;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/** {@link CommonExceptionHandler} の例外型 → status・ワイヤ形の写像を固定する単体テスト。 */
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

  @Test
  @DisplayName("NotFoundException は 404 で、領域が付けた文言をそのまま返す")
  void notFoundReturns404() {
    ResponseEntity<Map<String, Object>> response =
        handler.handle(new NotFoundException("ロールが見つかりません: 404"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).containsEntry("error", "ロールが見つかりません: 404");
  }

  @Test
  @DisplayName("ServiceUnavailableException は 503")
  void serviceUnavailableReturns503() {
    ResponseEntity<Map<String, Object>> response =
        handler.handle(new ServiceUnavailableException("メンテナンス中です"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).containsEntry("error", "メンテナンス中です");
  }

  @Test
  @DisplayName("一意制約違反（SQLSTATE 23505）だけが 409 になる")
  void uniqueViolationReturns409() {
    ResponseEntity<Map<String, Object>> response =
        handler.handle(
            new DataIntegrityViolationException(
                "save failed", new SQLException("duplicate key", "23505")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).containsEntry("error", "既に登録されている値と重複しています");
  }

  @Test
  @DisplayName("一意制約以外の整合性違反（FK 等）は 409 に化けず 500 のまま、内部 message も漏らさない")
  void otherIntegrityViolationStays500() {
    ResponseEntity<Map<String, Object>> response =
        handler.handle(
            new DataIntegrityViolationException(
                "fk violated", new SQLException("foreign key violation", "23503")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).containsEntry("error", "サーバー内部エラーが発生しました");
    assertThat(response.getBody()).doesNotContainValue("foreign key violation");
  }

  @Test
  @DisplayName("列挙に無い値は 400 で、details にワイヤ上のフィールド名（snake_case）を載せ、Java の型名は載せない")
  void unreadableBodyExposesWireFieldOnly() {
    HttpMessageNotReadableException ex = unreadable("{\"store_scope_type\":\"NOT_A_REAL_TYPE\"}");

    ResponseEntity<Map<String, Object>> response = handler.handle(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("error", "リクエストの形式が正しくありません");
    assertThat(response.getBody())
        .extracting("details")
        .isEqualTo(Map.of("store_scope_type", "値の形式が正しくありません"));
    assertThat(response.getBody().toString()).doesNotContain("com.kizuna");
  }

  @Test
  @DisplayName("構文自体が壊れた JSON は対象フィールドが無いので details を付けない")
  void malformedJsonHasNoDetails() {
    HttpMessageNotReadableException ex = unreadable("{\"email\": ");

    ResponseEntity<Map<String, Object>> response = handler.handle(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).doesNotContainKey("details");
  }

  @Test
  @DisplayName("必須パラメータ欠落は固定文言 + details で、フレームワークの生 message を透過しない")
  void missingParameterDoesNotLeakRawMessage() {
    MissingServletRequestParameterException ex =
        new MissingServletRequestParameterException("domain", "String");

    ResponseEntity<Map<String, Object>> response = handler.handle(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("error", "必須パラメータが不足しています");
    assertThat(response.getBody()).extracting("details").isEqualTo(Map.of("domain", "必須です"));
    assertThat(response.getBody().toString()).doesNotContain("java.lang.String");
  }

  /**
   * 実際に Jackson へ解釈させて、本番と同じ原因例外を持つ {@link HttpMessageNotReadableException} を組み立てる。
   *
   * <p>命名戦略は本番（{@code spring.jackson.property-naming-strategy: SNAKE_CASE}）に揃える — 原因例外の path
   * が返す名前がワイヤ形かどうかが、この検証の対象そのものであるため。
   */
  private HttpMessageNotReadableException unreadable(String json) {
    JsonMapper mapper =
        JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    try {
      mapper.readValue(json, PlatformStaffCreateRequest.class);
      throw new IllegalStateException("前提: この JSON は解釈に失敗するはず");
    } catch (RuntimeException cause) {
      return new HttpMessageNotReadableException(
          "unreadable", cause, new MockHttpInputMessage(json.getBytes()));
    }
  }
}
