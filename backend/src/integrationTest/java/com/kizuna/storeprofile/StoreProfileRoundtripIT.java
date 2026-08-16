package com.kizuna.storeprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.CrossStoreTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * StoreProfile の String-PK 化後の {@code /store/config} 永続化経路を固定する IT。
 *
 * <p>demo シード行（seed が String id "1"/"2" を固定する t_store_profiles）に対し、String PK ロード・@Version 楽観ロック・基類
 * （StoreScopedEntity）のタイムスタンプが GET→PUT→GET の roundtrip で機能することを確認する。主体は種子の yamada.jiro （束「店舗スタッフ」=
 * STORE_PROFILE_MANAGE 保持、店舗1）で、{@link CrossStoreTestSupport} のログイン主体をそのまま用いる。
 */
class StoreProfileRoundtripIT extends CrossStoreTestSupport {

  @Test
  @DisplayName("/store/config は GET→PUT→GET で更新が永続化され、id は非空 JSON 文字列であること")
  void configRoundtripPersistsAndExposesStringId() {
    // GET: シード行が String PK でロードでき、id が数値でなく引用符付き JSON 文字列であること。
    ResponseEntity<String> rawGet =
        rest.exchange(
            "/store/config", HttpMethod.GET, new HttpEntity<>(storeHeaders(STORE_A)), String.class);
    assertThat(rawGet.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(rawGet.getBody())
        .as("id は数値でなく引用符付き JSON 文字列（String PK）であること")
        .containsPattern("\"id\"\\s*:\\s*\"[^\"]+\"");

    ResponseEntity<JsonNode> got =
        rest.exchange(
            "/store/config",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    String id = got.getBody().path("id").asString();
    assertThat(id).as("String PK が非空であること").isNotBlank();

    // PUT: catch_copy を run-unique 値へ更新（@Version・updated_at が動く経路）。
    String unique = "IT_catch_" + System.nanoTime();
    ResponseEntity<JsonNode> put =
        rest.exchange(
            "/store/config",
            HttpMethod.PUT,
            new HttpEntity<>("{\"catch_copy\": \"" + unique + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(put.getBody().path("catch_copy").asString()).isEqualTo(unique);

    // GET: 更新が永続化され、id は不変であること。
    ResponseEntity<JsonNode> reget =
        rest.exchange(
            "/store/config",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(reget.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(reget.getBody().path("catch_copy").asString()).isEqualTo(unique);
    assertThat(reget.getBody().path("id").asString()).as("id は更新後も不変であること").isEqualTo(id);
  }

  /**
   * jsonb コレクション（sns_links・partner_links）を含む更新経路を固定する。
   *
   * <p>要素型が Serializable でないと hypersistence-utils の深いコピーが実行時に失敗し 500 になる。空配列では深いコピーが走らないため、 要素が 1
   * 件以上あるペイロードでなければこの経路は検証できない。
   */
  @Test
  @DisplayName("/store/config は要素を持つ sns_links・partner_links を含む PUT を永続化できること")
  void configPutPersistsNonEmptyJsonbCollections() {
    String label = "IT_sns_" + System.nanoTime();
    String partner = "IT_partner_" + System.nanoTime();
    String body =
        """
        {"sns_links": [{"platform": "x", "url": "https://example.com/sns", "label": "%s"}],
         "partner_links": [{"name": "%s", "url": "https://example.com/partner", "logo_url": ""}]}
        """
            .formatted(label, partner);

    ResponseEntity<JsonNode> put =
        rest.exchange(
            "/store/config",
            HttpMethod.PUT,
            new HttpEntity<>(body, storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(put.getBody().path("sns_links").path(0).path("label").asString()).isEqualTo(label);
    assertThat(put.getBody().path("partner_links").path(0).path("name").asString())
        .isEqualTo(partner);

    ResponseEntity<JsonNode> reget =
        rest.exchange(
            "/store/config",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(reget.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode snsLink = reget.getBody().path("sns_links").path(0);
    assertThat(snsLink.path("platform").asString()).isEqualTo("x");
    assertThat(snsLink.path("url").asString()).isEqualTo("https://example.com/sns");
    assertThat(snsLink.path("label").asString()).isEqualTo(label);
    JsonNode partnerLink = reget.getBody().path("partner_links").path(0);
    assertThat(partnerLink.path("name").asString()).isEqualTo(partner);
    assertThat(partnerLink.path("url").asString()).isEqualTo("https://example.com/partner");
  }
}
