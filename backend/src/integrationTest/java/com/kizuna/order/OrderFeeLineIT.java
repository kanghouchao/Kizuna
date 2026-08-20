package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 受注明細と導出合計の不変量を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>固定するのは #751 の裁定 — 合計が常に行の総和であること、種別ごとの符号 CHECK が集約を迂回した書き込みを DB で塞ぐこと、ポイント利用の行が完了処理の専有であること。
 *
 * <p>符号 CHECK は素の SQL でしか確かめられない。集約は同じ判定を先に行うため、API 経由の要求は DB へ届く前に 400 で撥ねられ、DDL
 * の制約が実在するかは分からないままになる。
 */
class OrderFeeLineIT extends CrossStoreTestSupport {

  @Autowired private JdbcTemplate jdbcTemplate;

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("作成した受注の合計が明細の総和になり、詳細が減項を正値で返すこと")
  void creationDerivesTheTotalFromTheFeeLines() {
    String orderId =
        createOrder(
            """
            "course_name": "90 分コース",
            "fee_lines": [
              {"kind": "BASE_COURSE", "amount": 18000},
              {"kind": "OPTION", "name": "指名オプション", "amount": 3000},
              {"kind": "DISCOUNT", "name": "初回割", "amount": 5000}
            ]
            """);

    JsonNode detail = orderJson(orderId);
    assertThat(detail.path("total_fee").asInt()).isEqualTo(16000);
    assertThat(storedTotalFee(orderId)).as("列も同じ値で書かれていること").isEqualTo(16000);
    assertThat(storedLineSum(orderId)).isEqualTo(16000);

    JsonNode lines = detail.path("fee_lines");
    assertThat(lines).hasSize(3);
    // 基本コース料金の名称はコース名の写しから採る
    assertThat(lines.get(0).path("name").asString()).isEqualTo("90 分コース");
    // 減項は正値で返る（引くことは種別が表す）
    assertThat(lines.get(2).path("kind").asString()).isEqualTo("DISCOUNT");
    assertThat(lines.get(2).path("amount").asInt()).isEqualTo(5000);
  }

  @Test
  @DisplayName("明細を差し替えると合計が取り直され、空配列で内訳を空にできること")
  void updateReplacesTheBreakdownAndRederivesTheTotal() {
    String orderId =
        createOrder("\"fee_lines\": [{\"kind\": \"OPTION\", \"name\": \"A\", \"amount\": 2000}]");

    ResponseEntity<JsonNode> replaced =
        update(
            orderId, "\"fee_lines\": [{\"kind\": \"OPTION\", \"name\": \"B\", \"amount\": 7000}]");
    assertThat(replaced.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(storedTotalFee(orderId)).isEqualTo(7000);
    assertThat(storedLineCount(orderId)).as("差し替えは前の行を残さないこと").isEqualTo(1);

    assertThat(update(orderId, "\"fee_lines\": []").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(storedTotalFee(orderId)).isZero();
    assertThat(storedLineCount(orderId)).isZero();
  }

  @Test
  @DisplayName("店舗の書き口はポイント利用の明細を作れないこと")
  void storeFacingWritesCannotCreateThePointRedemptionLine() {
    String orderId = createOrder("\"fee_lines\": []");

    ResponseEntity<JsonNode> rejected =
        update(
            orderId,
            "\"fee_lines\": [{\"kind\": \"POINT_REDEMPTION\", \"name\": \"ポイント利用\", \"amount\": 300}]");

    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(storedLineCount(orderId)).isZero();
  }

  @Test
  @DisplayName("符号約定に反する要求が集約に撥ねられること")
  void theAggregateRefusesAmountsThatContradictTheKind() {
    String orderId = createOrder("\"fee_lines\": []");

    assertThat(
            update(
                    orderId,
                    "\"fee_lines\": [{\"kind\": \"DISCOUNT\", \"name\": \"割引\", \"amount\": -1}]")
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            update(
                    orderId,
                    "\"fee_lines\": [{\"kind\": \"OPTION\", \"name\": \"追加\", \"amount\": -1}]")
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(storedLineCount(orderId)).isZero();
  }

  @Test
  @DisplayName("集約を迂回した書き込みを DB の符号 CHECK が塞ぐこと")
  void theDatabaseRefusesWrongSignedRowsWrittenAroundTheAggregate() {
    String orderId = createOrder("\"fee_lines\": []");

    // 加算の種別に負値
    assertThatThrownBy(() -> insertLine(orderId, "OPTION", "迂回", -1))
        .isInstanceOf(DataIntegrityViolationException.class);
    // 減算の種別に正値
    assertThatThrownBy(() -> insertLine(orderId, "DISCOUNT", "迂回", 1))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertLine(orderId, "POINT_REDEMPTION", "迂回", 1))
        .isInstanceOf(DataIntegrityViolationException.class);
    // 閉集合の外
    assertThatThrownBy(() -> insertLine(orderId, "HOTEL_FEE", "ホテル代", 1))
        .isInstanceOf(DataIntegrityViolationException.class);

    // 手動調整だけが符号を縛られない
    insertLine(orderId, "MANUAL_ADJUST", "迂回", -1);
    assertThat(storedLineCount(orderId)).isEqualTo(1);
  }

  @Test
  @DisplayName("完了が会計の場で送られた内訳を当て、合計を取り直すこと")
  void completionAppliesTheBreakdownSentAtCheckout() {
    // ポイント利用の行が台帳の減算仕訳と対で書かれることは OrderCompletionIT が固定する（残高が要る）
    String orderId =
        createOrder("\"fee_lines\": [{\"kind\": \"OPTION\", \"name\": \"仮\", \"amount\": 1}]");

    assertThat(complete(orderId, null).getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(storedTotalFee(orderId)).isEqualTo(12000);
    assertThat(storedLineSum(orderId)).as("和と合計は常に一致すること").isEqualTo(12000);
    assertThat(storedLineCount(orderId)).as("会計の内訳が仮の行を置き換えていること").isEqualTo(1);
  }

  // ==================== 補助 ====================

  private String createOrder(String extraFields) {
    String body =
        "{\"business_date\": \""
            + LocalDate.now()
            + "\", \"cast_id\": \""
            + createCast()
            + "\", "
            + extraFields
            + "}";
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/orders", new HttpEntity<>(body, storeHeaders(STORE_A)), JsonNode.class);
    assertThat(created.getStatusCode()).as("前提: 受注作成が成功すること").isEqualTo(HttpStatus.CREATED);
    return created.getBody().path("id").asString();
  }

  private ResponseEntity<JsonNode> update(String orderId, String extraFields) {
    return rest.exchange(
        "/store/orders/" + orderId,
        HttpMethod.PUT,
        new HttpEntity<>("{" + extraFields + "}", storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> complete(String orderId, Integer usePoints) {
    String body =
        "{\"fee_lines\": [{\"kind\": \"SURCHARGE\", \"name\": \"会計\", \"amount\": 12000}]"
            + (usePoints == null ? "" : ", \"use_points\": " + usePoints)
            + "}";
    return rest.postForEntity(
        "/store/orders/" + orderId + "/completion",
        new HttpEntity<>(body, storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private JsonNode orderJson(String orderId) {
    ResponseEntity<JsonNode> detail =
        rest.exchange(
            "/store/orders/" + orderId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(detail.getStatusCode()).as("前提: 詳細が読めること").isEqualTo(HttpStatus.OK);
    return detail.getBody();
  }

  private String createCast() {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>(
                "{\"name\": \"明細-" + nonce + "-" + System.nanoTime() + "\"}",
                storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: キャスト作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private void insertLine(String orderId, String kind, String name, int amount) {
    jdbcTemplate.update(
        "insert into t_order_fee_lines (order_id, kind, name, amount, created_at, updated_at,"
            + " version) values (?, ?, ?, ?, now(), now(), 0)",
        orderId,
        kind,
        name,
        amount);
  }

  private int storedTotalFee(String orderId) {
    return jdbcTemplate.queryForObject(
        "select total_fee from t_orders where id = ?", Integer.class, orderId);
  }

  private int storedLineSum(String orderId) {
    return jdbcTemplate.queryForObject(
        "select coalesce(sum(amount), 0) from t_order_fee_lines where order_id = ?",
        Integer.class,
        orderId);
  }

  private int storedLineCount(String orderId) {
    return jdbcTemplate.queryForObject(
        "select count(*) from t_order_fee_lines where order_id = ?", Integer.class, orderId);
  }
}
