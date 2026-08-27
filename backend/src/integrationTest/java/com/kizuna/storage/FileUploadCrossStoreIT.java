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
 * 平台トークンによる {@code POST /files} のプラットフォーム保存判定を本物の PostgreSQL/Redis/MinIO で固定する統合テスト。
 *
 * <p>{@code /files/**} も含め全リクエストが単一 issuer（PlatformAuth）の decoder 検証を通るため、平台トークンで
 * 認証が通る。プラットフォーム共有領域（platform prefix）への保存は {@code PLATFORM_ASSET_MANAGE} の保持者のみに限定し、 非保持者は
 * fail-closed で 403 拒否する。
 *
 * <p>保存先は役割ではなく店舗文脈の有無が決める。店舗文脈を確立した店舗スタッフの保存先はその店舗配下になり、プラットフォーム 共有領域へは入らない（負向）。ヘッダ無しの HQ アップロードは
 * platform 領域へ 201 で保存される（正向対照）。HQ 以外の平台トークン（店舗スタッフ）はヘッダ無しでは プラットフォーム保存を拒否される。
 *
 * <p>HQ は店舗の面から撤退しており storeBridge を持たないため、{@code X-Role:store} + {@code X-Store-ID} を付けた HQ
 * の要求は僭称として 403 になる（ADR 0021）。{@code isAuthenticated()} だけの端点でも店舗文脈の確立が塞がることを、この経路で固定する。
 *
 * <p>プラットフォームログイン前提を廃し、ベースラインの平台シード（seed/04-platform-admin.yaml と seed/05-demo.yaml）でログインする。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class FileUploadCrossStoreIT {

  /** seed/04-platform-admin.yaml の HQ 管理者（ALL_STORES）。プラットフォーム保存を許可される唯一のロール。 */
  private static final String HQ_EMAIL = "admin@kizuna.test";

  /** demo シードの店舗スタッフ（SPECIFIC_STORES {1}）。プラットフォーム保存は拒否される。 */
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
  @DisplayName("店舗スタッフ + X-Role:store + X-Store-ID の POST /files は店舗配下へ保存されプラットフォーム領域には入らないこと")
  void storeContextDecidesTheStoragePrefix() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(platformLogin(STAFF_EMAIL));
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", "1");

    ResponseEntity<JsonNode> res =
        rest.exchange("/files", HttpMethod.POST, uploadRequest(headers), JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(res.getBody().path("url").asString())
        .as("店舗文脈が保存先を決め、プラットフォーム共有領域へは入らないこと")
        .contains("/1/")
        .doesNotContain("/platform/");
  }

  @Test
  @DisplayName("HQ トークン + X-Role:store + X-Store-ID の POST /files は僭称として 403 になること")
  void hqTokenWithStoreHeaderIsForbidden() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(platformLogin(HQ_EMAIL));
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", "1");

    ResponseEntity<String> res =
        rest.exchange("/files", HttpMethod.POST, uploadRequest(headers), String.class);

    assertThat(res.getStatusCode())
        .as("撤退後の HQ は storeBridge を持たず、店舗文脈を名乗れないこと")
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("HQ 以外の平台トークン（店舗スタッフ）の POST /files は 403 で拒否されプラットフォーム領域に保存されないこと")
  void nonHqPlatformTokenUploadIsForbidden() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(platformLogin(STAFF_EMAIL));

    ResponseEntity<String> res =
        rest.exchange("/files", HttpMethod.POST, uploadRequest(headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("HQ トークン（店舗ヘッダ無し）の POST /files は platform 領域へ 201 で保存されること")
  void hqTokenWithoutSpoofUploadsToPlatform() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(platformLogin(HQ_EMAIL));

    ResponseEntity<JsonNode> res =
        rest.exchange("/files", HttpMethod.POST, uploadRequest(headers), JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(res.getBody().path("url").asString()).contains("/platform/");
  }
}
