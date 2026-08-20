package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderApplication;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * Order のクロス店舗分離を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>store A の Order を store B が ID 指定で 読取・更新できないことを固定する（applyToLoadByKey 修正の対象経路）。
 */
class OrderCrossStoreIT extends CrossStoreTestSupport {

  /**
   * demo シード（seed/05-demo.yaml）の山田次郎(platform_users id=3, STORE_STAFF,
   * SPECIFIC_STORES{1})。受付担当として使用。
   */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  @Autowired private CustomerRepository customerRepository;
  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderApplicationRepository orderApplicationRepository;

  private final long nonce = System.nanoTime();

  private String createCustomerAs(long storeId, String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>("{\"name\": \"" + name + "-" + nonce + "\"}", storeHeaders(storeId)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful())
        .as("前提: store %d での顧客作成が成功すること", storeId)
        .isTrue();
    return created.getBody().path("id").asString();
  }

  /**
   * 第二店舗の顧客を storeFilter を経由しない直挿しで用意する（save で store_id を明示）。
   *
   * <p>基底のシードユーザーは店舗1 のみ授権なので、店舗2 の台帳は API では起こせない。
   */
  private String insertCustomerForStoreB() {
    Customer customer = Customer.builder().name("店舗B顧客-" + nonce).rank("SILVER").build();
    customer.setStoreId(STORE_B);
    return customerRepository.save(customer).getId();
  }

  private String createCastAs(long storeId, String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", storeHeaders(storeId)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful())
        .as("前提: store %d でのキャスト作成が成功すること", storeId)
        .isTrue();
    return created.getBody().path("id").asString();
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

  /** 顧客を名指した作成の要求体。 */
  private String orderBodyForCustomer(String castId, String customerId) {
    return "{\"receptionist_id\": "
        + SEED_RECEPTIONIST_ID
        + ", \"business_date\": \""
        + LocalDate.now()
        + "\", \"cast_id\": \""
        + castId
        + "\", \"customer_id\": \""
        + customerId
        + "\"}";
  }

  /** 更新の要求体。OrderUpdateRequest は営業日を持たないため、作成の体をそのまま流用できない。 */
  private String orderUpdateBody(String castId, String remarks) {
    return "{\"receptionist_id\": "
        + SEED_RECEPTIONIST_ID
        + ", \"cast_id\": \""
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
    String id = created.getBody().path("id").asString();
    assertThat(id).isNotBlank();
    return id;
  }

  @Test
  @DisplayName("他店舗の顧客を名指した受注録入は 404 になり、受注も作られないこと")
  void otherStoreCustomerCannotBeLinkedOnCreate() {
    String castId = createCastAs(STORE_A, "統合テストキャスト（顧客名指し用）");

    // 正向対照: 同一ボディ形式で自店舗の顧客を名指した作成は成功する（負向 404 が形式起因でない証明）
    String ownCustomerId = createCustomerAs(STORE_A, "統合テスト顧客（正向）");
    ResponseEntity<JsonNode> own =
        rest.postForEntity(
            "/store/orders",
            new HttpEntity<>(orderBodyForCustomer(castId, ownCustomerId), storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(own.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(own.getBody().path("customer_id").asString()).isEqualTo(ownCustomerId);

    // 負向: 書き込み先の解決は storeFilter 越しなので、他店舗の顧客は不在と区別のつかない 404 になる
    String foreignCustomerId = insertCustomerForStoreB();
    ResponseEntity<JsonNode> foreign =
        rest.postForEntity(
            "/store/orders",
            new HttpEntity<>(
                orderBodyForCustomer(castId, foreignCustomerId), storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    // 拒否された要求が受注を残していないこと（storeFilter の掛からない直読みで確かめる）
    assertThat(
            orderRepository.findAllViews(foreignCustomerId, Pageable.ofSize(1)).getTotalElements())
        .as("他店舗の顧客に着いた受注が 1 件も無いこと")
        .isZero();
  }

  @Test
  @DisplayName("群読み口が他店舗の受注を返さないこと（作業キュー・アーカイブとも）")
  void groupReadsDoNotLeakForeignOrders() {
    // 群読み口の並びは注入した EntityManager への動的な問い合わせで決まる。@Query と違い店舗行分離が
    // 効いているかは形から読めないため、実データそのものが応答へ現れないことで確かめる。
    // 件数の一致で見ると「帰属不一致」と「実データの非漏出」が区別できない。
    String foreignName = "他店舗カナリア-" + nonce;
    insertOrderForStoreB(foreignName, OrderStatus.CONFIRMED);
    insertOrderForStoreB(foreignName + "-終端", OrderStatus.CANCELLED);

    // 正向対照: 同じ条件で自店舗の受注は現れる（負向が条件の書き損じ由来でない証明）
    String castId = createCastAs(STORE_A, "群読み口の対照キャスト");
    String ownId = createOrderAs(STORE_A, castId);

    ResponseEntity<String> queue =
        rest.exchange(
            "/store/orders/work-queue?statuses=CONFIRMED&size=2000",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            String.class);
    assertThat(queue.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(queue.getBody()).as("正向対照: 自店舗の受注は現れること").contains(ownId);
    assertThat(queue.getBody()).as("他店舗の受注が作業キューへ漏れないこと").doesNotContain(foreignName);

    ResponseEntity<String> archive =
        rest.exchange(
            "/store/orders/archive?statuses=COMPLETED,CANCELLED&size=2000",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            String.class);
    assertThat(archive.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(archive.getBody()).as("他店舗の受注がアーカイブへ漏れないこと").doesNotContain(foreignName);
  }

  /**
   * 第二店舗の受注を storeFilter を経由しない直挿しで用意する。
   *
   * <p>基底のシードユーザーは店舗1 のみ授権なので、店舗2 の受注は API では起こせない。お客様名は受注側の 連絡先の写しへ入れる —
   * 群読み口の検索が読む項目であり、応答にもそのまま載るため、漏れれば文字列として現れる。
   */
  private String insertOrderForStoreB(String contactName, OrderStatus status) {
    Order order =
        Order.builder()
            .businessDate(LocalDate.now())
            .contactName(contactName)
            .pax(2)
            .status(status)
            .build();
    order.setStoreId(STORE_B);
    return orderRepository.save(order).getId();
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
  @DisplayName("店舗起点の受注は申請の空間に現れず、確定・謝絶の対象にならないこと")
  void ordersAreUnreachableThroughApplicationOperations() {
    String castId = createCastAs(STORE_A, "統合テスト（確定謝絶用）");

    // 受注と申請は別記録。受注の id を申請の操作へ渡しても、その id の申請は存在しない
    String orderId = createOrderAs(STORE_A, castId);
    ResponseEntity<JsonNode> confirmAttempt =
        rest.exchange(
            "/store/order-applications/" + orderId + "/confirmation",
            HttpMethod.POST,
            new HttpEntity<>(
                "{\"business_date\": \"" + LocalDate.now() + "\", \"pax\": 2}",
                storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(confirmAttempt.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    ResponseEntity<JsonNode> declineAttempt =
        rest.exchange(
            "/store/order-applications/" + orderId + "/refusal",
            HttpMethod.POST,
            new HttpEntity<>("{\"reason\": \"対象外\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(declineAttempt.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    // 拒否された受注は出生時のまま（確定）残っている
    ResponseEntity<JsonNode> after =
        rest.exchange(
            "/store/orders/" + orderId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(after.getBody().path("status").asString()).isEqualTo("CONFIRMED");
  }

  @Test
  @DisplayName("予約受付箱が他店舗の申請を返さず、他店舗からは確定・謝絶にも到達できないこと")
  void applicationInboxDoesNotLeakForeignApplications() {
    // 会員経路は店舗を選べるため、他店舗の申請は直挿しで用意する（基底のシードユーザーは店舗1のみ授権）。
    // 名乗った名前は受付箱の応答にそのまま載る項目であり、漏れれば文字列として現れる。
    String foreignName = "他店舗申請カナリア-" + nonce;
    OrderApplication foreign =
        OrderApplication.builder()
            .status(OrderApplicationStatus.PENDING)
            .businessDate(LocalDate.now())
            .pax(2)
            .requesterMemberCode("000000000001")
            .requesterDeclaredName(foreignName)
            .build();
    foreign.setStoreId(STORE_B);
    String foreignId = orderApplicationRepository.save(foreign).getId();

    // 正向対照: 自店舗の申請は現れる（負向が読み口の書き損じ由来でない証明）
    String ownName = "自店舗申請対照-" + nonce;
    OrderApplication own =
        OrderApplication.builder()
            .status(OrderApplicationStatus.PENDING)
            .businessDate(LocalDate.now())
            .pax(2)
            .requesterMemberCode("000000000002")
            .requesterDeclaredName(ownName)
            .build();
    own.setStoreId(STORE_A);
    orderApplicationRepository.save(own);

    ResponseEntity<String> inbox =
        rest.exchange(
            "/store/order-applications?statuses=PENDING&size=2000",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            String.class);
    assertThat(inbox.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(inbox.getBody()).as("正向対照: 自店舗の申請は現れること").contains(ownName);
    assertThat(inbox.getBody())
        .as("他店舗の申請の実データ・ID が生ボディに現れないこと")
        .doesNotContain(foreignName)
        .doesNotContain(foreignId);

    // 他店舗の申請は ID を知っていても確定・謝絶に到達できない（storeFilter は主キー直接ロードにも掛かる）
    ResponseEntity<JsonNode> foreignConfirm =
        rest.exchange(
            "/store/order-applications/" + foreignId + "/confirmation",
            HttpMethod.POST,
            new HttpEntity<>(
                "{\"business_date\": \"" + LocalDate.now() + "\", \"pax\": 2}",
                storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(foreignConfirm.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    ResponseEntity<JsonNode> foreignDecline =
        rest.exchange(
            "/store/order-applications/" + foreignId + "/refusal",
            HttpMethod.POST,
            new HttpEntity<>("{\"reason\": \"他店舗の謝絶\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(foreignDecline.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    OrderApplication untouched = orderApplicationRepository.findById(foreignId).orElseThrow();
    assertThat(untouched.getStatus())
        .as("拒否された申請は未処理のまま残ること")
        .isEqualTo(OrderApplicationStatus.PENDING);
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
            new HttpEntity<>(orderUpdateBody(castId, "対照・更新後"), storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(ownUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);

    // 負向: store B は store A の受注を更新できない
    String orderId = createOrderAs(STORE_A, castId);
    ResponseEntity<JsonNode> tampered =
        rest.exchange(
            "/store/orders/" + orderId,
            HttpMethod.PUT,
            new HttpEntity<>(orderUpdateBody(castId, "改ざん"), storeHeaders(STORE_B)),
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
