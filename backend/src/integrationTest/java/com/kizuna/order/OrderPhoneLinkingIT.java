package com.kizuna.order;

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
 * 受注録入の電話照合を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>固定するのは照合の 3 分岐 — 0 件＝顧客を新規作成して紐づけ、1 件＝その顧客に紐づけ、2 件以上＝自動照合を断念して顧客未設定のまま成立。
 *
 * <p>2 件以上の分岐は単体テストでは守れない。台帳照合は派生クエリの戻り値の形（{@code Optional} か {@code List} か）で決まり、複数行を {@code
 * Optional} に詰める失敗は Spring Data が実行時に起こすものなので、モックした repository は 1 行だけ返す前提のまま緑になる。
 *
 * <p>照合キーの電話番号は実行ごとに変える。同店同号の重複を意図的に作るテストであり、固定値だと他の実行の残りと混ざって分岐の前提が崩れる。
 */
class OrderPhoneLinkingIT extends CrossStoreTestSupport {

  /** v0.5.0 の山田次郎シード（STORE_STAFF・授権店舗 = 店舗1）。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("同店同号の顧客が複数あると自動照合を断念し、顧客未設定のまま受注が成立して連絡先が残ること")
  void severalMatchesLeaveTheCustomerUnsetAndKeepTheReportedContact() {
    String phone = phone("dup");
    createCustomer("重複照合A-" + nonce, phone);
    createCustomer("重複照合B-" + nonce, phone);

    ResponseEntity<JsonNode> created = createOrderByPhone("重複照合の来客", phone);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    // 一致行の中に会員関連付きの行があり得るため、機械が 1 行を選ぶことはしない（ADR 0009）
    assertThat(created.getBody().hasNonNull("customer_id")).as("顧客は未設定のままであること").isFalse();
    // 顧客未設定で成立させる以上、録入された連絡先は受注側に残さないと消える
    assertThat(created.getBody().path("contact_name").asString()).isEqualTo("重複照合の来客");
    assertThat(created.getBody().path("contact_phone_number").asString()).isEqualTo(phone);
  }

  @Test
  @DisplayName("同店同号が 1 件だけならその顧客に紐づき、連絡先の写しは残らないこと")
  void theOnlyMatchIsLinkedAndNeedsNoContactCopy() {
    String phone = phone("one");
    String customerId = createCustomer("単一照合-" + nonce, phone);

    ResponseEntity<JsonNode> created = createOrderByPhone("単一照合の来客", phone);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().path("customer_id").asString()).isEqualTo(customerId);
    // 台帳の行が連絡先を持つので、受注側の写しは要らない
    assertThat(created.getBody().hasNonNull("contact_name")).as("写しは残さないこと").isFalse();
    assertThat(created.getBody().hasNonNull("contact_phone_number")).as("写しは残さないこと").isFalse();
  }

  @Test
  @DisplayName("同店同号が無ければ顧客が新規作成されて紐づくこと")
  void noMatchCreatesTheCustomer() {
    String phone = phone("new");

    ResponseEntity<JsonNode> created = createOrderByPhone("新規照合の来客", phone);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String customerId = created.getBody().path("customer_id").asString();
    assertThat(customerId).isNotBlank();
    assertThat(created.getBody().hasNonNull("contact_name")).as("写しは残さないこと").isFalse();

    JsonNode customer = getCustomer(customerId);
    assertThat(customer.path("name").asString()).isEqualTo("新規照合の来客");
    assertThat(customer.path("phone_number").asString()).isEqualTo(phone);
  }

  // ==================== 準備 ====================

  /** 実行ごと・分岐ごとに異なる照合キー。列は VARCHAR(50)。 */
  private String phone(String label) {
    return "090" + Math.abs((label + nonce).hashCode()) + nonce;
  }

  private ResponseEntity<JsonNode> createOrderByPhone(String customerName, String phone) {
    String castId = createCast("電話照合IT-" + customerName + "-" + nonce);
    String body =
        "{\"receptionist_id\": "
            + SEED_RECEPTIONIST_ID
            + ", \"business_date\": \""
            + LocalDate.now()
            + "\", \"cast_id\": \""
            + castId
            + "\", \"customer_name\": \""
            + customerName
            + "\", \"phone_number\": \""
            + phone
            + "\"}";
    return rest.postForEntity(
        "/store/orders", new HttpEntity<>(body, storeHeaders(STORE_A)), JsonNode.class);
  }

  private String createCustomer(String name, String phone) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>(
                "{\"name\": \"" + name + "\", \"phone_number\": \"" + phone + "\"}",
                storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 顧客作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private String createCast(String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: キャスト作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private JsonNode getCustomer(String customerId) {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/customers/" + customerId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 作成された顧客を読めること").isEqualTo(HttpStatus.OK);
    return res.getBody();
  }
}
