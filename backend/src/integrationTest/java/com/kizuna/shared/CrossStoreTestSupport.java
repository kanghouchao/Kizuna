package com.kizuna.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * クロス店舗統合テストの共通土台。
 *
 * <p>シードユーザー yamada.jiro@kizuna.test/pass（STORE_STAFF・授権店舗 = 店舗1）で平台ログインして JWT を保持し、 認証 = JWT /
 * 店舗文脈 = X-Store-ID ヘッダという本番構造どおりのリクエストヘッダを組み立てる （Bearer ヘッダ付きリクエストは CSRF 免除）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
public abstract class CrossStoreTestSupport {

  protected static final long STORE_A = 1L;
  protected static final long STORE_B = 2L;

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
    token = res.getBody().path("token").asString();
    assertThat(token).isNotBlank();
  }

  protected HttpHeaders storeHeaders(long storeId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(storeId));
    headers.setBearerAuth(token);
    return headers;
  }
}
