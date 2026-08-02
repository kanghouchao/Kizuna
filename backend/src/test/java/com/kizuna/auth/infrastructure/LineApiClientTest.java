package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.config.AppProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link LineApiClient} の単体テスト。LINE 実サービスの代わりに、要求本文を記録して定型応答を返す HTTP スタブを
 * ループバックへ立てる（実チャネルが無くても、送信するフォーム項目と 応答解釈・失敗時の写像を固定できる）。
 */
class LineApiClientTest {

  private static final LineChannel CHANNEL = new LineChannel("channel-id", "channel-secret");

  private HttpServer server;
  private LineApiClient client;

  private final Map<String, String> requestBodies = new LinkedHashMap<>();

  private int tokenStatus = 200;
  private String tokenBody = "{\"id_token\": \"dummy-id-token\"}";
  private int verifyStatus = 200;
  private String verifyBody = "{\"sub\": \"U-line-1\", \"name\": \"LINE太郎\"}";

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/oauth2/v2.1/token", exchange -> respond(exchange, "token", tokenStatus, tokenBody));
    server.createContext(
        "/oauth2/v2.1/verify", exchange -> respond(exchange, "verify", verifyStatus, verifyBody));
    server.start();

    AppProperties appProperties = new AppProperties();
    appProperties.getLine().setApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
    client = new LineApiClient(appProperties);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  private void respond(HttpExchange exchange, String name, int status, String body)
      throws IOException {
    try (InputStream in = exchange.getRequestBody()) {
      requestBodies.put(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  @Test
  @DisplayName("LINE が空の応答本文を返した場合も未処理例外にせず認証失敗として扱う")
  void emptyResponseBodyIsAuthenticationFailure() {
    tokenBody = "";

    assertThatThrownBy(
            () -> client.exchangeAndVerify(CHANNEL, "auth-code", "https://app.test/cb", "verifier"))
        .isInstanceOf(LineAuthenticationException.class);
  }

  @Test
  @DisplayName("認可コードを交換し id_token を検証して LINE ユーザー ID と表示名を返す")
  void exchangeAndVerifyReturnsIdentity() {
    LineIdentity identity =
        client.exchangeAndVerify(CHANNEL, "auth-code", "https://app.test/cb", "verifier");

    assertThat(identity).isEqualTo(new LineIdentity("U-line-1", "LINE太郎"));
  }

  @Test
  @DisplayName("トークン交換にはチャネル資格情報・redirect_uri・code_verifier を送る")
  void tokenRequestCarriesChannelCredentials() {
    client.exchangeAndVerify(CHANNEL, "auth-code", "https://app.test/cb", "verifier");

    assertThat(requestBodies.get("token"))
        .contains("grant_type=authorization_code")
        .contains("code=auth-code")
        .contains("client_id=channel-id")
        .contains("client_secret=channel-secret")
        .contains("code_verifier=verifier")
        .contains("redirect_uri=https%3A%2F%2Fapp.test%2Fcb");
    // 検証はチャネル ID 付きで LINE 自身に行わせる（自前の署名検証を持たない）。
    assertThat(requestBodies.get("verify"))
        .contains("id_token=dummy-id-token")
        .contains("client_id=channel-id");
  }

  @Test
  @DisplayName("LINE がエラーを返したら 401 系の認証例外へ写像する（LINE 側の詳細はワイヤへ出さない）")
  void lineErrorBecomesAuthenticationException() {
    tokenStatus = 400;
    tokenBody = "{\"error\": \"invalid_grant\"}";

    assertThatThrownBy(
            () -> client.exchangeAndVerify(CHANNEL, "used-code", "https://app.test/cb", "verifier"))
        .isInstanceOf(LineAuthenticationException.class)
        .hasMessageNotContaining("invalid_grant");
  }

  @Test
  @DisplayName("id_token を含まないトークン応答は認証例外")
  void missingIdTokenBecomesAuthenticationException() {
    tokenBody = "{\"access_token\": \"only-access-token\"}";

    assertThatThrownBy(
            () -> client.exchangeAndVerify(CHANNEL, "auth-code", "https://app.test/cb", "verifier"))
        .isInstanceOf(LineAuthenticationException.class);
  }

  @Test
  @DisplayName("sub を含まない検証応答は認証例外（同一性の根拠が無いまま先へ進ませない）")
  void missingSubBecomesAuthenticationException() {
    verifyBody = "{\"name\": \"LINE太郎\"}";

    assertThatThrownBy(
            () -> client.exchangeAndVerify(CHANNEL, "auth-code", "https://app.test/cb", "verifier"))
        .isInstanceOf(LineAuthenticationException.class);
  }

  @Test
  @DisplayName("JSON として解釈できない応答は認証例外")
  void malformedResponseBecomesAuthenticationException() {
    verifyBody = "not-json";

    assertThatThrownBy(
            () -> client.exchangeAndVerify(CHANNEL, "auth-code", "https://app.test/cb", "verifier"))
        .isInstanceOf(LineAuthenticationException.class);
  }
}
