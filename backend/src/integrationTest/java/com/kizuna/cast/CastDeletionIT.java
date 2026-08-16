package com.kizuna.cast;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * キャスト削除の可否を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>受注から参照されているキャストは外部キー（RESTRICT）が削除を止める。止めること自体は正しい — 過去の受注が誰の担当だったかは売上の根拠であり、消えてよい参照ではない。
 * 固定するのは応答のほうで、これは日常操作で当たる競合なので 500 ではなく次の一手の読める 409 になる。
 */
class CastDeletionIT extends CrossStoreTestSupport {

  /** demo シード（seed/05-demo.yaml）の山田次郎（STORE_STAFF・授権店舗 = 店舗1）。受注の受付担当として使用。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("受注から参照されているキャストの削除が、案内の読める 409 になること")
  void rejectsDeletingCastReferencedByOrder() {
    String castId = createCast("受注あり");
    createOrderFor(castId);

    ResponseEntity<JsonNode> response = delete(castId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().path("error").asString())
        .isEqualTo("受注が紐づいているキャストは削除できません。在籍停止に変更してください");
    assertThat(get(castId).getStatusCode()).as("削除は成立していないこと").isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("正の対照: 受注から参照されていないキャストは削除できること")
  void deletesCastWithoutOrders() {
    String castId = createCast("受注なし");

    assertThat(delete(castId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(get(castId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private String createCast(String label) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>(
                "{\"name\": \"削除検証-" + label + "-" + nonce + "\"}", managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: キャスト作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private void createOrderFor(String castId) {
    String body =
        "{\"receptionist_id\": "
            + SEED_RECEPTIONIST_ID
            + ", \"business_date\": \""
            + LocalDate.now()
            + "\", \"cast_id\": \""
            + castId
            + "\", \"pax\": 2}";
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/orders", new HttpEntity<>(body, managerHeaders(STORE_A)), JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 受注作成が成功すること").isTrue();
  }

  private ResponseEntity<JsonNode> delete(String castId) {
    return rest.exchange(
        "/store/casts/" + castId,
        HttpMethod.DELETE,
        new HttpEntity<>(managerHeaders(STORE_A)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> get(String castId) {
    return rest.exchange(
        "/store/casts/" + castId,
        HttpMethod.GET,
        new HttpEntity<>(managerHeaders(STORE_A)),
        JsonNode.class);
  }
}
