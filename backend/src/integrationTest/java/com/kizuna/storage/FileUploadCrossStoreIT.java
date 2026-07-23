package com.kizuna.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;

/**
 * 平台トークンによる {@code /files/upload} のプラットフォーム保存判定を本物の PostgreSQL/Redis/MinIO で固定する統合テスト。
 *
 * <p>{@code /files/**} は {@code JwtAuthenticationFilter.issuerMatchesDomain} の issuer
 * 制約外のため平台トークンでも 認証が通る。プラットフォーム領域（platform prefix）への保存は HQ 管理者（role claim =
 * HQ_ADMIN）のみに限定し、それ以外のロール・ 店舗詐称ヘッダは fail-closed で 403 拒否する。
 *
 * <p>HQ トークンに {@code X-Role:store} + {@code X-Store-ID} を付けても、{@code StoreIdInterceptor} の
 * STORE_BRIDGE_ROLES が HQ_ADMIN を含まないため店舗文脈は確立できず 403 で拒否される（負向）。詐称ヘッダ無しの HQ アップロードは platform 領域へ
 * 200 で保存される（正向対照）。HQ 以外の平台トークン（店舗スタッフ）は詐称ヘッダ無しでも プラットフォーム保存を拒否される（follow-up:
 * 低権限身分のプラットフォーム共有領域書き込み封鎖）。
 *
 * <p>プラットフォームログイン前提を廃し、v0.4.0/v0.5.0 の平台シードでログインする。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class FileUploadCrossStoreIT {

  /** v0.4.0 シードの HQ 管理者（ALL_STORES）。プラットフォーム保存を許可される唯一のロール。 */
  private static final String HQ_EMAIL = "admin@kizuna.test";

  /** v0.5.0 シードの店舗スタッフ（SPECIFIC_STORES {1}）。プラットフォーム保存は拒否される。 */
  private static final String STAFF_EMAIL = "yamada.jiro@kizuna.test";

  @Autowired private TestRestTemplate rest;

  private String platformLogin(String email) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                String.format("{\"email\": \"%s\", \"password\": \"pass\"}", email), headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: %s の平台ログインが成功すること", email).isEqualTo(HttpStatus.OK);
    String token = res.getBody().path("token").asString();
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
  @DisplayName("HQ トークン + X-Role:store + X-Store-ID の /files/upload は 403 で拒否されること（過橋の店舗ロール制限）")
  void hqTokenWithSpoofedStoreHeaderIsForbidden() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(platformLogin(HQ_EMAIL));
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", "1");

    ResponseEntity<String> res =
        rest.exchange("/files/upload", HttpMethod.POST, uploadRequest(headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("HQ 以外の平台トークン（店舗スタッフ）の /files/upload は 403 で拒否されプラットフォーム領域に保存されないこと")
  void nonHqPlatformTokenUploadIsForbidden() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(platformLogin(STAFF_EMAIL));

    ResponseEntity<String> res =
        rest.exchange("/files/upload", HttpMethod.POST, uploadRequest(headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("HQ トークン（詐称ヘッダ無し）の /files/upload は platform 領域へ 200 で保存されること")
  void hqTokenWithoutSpoofUploadsToPlatform() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(platformLogin(HQ_EMAIL));

    ResponseEntity<JsonNode> res =
        rest.exchange("/files/upload", HttpMethod.POST, uploadRequest(headers), JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().path("url").asString()).contains("/platform/");
  }
}
