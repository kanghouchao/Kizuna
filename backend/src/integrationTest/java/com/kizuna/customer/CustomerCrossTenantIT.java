package com.kizuna.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
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
 * Customer のクロステナント分離を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>commit 5b39c06（tenantFilter の applyToLoadByKey=true 補完）の回帰テスト。 tenant A が作成した Customer を
 * tenant B が GET /tenant/customers/{id}（findById 経由） で読めないことを固定する。PR-B の手動 curl 検証の自動化（issue #225）。
 *
 * <p>認証はシードユーザー admin@store1.kizuna.com/pass（tenant 1）のログインで得た JWT。テナント文脈は X-Tenant-ID
 * ヘッダで切り替える（認証=JWT、 テナント解決=ヘッダという本番構造どおり。5b39c06 の curl 検証と同構）。Bearer ヘッダ付きリクエストは CSRF 免除。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerCrossTenantIT {

  private static final long TENANT_A = 1L;
  private static final long TENANT_B = 2L;

  @Autowired private TestRestTemplate rest;

  private String token;

  @BeforeEach
  void login() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Role", "tenant");
    headers.set("X-Tenant-ID", String.valueOf(TENANT_A));
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/tenant/login",
            new HttpEntity<>(
                "{\"username\": \"admin@store1.kizuna.com\", \"password\": \"pass\"}", headers),
            JsonNode.class);
    assertThat(res.getStatusCode())
        .as("前提: シードユーザー admin@store1.kizuna.com/pass でのログインが成功すること")
        .isEqualTo(HttpStatus.OK);
    token = res.getBody().path("token").asText();
    assertThat(token).isNotBlank();
  }

  private HttpHeaders tenantHeaders(long tenantId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Role", "tenant");
    headers.set("X-Tenant-ID", String.valueOf(tenantId));
    headers.setBearerAuth(token);
    return headers;
  }

  private String createCustomerAs(long tenantId, String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/tenant/customers",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", tenantHeaders(tenantId)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful())
        .as("前提: tenant %d での顧客作成が成功すること", tenantId)
        .isTrue();
    String id = created.getBody().path("id").asText();
    assertThat(id).isNotBlank();
    return id;
  }

  @Test
  @DisplayName("自テナントで作成した顧客は GET /tenant/customers/{id} で取得できること")
  void ownTenantCanReadOwnCustomer() {
    String id = createCustomerAs(TENANT_A, "統合テスト顧客（正向）");

    ResponseEntity<JsonNode> got =
        rest.exchange(
            "/tenant/customers/" + id,
            HttpMethod.GET,
            new HttpEntity<>(tenantHeaders(TENANT_A)),
            JsonNode.class);

    assertThat(got.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(got.getBody().path("id").asText()).isEqualTo(id);
  }

  @Test
  @DisplayName("他テナントの顧客 ID を GET しても取得できないこと（applyToLoadByKey 回帰）")
  void otherTenantCannotReadForeignCustomerById() {
    String id = createCustomerAs(TENANT_A, "統合テスト顧客（負向）");

    ResponseEntity<JsonNode> leaked =
        rest.exchange(
            "/tenant/customers/" + id,
            HttpMethod.GET,
            new HttpEntity<>(tenantHeaders(TENANT_B)),
            JsonNode.class);

    // ServiceException(@ResponseStatus BAD_REQUEST) → 400。
    // 重要なのは 200 でデータが漏れないこと（5b39c06 修正前は 200 で漏洩していた）
    assertThat(leaked.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
