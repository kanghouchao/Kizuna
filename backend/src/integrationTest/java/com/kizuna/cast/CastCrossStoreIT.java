package com.kizuna.cast;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.kizuna.shared.CrossStoreTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Cast のクロス店舗分離を本物の PostgreSQL で検証する統合テスト（issue #226）。
 *
 * <p>PR-B の手動 curl 検証の自動化。store A の Cast を store B が ID 指定で 読取・更新できないことを固定する（5b39c06
 * applyToLoadByKey 修正の対象経路）。
 */
class CastCrossStoreIT extends CrossStoreTestSupport {

  private String createCastAs(long storeId, String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", storeHeaders(storeId)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful())
        .as("前提: store %d でのキャスト作成が成功すること", storeId)
        .isTrue();
    String id = created.getBody().path("id").asText();
    assertThat(id).isNotBlank();
    return id;
  }

  @Test
  @DisplayName("他店舗のキャスト ID を GET しても取得できないこと")
  void otherStoreCannotReadForeignCastById() {
    String id = createCastAs(STORE_A, "統合テストキャスト（読取）");

    ResponseEntity<JsonNode> own =
        rest.exchange(
            "/store/casts/" + id,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(own.getStatusCode()).as("正向対照: 自店舗では読める").isEqualTo(HttpStatus.OK);

    ResponseEntity<JsonNode> leaked =
        rest.exchange(
            "/store/casts/" + id,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_B)),
            JsonNode.class);
    // 200 でデータが漏れないことが本質（越権はインターセプタが拒否 → 403）
    assertThat(leaked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("他店舗のキャストを更新できず、データも変わらないこと")
  void otherStoreCannotUpdateForeignCast() {
    // 正向対照: 同一ボディ形式で自店舗の更新は成功する（負向 403 がバリデーション起因でない証明）
    String controlId = createCastAs(STORE_A, "統合テストキャスト（対照）");
    ResponseEntity<JsonNode> ownUpdate =
        rest.exchange(
            "/store/casts/" + controlId,
            HttpMethod.PUT,
            new HttpEntity<>("{\"name\": \"統合テストキャスト（対照・更新後）\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(ownUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);

    // 負向: store B は store A のキャストを更新できない
    String id = createCastAs(STORE_A, "統合テストキャスト（更新前）");
    ResponseEntity<JsonNode> tampered =
        rest.exchange(
            "/store/casts/" + id,
            HttpMethod.PUT,
            new HttpEntity<>("{\"name\": \"統合テストキャスト（改ざん）\"}", storeHeaders(STORE_B)),
            JsonNode.class);
    assertThat(tampered.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // データ不変: store A から見て名前が変わっていない
    ResponseEntity<JsonNode> after =
        rest.exchange(
            "/store/casts/" + id,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(after.getBody().path("name").asText()).isEqualTo("統合テストキャスト（更新前）");
  }
}
