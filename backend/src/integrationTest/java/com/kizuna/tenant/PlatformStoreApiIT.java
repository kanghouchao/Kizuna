package com.kizuna.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * /platform 命名空間へ統合した店舗 API（#415 収束C）の権限マトリクス・literal/{id} 共存・旧パス消滅を 本物の PostgreSQL で固定する統合テスト。
 *
 * <p>受入基準 3（stores/me・CRUD・stats・lookup の権限）、4（literal セグメントが {id} に吸われない）、 1（旧 /central・/tenant
 * パスの消滅）を対象とする。シードは v0.4.0/v0.5.0（admin=HQ ALL_STORES / 田中花子=店長 SPECIFIC{1,2} /
 * store1.kizuna.test=id 1）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlatformStoreApiIT {

  /** v0.4.0 シードの HQ 管理者（ALL_STORES・PERM_STORE_MANAGE 保持）。 */
  private static final String HQ_EMAIL = "admin@kizuna.test";

  /** v0.5.0 シードの店長（SPECIFIC_STORES {1,2}・STORE_VIEW 保持 / STORE_MANAGE 非保持）。 */
  private static final String MANAGER_EMAIL = "tanaka.hanako@kizuna.test";

  private static final String PASSWORD = "pass";

  /** v0.4.0 シードの店舗1のドメイン（id=1）。 */
  private static final String STORE1_DOMAIN = "store1.kizuna.test";

  @Autowired private TestRestTemplate rest;

  private String platformToken(String email) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, PASSWORD),
                headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: %s の平台ログインが成功すること", email).isEqualTo(HttpStatus.OK);
    String token = res.getBody().path("token").asText();
    assertThat(token).isNotBlank();
    return token;
  }

  private static HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  private ResponseEntity<String> get(String path, String token) {
    return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
  }

  private static List<Long> idsOf(JsonNode array) {
    List<Long> ids = new ArrayList<>();
    array.forEach(node -> ids.add(node.path("id").asLong()));
    return ids;
  }

  @Test
  @DisplayName("GET /platform/stores/lookup?domain= は未認証で 200 と店舗情報を返すこと（受入基準3）")
  void lookupByDomainIsPublic() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/stores/lookup?domain=" + STORE1_DOMAIN,
            HttpMethod.GET,
            HttpEntity.EMPTY,
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().path("domain").asText()).isEqualTo(STORE1_DOMAIN);
  }

  @Test
  @DisplayName("GET /platform/stores/lookup は domain 未指定なら未認証でも 400 になること（params 分岐廃止・独立子路径化の確認）")
  void lookupWithoutDomainIsBadRequest() {
    // params="domain" 分岐時は /lookup が getById("lookup") に吸われ未認証 403 になっていた。
    // 独立子路径化後は必須 @RequestParam の欠落として CommonExceptionHandler が 400 へ映射する。
    ResponseEntity<String> res =
        rest.exchange("/platform/stores/lookup", HttpMethod.GET, HttpEntity.EMPTY, String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("GET /platform/stores は STORE_MANAGE 無しトークン(店長)で 403、HQ 管理者で 200 になること（受入基準3）")
  void storeManagementListRequiresStoreManage() {
    assertThat(get("/platform/stores", platformToken(MANAGER_EMAIL)).getStatusCode())
        .as("STORE_MANAGE を持たない店長は CRUD 一覧に到達できない")
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(get("/platform/stores", platformToken(HQ_EMAIL)).getStatusCode())
        .as("STORE_MANAGE を持つ HQ 管理者は CRUD 一覧に到達できる")
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("GET /platform/stores/me は店長トークンで 200 と授権店舗(1,2)のみを返すこと（受入基準3）")
  void meReturnsAuthorizedStoresOnly() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/stores/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(MANAGER_EMAIL))),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(idsOf(res.getBody())).as("店長の授権店舗集合(1,2)のみが返ること").containsExactly(1L, 2L);
  }

  @Test
  @DisplayName("literal セグメント(/me,/stats,/lookup)は /{id} に吸われず、/{id} は実在 id で 200 を返すこと（受入基準4）")
  void literalSegmentsCoexistWithIdPath() {
    String hq = platformToken(HQ_EMAIL);

    // /{id}: 実在店舗 id=1 は getById が 200 で解決する。
    ResponseEntity<JsonNode> byId =
        rest.exchange(
            "/platform/stores/1", HttpMethod.GET, new HttpEntity<>(bearer(hq)), JsonNode.class);
    assertThat(byId.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(byId.getBody().path("id").asText()).isEqualTo("1");
    assertThat(byId.getBody().path("domain").asText()).isEqualTo(STORE1_DOMAIN);

    // literal は {id} より優先されるため、それぞれ専用ハンドラに解決され 200 になる
    // （{id} に吸われれば getById("stats"/"me") が 404 になり区別できる）。
    assertThat(get("/platform/stores/stats", hq).getStatusCode())
        .as("/stats が getById({id}) に吸われないこと")
        .isEqualTo(HttpStatus.OK);
    assertThat(get("/platform/stores/me", hq).getStatusCode())
        .as("/me が getById({id}) に吸われないこと")
        .isEqualTo(HttpStatus.OK);
    ResponseEntity<String> lookup =
        rest.exchange(
            "/platform/stores/lookup?domain=" + STORE1_DOMAIN,
            HttpMethod.GET,
            HttpEntity.EMPTY,
            String.class);
    assertThat(lookup.getStatusCode())
        .as("/lookup が getById({id}) に吸われないこと")
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("旧 /central/tenants と旧 /tenant/orders は 404 で消滅していること（受入基準1）")
  void legacyPathsAreGone() {
    String hq = platformToken(HQ_EMAIL);

    assertThat(get("/central/tenants", hq).getStatusCode())
        .as("中央名前空間の旧テナント一覧は消滅していること")
        .isEqualTo(HttpStatus.NOT_FOUND);

    HttpHeaders storeContext = bearer(hq);
    storeContext.set("X-Role", "store");
    storeContext.set("X-Store-ID", "1");
    ResponseEntity<String> orders =
        rest.exchange(
            "/tenant/orders", HttpMethod.GET, new HttpEntity<>(storeContext), String.class);
    assertThat(orders.getStatusCode())
        .as("店舗文脈ヘッダを付けても旧 /tenant 名前空間は消滅していること")
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
