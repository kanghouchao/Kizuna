package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Order のクロス店舗分離を本物の PostgreSQL で検証する統合テスト（issue #226）。
 *
 * <p>PR-B の手動 curl 検証の自動化。store A の Order を store B が ID 指定で 読取・更新できないことを固定する（5b39c06
 * applyToLoadByKey 修正の対象経路）。
 */
class OrderCrossStoreIT extends CrossStoreTestSupport {

  /** v0.5.0 central/01 の山田次郎シード(platform_users id=3, STORE_STAFF, SPECIFIC_STORES{1})。受付担当として使用。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  private String createCastAs(long storeId, String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", storeHeaders(storeId)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful())
        .as("前提: store %d でのキャスト作成が成功すること", storeId)
        .isTrue();
    return created.getBody().path("id").asText();
  }

  private String orderBody(String castId, String remarks) {
    return "{\"receptionist_id\": "
        + SEED_RECEPTIONIST_ID
        + ", \"business_date\": \""
        + LocalDate.now()
        + "\", \"cast_id\": \""
        + castId
        + "\", \"remarks\": \""
        + remarks
        + "\"}";
  }

  private String createOrderAs(long storeId, String castId) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/orders",
            new HttpEntity<>(orderBody(castId, "統合テスト受注"), storeHeaders(storeId)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful())
        .as("前提: store %d での受注作成が成功すること", storeId)
        .isTrue();
    String id = created.getBody().path("id").asText();
    assertThat(id).isNotBlank();
    return id;
  }

  @Test
  @DisplayName("他店舗の受注 ID を GET しても取得できないこと")
  void otherStoreCannotReadForeignOrderById() {
    String castId = createCastAs(STORE_A, "統合テストキャスト（受注読取用）");
    String orderId = createOrderAs(STORE_A, castId);

    ResponseEntity<JsonNode> own =
        rest.exchange(
            "/store/orders/" + orderId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(own.getStatusCode()).as("正向対照: 自店舗では読める").isEqualTo(HttpStatus.OK);

    ResponseEntity<JsonNode> leaked =
        rest.exchange(
            "/store/orders/" + orderId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_B)),
            JsonNode.class);
    assertThat(leaked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("他店舗の受注を更新できないこと")
  void otherStoreCannotUpdateForeignOrder() {
    String castId = createCastAs(STORE_A, "統合テストキャスト（受注更新用）");

    // 正向対照: 同一ボディ形式で自店舗の更新は成功する（負向 403 がバリデーション起因でない証明）
    String controlId = createOrderAs(STORE_A, castId);
    ResponseEntity<JsonNode> ownUpdate =
        rest.exchange(
            "/store/orders/" + controlId,
            HttpMethod.PUT,
            new HttpEntity<>(orderBody(castId, "対照・更新後"), storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(ownUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);

    // 負向: store B は store A の受注を更新できない
    String orderId = createOrderAs(STORE_A, castId);
    ResponseEntity<JsonNode> tampered =
        rest.exchange(
            "/store/orders/" + orderId,
            HttpMethod.PUT,
            new HttpEntity<>(orderBody(castId, "改ざん"), storeHeaders(STORE_B)),
            JsonNode.class);
    assertThat(tampered.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // store A からは引き続き読める（レコード自体は健在）
    ResponseEntity<JsonNode> after =
        rest.exchange(
            "/store/orders/" + orderId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
