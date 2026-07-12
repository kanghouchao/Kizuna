package com.kizuna.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * 央端（CentralAuth）トークンによる {@code /files/upload} のテナント冒認（#294）を本物の PostgreSQL/Redis/MinIO で固定する統合テスト。
 *
 * <p>{@code /files/**} は {@code JwtAuthenticationFilter.issuerMatchesDomain} の issuer
 * 制約外のため央端トークンでも認証が通る。 修正前は、央端トークンに {@code X-Role:tenant} + {@code X-Tenant-ID} を付けると interceptor
 * のヘッダ兜底に落ち、指定テナントの prefix にファイルが保存されていた。本テストは詐称ヘッダ付きが 403 で拒否されること（負向）と、 詐称ヘッダ無しの央端アップロードが従来どおり
 * central 領域へ 200 で保存されること（正向対照）を固定する。
 *
 * <p>あわせてプラットフォーム発行（PlatformAuth）トークンによる同エンドポイントの中央領域アップロードが 403 で拒否されること（#322）も固定する。 PlatformAuth
 * も {@code tenantId} claim を持たず {@code @TenantOptional} の許可経路に乗るため、低権限のプラットフォーム身分が
 * 中央共有領域へ書き込めないことを保証する。
 *
 * <p>中央ログイン前提のため {@link com.kizuna.shared.CrossTenantTestSupport}（tenant ログイン）は継承せず、中央ログインを自前で行う。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FileUploadCrossTenantIT {

  @Autowired private TestRestTemplate rest;

  private String centralLogin() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/central/login",
            new HttpEntity<>("{\"username\": \"admin\", \"password\": \"pass\"}", headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 中央 admin でのログインが成功すること").isEqualTo(HttpStatus.OK);
    String token = res.getBody().path("token").asText();
    assertThat(token).isNotBlank();
    return token;
  }

  private String platformLogin() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>("{\"email\": \"admin@kizuna.test\", \"password\": \"pass\"}", headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: プラットフォーム HQ 管理者でのログインが成功すること").isEqualTo(HttpStatus.OK);
    String token = res.getBody().path("token").asText();
    assertThat(token).isNotBlank();
    return token;
  }

  /** 与えられたヘッダに multipart のダミー JPEG を載せたアップロードリクエストを組み立てる。 */
  private HttpEntity<MultiValueMap<String, Object>> uploadRequest(HttpHeaders headers) {
    ByteArrayResource image =
        new ByteArrayResource("dummy-jpeg-bytes".getBytes()) {
          @Override
          public String getFilename() {
            // 拡張子から part の Content-Type が image/jpeg に解決され、許可 MIME を満たす。
            return "photo.jpg";
          }
        };
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", image);
    return new HttpEntity<>(body, headers);
  }

  @Test
  @DisplayName(
      "央端トークン + X-Role:tenant + X-Tenant-ID の /files/upload は 403 で拒否されテナントに保存されないこと（#294）")
  void centralTokenWithSpoofedTenantHeaderIsForbidden() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(centralLogin());
    headers.set("X-Role", "tenant");
    headers.set("X-Tenant-ID", "1");

    ResponseEntity<String> res =
        rest.exchange("/files/upload", HttpMethod.POST, uploadRequest(headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("プラットフォームトークンの /files/upload は 403 で拒否され中央領域に保存されないこと（#322）")
  void platformTokenUploadIsForbidden() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(platformLogin());

    ResponseEntity<String> res =
        rest.exchange("/files/upload", HttpMethod.POST, uploadRequest(headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("央端トークン（詐称ヘッダ無し）の /files/upload は従来どおり central 領域へ 200 で保存されること")
  void centralTokenWithoutSpoofUploadsToCentral() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(centralLogin());

    ResponseEntity<JsonNode> res =
        rest.exchange("/files/upload", HttpMethod.POST, uploadRequest(headers), JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().path("url").asText()).contains("/central/");
  }
}
