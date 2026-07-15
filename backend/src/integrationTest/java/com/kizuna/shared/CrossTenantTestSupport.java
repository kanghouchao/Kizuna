package com.kizuna.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * クロステナント統合テストの共通土台。
 *
 * <p>シードユーザー yamada.jiro@kizuna.test/pass（STORE_STAFF・授権店舗 = 店舗1）で平台ログインして JWT を保持し、 認証 = JWT /
 * テナント文脈 = X-Tenant-ID ヘッダという本番構造どおりのリクエストヘッダを組み立てる （Bearer ヘッダ付きリクエストは CSRF 免除）。issue #225 の
 * CustomerCrossTenantIT から抽出（3 クラス目の重複解消）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class CrossTenantTestSupport {

  protected static final long TENANT_A = 1L;
  protected static final long TENANT_B = 2L;

  @Autowired protected TestRestTemplate rest;

  protected String token;

  @BeforeEach
  void loginAsSeedUser() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"yamada.jiro@kizuna.test\", \"password\": \"pass\"}", headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: シードユーザーでのログインが成功すること").isEqualTo(HttpStatus.OK);
    token = res.getBody().path("token").asText();
    assertThat(token).isNotBlank();
  }

  protected HttpHeaders tenantHeaders(long tenantId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Role", "tenant");
    headers.set("X-Tenant-ID", String.valueOf(tenantId));
    headers.setBearerAuth(token);
    return headers;
  }
}
