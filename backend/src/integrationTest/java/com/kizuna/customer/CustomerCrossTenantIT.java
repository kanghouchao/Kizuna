package com.kizuna.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.kizuna.shared.CrossTenantTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Customer のクロステナント分離を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>commit 5b39c06（tenantFilter の applyToLoadByKey=true 補完）の回帰テスト。 tenant A が作成した Customer を
 * tenant B が GET /tenant/customers/{id}（findById 経由） で読めないことを固定する。PR-B の手動 curl 検証の自動化（issue #225）。
 *
 * <p>認証・テナントヘッダ組立は {@link CrossTenantTestSupport} に共通化（issue #226 で 3 クラス目の重複を抽出）。
 */
class CustomerCrossTenantIT extends CrossTenantTestSupport {

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

    // 越権はインターセプタが JWT と X-Tenant-ID の不一致を拒否 → 403。
    // 重要なのは 200 でデータが漏れないこと（5b39c06 修正前は 200 で漏洩していた）
    assertThat(leaked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
