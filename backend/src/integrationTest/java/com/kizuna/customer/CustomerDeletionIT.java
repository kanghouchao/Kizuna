package com.kizuna.customer;

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
 * 顧客削除の可否を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>統合に関与した行が削除できないことは {@link CustomerMergeIT} が固定する。ここが見るのは受注から参照されている行で、
 * キャストと同じく外部キー（RESTRICT）が止める日常操作の競合なので、応答は 500 ではなく理由の読める 409 になる。
 */
class CustomerDeletionIT extends CrossStoreTestSupport {

  /** demo シード（seed/05-demo.yaml）の山田次郎（STORE_STAFF・授権店舗 = 店舗1）。受注の受付担当として使用。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("受注から参照されている顧客の削除が、理由の読める 409 になること")
  void rejectsDeletingCustomerReferencedByOrder() {
    String customerId = createCustomer("受注あり");
    createOrderFor(customerId);

    ResponseEntity<JsonNode> response = delete(customerId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().path("error").asString())
        .isEqualTo("受注が紐づいている顧客は削除できません。来店の記録が参照しています");
    assertThat(get(customerId).getStatusCode()).as("削除は成立していないこと").isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("正の対照: 受注から参照されていない顧客は削除できること")
  void deletesCustomerWithoutOrders() {
    String customerId = createCustomer("受注なし");

    assertThat(delete(customerId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(get(customerId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private String createCustomer(String label) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>(
                "{\"name\": \"削除検証-" + label + "-" + nonce + "\"}", managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 顧客作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private void createOrderFor(String customerId) {
    ResponseEntity<JsonNode> cast =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"削除検証キャスト-" + nonce + "\"}", managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(cast.getStatusCode().is2xxSuccessful()).as("前提: キャスト作成が成功すること").isTrue();
    String body =
        "{\"receptionist_id\": "
            + SEED_RECEPTIONIST_ID
            + ", \"business_date\": \""
            + LocalDate.now()
            + "\", \"cast_id\": \""
            + cast.getBody().path("id").asString()
            + "\", \"pax\": 2, \"customer_id\": \""
            + customerId
            + "\"}";
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/orders", new HttpEntity<>(body, managerHeaders(STORE_A)), JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 受注作成が成功すること").isTrue();
  }

  private ResponseEntity<JsonNode> delete(String customerId) {
    return rest.exchange(
        "/store/customers/" + customerId,
        HttpMethod.DELETE,
        new HttpEntity<>(managerHeaders(STORE_A)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> get(String customerId) {
    return rest.exchange(
        "/store/customers/" + customerId,
        HttpMethod.GET,
        new HttpEntity<>(managerHeaders(STORE_A)),
        JsonNode.class);
  }
}
