package com.kizuna.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * クロス店舗統合テストの共通土台。
 *
 * <p>シードユーザー yamada.jiro@kizuna.test/pass（STORE_STAFF・授権店舗 = 店舗1）で平台ログインして JWT を保持し、 認証 = JWT /
 * 店舗文脈 = X-Store-ID ヘッダという本番構造どおりのリクエストヘッダを組み立てる （Bearer ヘッダ付きリクエストは CSRF 免除）。
 *
 * <p>準金銭的な確定操作（ポイントの手動調整、誤帰属の訂正とその一段目の無効化）は店長限定のため、店員の身分では 403 になる。 それらを叩くテストは {@link
 * #managerHeaders(long)} を使う（ADR 0012）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
public abstract class CrossStoreTestSupport {

  protected static final long STORE_A = 1L;
  protected static final long STORE_B = 2L;

  @Autowired protected TestRestTemplate rest;

  protected String token;

  /** 店長（tanaka.hanako シード）の JWT。要るテストだけが払うよう、初めて使うときに取得する。 */
  private String managerToken;

  @BeforeEach
  void loginAsSeedUser() {
    token = login("yamada.jiro@kizuna.test");
  }

  protected HttpHeaders storeHeaders(long storeId) {
    return headersFor(storeId, token);
  }

  /**
   * 店長の身分での店舗文脈ヘッダ。{@code POINT_ADJUST} を要する端点にはこちらを使う。
   *
   * <p>demo シード（seed/05-demo.yaml）の田中花子（STORE_MANAGER・授権店舗 = 店舗1）。店員の {@link #storeHeaders(long)}
   * と使い分けること自体が、 権限の線がどこに引かれているかの記録になる。
   */
  protected HttpHeaders managerHeaders(long storeId) {
    if (managerToken == null) {
      managerToken = login("tanaka.hanako@kizuna.test");
    }
    return headersFor(storeId, managerToken);
  }

  /**
   * 受注が現に持つ版。完了・訂正の要求はこれを載せて初めて通る（版が食い違えば 409）。
   *
   * <p>作成直後の版を定数で書かず読み直すのは、間に更新を挟むテストでも同じ助手で済ませるため。
   */
  protected long orderVersion(HttpHeaders headers, String orderId) {
    return rest.exchange(
            "/store/orders/" + orderId, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class)
        .getBody()
        .path("version")
        .asLong();
  }

  /** 種子ユーザーとして平台ログインする。実行者の身分そのものが主題のテストが使う（HQ 管理者など）。 */
  protected String login(String email) {
    return loginWithPassword(email, "pass");
  }

  /** テスト内で作成したアカウントとして平台ログインする。種子の合言葉を使えない場合に使う。 */
  protected String loginWithPassword(String email, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}", headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: %s でのログインが成功すること", email).isEqualTo(HttpStatus.OK);
    String issued = res.getBody().path("token").asString();
    assertThat(issued).isNotBlank();
    return issued;
  }

  private static HttpHeaders headersFor(long storeId, String bearerToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(storeId));
    headers.setBearerAuth(bearerToken);
    return headers;
  }
}
