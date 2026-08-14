package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.order.domain.OrderReceiptToken;
import com.kizuna.order.domain.OrderReceiptTokenRepository;
import com.kizuna.order.domain.OrderReceiptTokenStatus;
import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 伝票トークンの発行を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>固定するのは「会員へ帰属しなかった完了だけがトークンを得る」ことと、「保存されるのはダイジェストだけで生値はデータベースの
 * どこにも残らない」こと。後者はバックアップや診断経路の露出が未申領クレデンシャルの漏えいにならないための条件で、 実データ（information_schema
 * から辿る全文字列列）に対して直接確かめる。
 *
 * <p>シード設定は「100 円ごとに 1 ポイント付与、利用は 100 ポイント単位」。
 */
class OrderReceiptTokenIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  /** v0.5.0 の山田次郎シード（STORE_STAFF・授権店舗 = 店舗1）。店舗A の受付担当として使用。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  private static final int TOTAL_FEE = 12000;

  /** シード設定（100 円ごとに 1 ポイント）を {@link #TOTAL_FEE} に当てた付与額。 */
  private static final int EXPECTED_PLANNED_POINTS = 120;

  @Autowired private OrderReceiptTokenRepository orderReceiptTokenRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("会員へ帰属しなかった完了はトークンを発行し、生値を完了応答へ一度だけ返すこと")
  void completionThatReachesNoMemberIssuesAReceiptToken() {
    String orderId = confirmedOrder(createCustomer("非会員"), "非会員");

    ResponseEntity<JsonNode> completed = complete(orderId, TOTAL_FEE);

    assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
    String raw = completed.getBody().path("receipt_token").asString();
    assertThat(raw).as("完了応答が生値を運ぶこと").isNotBlank();

    OrderReceiptToken token = tokenOf(orderId);
    assertThat(token.getPlannedPoints()).isEqualTo(EXPECTED_PLANNED_POINTS);
    assertThat(token.getStatus()).isEqualTo(OrderReceiptTokenStatus.ISSUED);
    assertThat(token.getExpiresAt()).isEqualTo(token.getIssuedAt().plusDays(90));

    // 二度目は読み口から取れない。応答を逃した生値を後から拾える経路があってはならない
    assertThat(get(orderId).getBody().has("receipt_token")).as("受注の読み口は生値を返さないこと").isFalse();
  }

  @Test
  @DisplayName("顧客に着いていない受注（無帰属受注）の完了でもトークンが発行されること")
  void completionOfAnOrderWithoutAnyCustomerIssuesAReceiptToken() {
    // 発行対象は「会員へ帰属しなかった受注」で、顧客の有無は問わない
    String orderId = confirmedOrder(null, "無帰属");

    ResponseEntity<JsonNode> completed = complete(orderId, TOTAL_FEE);

    assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(completed.getBody().path("receipt_token").asString()).isNotBlank();
    assertThat(tokenOf(orderId).getPlannedPoints()).isEqualTo(EXPECTED_PLANNED_POINTS);
  }

  @Test
  @DisplayName("0 円完了の無帰属受注にも付与予定額 0 でトークンが発行されること")
  void zeroFeeCompletionIssuesATokenWithNoPlannedPoints() {
    // 申領の効果は来店の可視化に閉じる。発行しないと、0 円の来店だけ取り戻せない
    String orderId = confirmedOrder(null, "0円無帰属");

    ResponseEntity<JsonNode> completed = complete(orderId, 0);

    assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(completed.getBody().path("receipt_token").asString()).isNotBlank();
    assertThat(tokenOf(orderId).getPlannedPoints()).isZero();
  }

  @Test
  @DisplayName("会員へ帰属した完了ではトークンが発行されないこと")
  void completionThatReachesAMemberIssuesNoReceiptToken() {
    String memberCode = registerMember("attributed");
    String customerId = createCustomer("会員帰属");
    linkMember(customerId, memberCode);
    String orderId = confirmedOrder(customerId, "会員帰属");

    ResponseEntity<JsonNode> completed = complete(orderId, TOTAL_FEE);

    assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(completed.getBody().has("receipt_token")).as("会員へ帰属した完了は生値を返さないこと").isFalse();
    assertThat(tokensOf(orderId)).isEmpty();
  }

  @Test
  @DisplayName("完了後に関連を解除しても、その受注へ遡ってトークンは発行されないこと")
  void releasingTheLinkAfterCompletionIssuesNoTokenRetroactively() {
    // 帰属は完了の瞬間に確定した不変の事実で、以後の関連の変化は過去の受注を書き換えない（ADR 0009）。
    // 遡及発行があると、既に会員の履歴に載っている来店を別人が申領しに来られる
    String memberCode = registerMember("retro");
    String customerId = createCustomer("遡及なし");
    linkMember(customerId, memberCode);
    String orderId = confirmedOrder(customerId, "遡及なし");
    assertThat(complete(orderId, TOTAL_FEE).getStatusCode())
        .as("前提: 会員へ帰属する完了ができること")
        .isEqualTo(HttpStatus.OK);

    ResponseEntity<Void> unlinked =
        rest.exchange(
            "/store/customers/" + customerId + "/member-link",
            HttpMethod.DELETE,
            new HttpEntity<>(storeHeaders(STORE_A)),
            Void.class);
    assertThat(unlinked.getStatusCode().is2xxSuccessful()).as("前提: 関連を解除できること").isTrue();

    assertThat(tokensOf(orderId)).isEmpty();
  }

  @Test
  @DisplayName("生値がデータベースのどの文字列列にも残らず、トークン行はダイジェストだけを持つこと")
  void theRawTokenIsNotPersistedAnywhere() {
    String remarks = "生値非永続-" + nonce;
    String orderId = confirmedOrder(null, remarks);
    String raw = complete(orderId, TOTAL_FEE).getBody().path("receipt_token").asString();
    assertThat(raw).as("前提: 生値が完了応答に現れること").isNotBlank();

    // 走査が実際に値を見つけられることを、確かに保存されている値で先に確かめる（不在の断言が
    // 「探し方が悪くて見つからないだけ」に化けないため）
    assertThat(columnsHoldingValue(remarks)).as("走査が保存済みの値を見つけること").contains("t_orders.remarks");

    // トークン行そのものが生値を持たないこと（列名を先読みせず、行の全値を見る）
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "select * from t_order_receipt_tokens where order_id = ?", orderId);
    assertThat(row.values())
        .as("トークン行のどの列も生値を持たないこと")
        .noneMatch(value -> raw.equals(String.valueOf(value)));
    assertThat(row.get("token_digest")).as("照合の材料はダイジェストであること").isNotNull().isNotEqualTo(raw);

    // 応答の経路（受注・台帳・監査）へ写し取られていないことを、全テーブルの文字列列に当たって確かめる
    assertThat(columnsHoldingValue(raw)).as("生値を持つ列").isEmpty();
  }

  /** public スキーマの全文字列列を走査し、与えた値をそのまま持つ列を返す。 */
  private List<String> columnsHoldingValue(String value) {
    List<Map<String, Object>> columns =
        jdbcTemplate.queryForList(
            """
            select c.table_name, c.column_name
            from information_schema.columns c
              join information_schema.tables t
                on t.table_schema = c.table_schema and t.table_name = c.table_name
            where c.table_schema = 'public'
              and t.table_type = 'BASE TABLE'
              and c.data_type in ('character varying', 'text', 'character')
            """);
    assertThat(columns).as("走査対象の文字列列が見つかること").isNotEmpty();
    return columns.stream()
        .filter(
            column ->
                jdbcTemplate.queryForObject(
                        "select count(*) from public.\"%s\" where \"%s\" = ?"
                            .formatted(column.get("table_name"), column.get("column_name")),
                        Integer.class,
                        value)
                    > 0)
        .map(column -> column.get("table_name") + "." + column.get("column_name"))
        .toList();
  }

  // ==================== トークンの読み出し ====================

  private OrderReceiptToken tokenOf(String orderId) {
    List<OrderReceiptToken> found = tokensOf(orderId);
    assertThat(found).as("受注 %s のトークンが 1 件だけ発行されること", orderId).hasSize(1);
    return found.get(0);
  }

  private List<OrderReceiptToken> tokensOf(String orderId) {
    return orderReceiptTokenRepository.findAll().stream()
        .filter(row -> orderId.equals(row.getOrderId()))
        .toList();
  }

  // ==================== 受注の用意 ====================

  private String confirmedOrder(String customerId, String label) {
    String castId = createCast(label);
    String orderId = createOrder(castId, customerId, label);
    return orderId;
  }

  private String createOrder(String castId, String customerId, String remarks) {
    String body =
        "{\"receptionist_id\": "
            + SEED_RECEPTIONIST_ID
            + ", \"business_date\": \""
            + LocalDate.now()
            + "\", \"cast_id\": \""
            + castId
            + "\""
            + (customerId == null ? "" : ", \"customer_id\": \"" + customerId + "\"")
            + ", \"remarks\": \""
            + remarks
            + "\"}";
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/orders", new HttpEntity<>(body, storeHeaders(STORE_A)), JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 受注作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private String createCast(String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"" + name + "-" + nonce + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: キャスト作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private String createCustomer(String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>("{\"name\": \"" + name + "-" + nonce + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 顧客作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private ResponseEntity<JsonNode> complete(String orderId, int totalFee) {
    return rest.exchange(
        "/store/orders/" + orderId + "/completion",
        HttpMethod.POST,
        new HttpEntity<>("{\"total_fee\": " + totalFee + "}", storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> get(String orderId) {
    return rest.exchange(
        "/store/orders/" + orderId,
        HttpMethod.GET,
        new HttpEntity<>(storeHeaders(STORE_A)),
        JsonNode.class);
  }

  // ==================== 会員・関連 ====================

  private String registerMember(String prefix) {
    String email = prefix + "-receipt-token-it-" + nonce + "-" + System.nanoTime() + "@kizuna.test";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \""
                    + email
                    + "\", \"password\": \""
                    + PASSWORD
                    + "\", \"display_name\": \"伝票トークン検証会員\"}",
                headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);
    return res.getBody().path("member_code").asString();
  }

  private void linkMember(String customerId, String memberCode) {
    ResponseEntity<JsonNode> linked =
        rest.exchange(
            "/store/customers/" + customerId + "/member-link",
            HttpMethod.POST,
            new HttpEntity<>("{\"member_code\": \"" + memberCode + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(linked.getStatusCode()).as("前提: 会員の紐づけが成功すること").isEqualTo(HttpStatus.OK);
  }
}
