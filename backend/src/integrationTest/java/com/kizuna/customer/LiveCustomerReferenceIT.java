package com.kizuna.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shared.exception.DbConstraint;
import com.kizuna.shared.exception.IntegrityViolations;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 「受注・関連が参照する顧客は生きている行である」を DB が宣言的に守ることを検証する統合テスト。
 *
 * <p>アプリ層の直列化（{@code CustomerReferenceResolver}）は既知の参照作成経路について競合を閉じており、正規の経路がこの制約に当たることは無い。
 * ここが固定するのはその帯 — 解決を通さずに書く経路が将来現れたとき、墓標へ静かに着地せず違反として落ちること。だから書き込みはサービスを通さず リポジトリへ直に行う（通せば直列化が働き、DB
 * の守りを観測できない）。
 *
 * <p>違反が「制約名の付いた」ものであることも併せて見る。名前が取れなければ {@link DbConstraint} の写像は命中せず、案内は 500 に化ける。
 */
class LiveCustomerReferenceIT extends CrossStoreTestSupport {

  /** demo シード（seed/05-demo.yaml）の山田次郎（STORE_STAFF・授権店舗 = 店舗1）。関連の実行者として使用。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerMemberLinkRepository customerMemberLinkRepository;
  @Autowired private OrderRepository orderRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("受注が残っている顧客の墓標化を DB が拒むこと")
  void refusesToTombstoneACustomerThatStillHasOrders() {
    String surviving = createCustomer("受注残存続");
    String merged = createCustomer("受注残被統合");
    insertOrder(merged);

    assertViolates(() -> tombstone(merged, surviving), DbConstraint.FK_T_ORDERS_CUSTOMER_ALIVE);
    assertThat(mergedIntoOf(merged)).as("墓標化は成立していないこと").isNull();
  }

  @Test
  @DisplayName("関連が残っている顧客の墓標化を DB が拒むこと")
  void refusesToTombstoneACustomerThatStillHasLinks() {
    String surviving = createCustomer("関連残存続");
    String merged = createCustomer("関連残被統合");
    insertLink(merged);

    assertViolates(
        () -> tombstone(merged, surviving), DbConstraint.FK_T_CUSTOMER_MEMBER_LINKS_CUSTOMER_ALIVE);
    assertThat(mergedIntoOf(merged)).as("墓標化は成立していないこと").isNull();
  }

  @Test
  @DisplayName("既に墓標の顧客を指す受注・関連の挿入を DB が拒むこと")
  void refusesToReferenceATombstone() {
    String surviving = createCustomer("参照先存続");
    String tombstoned = createCustomer("参照先墓標");
    tombstone(tombstoned, surviving);

    // 正の対照: 同じ挿入が生きている行に対しては通る（拒否が挿入の組み立て不備でない証明）
    assertThatCode(() -> insertOrder(surviving)).doesNotThrowAnyException();
    assertThatCode(() -> insertLink(surviving)).doesNotThrowAnyException();

    assertViolates(() -> insertOrder(tombstoned), DbConstraint.FK_T_ORDERS_CUSTOMER_ALIVE);
    assertViolates(
        () -> insertLink(tombstoned), DbConstraint.FK_T_CUSTOMER_MEMBER_LINKS_CUSTOMER_ALIVE);
  }

  @Test
  @DisplayName("顧客未設定の受注は従来どおり成立すること（MATCH SIMPLE で検査対象外）")
  void ordersWithoutACustomerRemainValid() {
    assertThatCode(() -> insertOrder(null)).doesNotThrowAnyException();

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/orders",
            new HttpEntity<>(
                "{\"business_date\": \""
                    + LocalDate.now()
                    + "\", \"cast_id\": \""
                    + createCast()
                    + "\", \"pax\": 1}",
                managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode()).as("端点からの無帰属受注も成立すること").isEqualTo(HttpStatus.CREATED);
    // 顧客欄は non_null 直列化なので、顧客未設定は「欄が無い」として現れる
    assertThat(created.getBody().has("customer_id")).isFalse();
  }

  /** 違反が起きること自体と、その違反から制約名が取れることの両方を見る。 */
  private static void assertViolates(ThrowingCallable write, DbConstraint constraint) {
    assertThatThrownBy(write)
        .isInstanceOfSatisfying(
            DataIntegrityViolationException.class,
            ex ->
                assertThat(IntegrityViolations.violates(ex, constraint))
                    .as("制約名 %s の取れる違反であること", constraint.sqlName())
                    .isTrue());
  }

  private void tombstone(String customerId, String survivingId) {
    Customer customer = customerRepository.findById(customerId).orElseThrow();
    customer.mergeInto(survivingId);
    customerRepository.saveAndFlush(customer);
  }

  private void insertOrder(String customerId) {
    Order order =
        Order.builder()
            .businessDate(LocalDate.now())
            .customerId(customerId)
            .status(OrderStatus.CONFIRMED)
            .build();
    order.setStoreId(STORE_A);
    orderRepository.saveAndFlush(order);
  }

  private void insertLink(String customerId) {
    CustomerMemberLink link =
        CustomerMemberLink.builder()
            .customerId(customerId)
            .memberId(newMemberId())
            .memberCode("LINK" + (System.nanoTime() % 1_000_000_000L))
            .reason(LinkReason.MEMBER_CODE)
            .linkedBy(SEED_RECEPTIONIST_ID)
            .linkedAt(OffsetDateTime.now())
            .build();
    link.setStoreId(STORE_A);
    customerMemberLinkRepository.saveAndFlush(link);
  }

  /** 関連は会員行を指すので会員を起こす。有効な関連は店舗ごと会員 1 人につき 1 本なので、毎回別の会員にする。 */
  private Long newMemberId() {
    String email = "live-ref-" + nonce + "-" + System.nanoTime() + "@kizuna.test";
    ResponseEntity<JsonNode> registered =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \""
                    + email
                    + "\", \"password\": \"password1234\", \"display_name\": \"生存参照検証会員\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(registered.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);
    return jdbcTemplate.queryForObject(
        "select id from t_members where member_code = ?",
        Long.class,
        registered.getBody().path("member_code").asString());
  }

  private String mergedIntoOf(String customerId) {
    return jdbcTemplate.queryForObject(
        "select merged_into_id from t_customers where id = ?", String.class, customerId);
  }

  private String createCustomer(String label) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>(
                "{\"name\": \"生存参照-" + label + "-" + nonce + "\"}", managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 顧客作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private String createCast() {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"生存参照キャスト-" + nonce + "\"}", managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: キャスト作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }
}
