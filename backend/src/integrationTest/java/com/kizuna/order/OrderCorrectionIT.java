package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.kizuna.order.domain.OrderCorrection;
import com.kizuna.order.domain.OrderCorrectionRepository;
import com.kizuna.order.domain.OrderFeeLineKind;
import com.kizuna.order.domain.OrderFeeLineSnapshot;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 完了後訂正の門を本物の PostgreSQL で検証する統合テスト（ADR 0019）。
 *
 * <p>固定するのは 4 つ — 門を通れるのは {@code ORDER_CORRECT} だけであること、直せるのが明細行・実績時刻・コース
 * スナップショットの三組に限られること、訂正のたびに前値の快照が残って鎖から履歴を復元できること、そして門が ポイント台帳へ一切書かないこと。
 *
 * <p>権限の差は 2 人のシードユーザーで見る。基底クラスの yamada は店舗スタッフで {@code ORDER_MANAGE} を持つが {@code ORDER_CORRECT}
 * は持たず、店長 tanaka が持つ。
 *
 * <p>シード設定は「100 円ごとに 1 ポイント付与」。
 */
class OrderCorrectionIT extends CrossStoreTestSupport {

  /** demo シード（seed/05-demo.yaml）の山田次郎（STORE_STAFF・授権店舗 = 店舗1）。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  private static final int COMPLETED_FEE = 12000;

  @Autowired private OrderCorrectionRepository orderCorrectionRepository;
  @Autowired private PointEntryRepository pointEntryRepository;

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("店長は完了した受注の三組を訂正でき、訂正のたびに前値の快照が残って鎖から履歴を復元できること")
  void managerCorrectsTheCompletedOrderAndLeavesAChainOfPriorValues() {
    String orderId = completedMemberOrder("鎖");
    long pointEntries = pointEntryRepository.count();

    ResponseEntity<JsonNode> first =
        correct(
            managerHeaders(STORE_A),
            orderId,
            """
            {"reason":"コースの取り違え","actual_arrival_time":"20:15:00","actual_end_time":"22:40:00",
             "course_name":"120 分コース","course_minutes":120,"extension_minutes":30,
             "fee_lines":[{"kind":"BASE_COURSE","amount":18000},
                          {"kind":"OPTION","name":"指名","amount":2000}]}
            """);

    assertThat(first.getStatusCode()).as("痕を生む操作なので 201").isEqualTo(HttpStatus.CREATED);
    assertThat(first.getBody().path("previous_total_fee").asInt()).isEqualTo(COMPLETED_FEE);
    assertThat(first.getBody().path("total_fee").asInt()).isEqualTo(20000);
    // 応答が名乗るのは会計金額の前後だけ。付与の差額は算出も提示もしない（手当てと結ぶ線が無いため）
    assertThat(first.getBody().path("granted_points").isMissingNode()).isTrue();
    assertThat(first.getBody().path("grant_difference").isMissingNode()).isTrue();

    JsonNode detail = orderJson(managerHeaders(STORE_A), orderId);
    assertThat(detail.path("status").asString()).as("訂正は状態を戻さないこと").isEqualTo("COMPLETED");
    assertThat(detail.path("actual_arrival_time").asString()).isEqualTo("20:15:00");
    assertThat(detail.path("actual_end_time").asString()).isEqualTo("22:40:00");
    assertThat(detail.path("course_name").asString()).isEqualTo("120 分コース");
    assertThat(detail.path("course_minutes").asInt()).isEqualTo(120);
    assertThat(detail.path("extension_minutes").asInt()).isEqualTo(30);
    assertThat(detail.path("total_fee").asInt()).isEqualTo(20000);
    assertThat(detail.path("auto_grant_points").asInt()).as("門はポイントを動かさないこと").isEqualTo(120);
    // 基本コース料金の行名称はコース名の写しから採る（金額だけ直る半修状態を作らない）
    assertThat(detail.path("fee_lines").get(0).path("name").asString()).isEqualTo("120 分コース");

    // 全量送信なので、行が在るのにコース名を落とした要求は撥ねられる（金額だけ直る半修状態を作らない）
    assertThat(
            correct(
                    managerHeaders(STORE_A),
                    orderId,
                    """
                    {"reason":"コース名の消去","fee_lines":[{"kind":"BASE_COURSE","amount":18000}]}
                    """)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    // 二度目の訂正。ある訂正の「後値」＝次の訂正の「前値」で、鎖が繋がる
    ResponseEntity<JsonNode> second =
        correct(
            managerHeaders(STORE_A),
            orderId,
            """
            {"reason":"オプションの取り消し","course_name":"120 分コース","course_minutes":120,
             "fee_lines":[{"kind":"BASE_COURSE","amount":18000}]}
            """);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(second.getBody().path("previous_total_fee").asInt()).isEqualTo(20000);

    assertThat(pointEntryRepository.count())
        .as("2 度の訂正を通しても台帳へ 1 行も書かないこと")
        .isEqualTo(pointEntries);

    List<OrderCorrection> chain =
        orderCorrectionRepository.findByOrderIdOrderByCorrectedAtAscIdAsc(orderId);
    assertThat(chain).as("撥ねられた訂正は痕を残さないこと（快照は先に起こすが巻き戻る）").hasSize(2);

    OrderCorrection before1 = chain.get(0);
    assertThat(before1.getReason()).isEqualTo("コースの取り違え");
    assertThat(before1.getCorrectedBy()).isNotNull();
    assertThat(before1.getTotalFee()).isEqualTo(COMPLETED_FEE);
    assertThat(before1.getActualArrivalTime()).as("訂正前は実績時刻を持たない受注だったこと").isNull();
    assertThat(before1.getFeeLines())
        .extracting(OrderFeeLineSnapshot::kind, OrderFeeLineSnapshot::amount)
        .containsExactly(tuple(OrderFeeLineKind.SURCHARGE, COMPLETED_FEE));

    OrderCorrection before2 = chain.get(1);
    assertThat(before2.getReason()).isEqualTo("オプションの取り消し");
    assertThat(before2.getTotalFee()).as("一度目の後値が二度目の前値であること").isEqualTo(20000);
    assertThat(before2.getActualEndTime()).isEqualTo(LocalTime.of(22, 40));
    assertThat(before2.getCourseName()).isEqualTo("120 分コース");
    assertThat(before2.getFeeLines())
        .extracting(OrderFeeLineSnapshot::kind, OrderFeeLineSnapshot::amount)
        .containsExactly(
            tuple(OrderFeeLineKind.BASE_COURSE, 18000), tuple(OrderFeeLineKind.OPTION, 2000));

    // 二度目の要求は実績時刻と延長分数を載せていない。全量送信なので「変更しない」ではなく「値なし」が当たる
    JsonNode afterSecond = orderJson(managerHeaders(STORE_A), orderId);
    assertThat(afterSecond.path("actual_arrival_time").isMissingNode()).isTrue();
    assertThat(afterSecond.path("actual_end_time").isMissingNode()).isTrue();
    assertThat(afterSecond.path("extension_minutes").isMissingNode()).isTrue();
    assertThat(afterSecond.path("course_name").asString()).isEqualTo("120 分コース");
  }

  @Test
  @DisplayName("受注管理だけの店員は完了後訂正に届かず、受注も痕も動かないこと")
  void staffWithOrderManageAloneCannotReachTheGate() {
    String orderId = completedOrder("権限");

    ResponseEntity<JsonNode> denied =
        correct(
            storeHeaders(STORE_A),
            orderId,
            """
            {"reason":"金額の誤記","fee_lines":[]}
            """);

    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(orderJson(managerHeaders(STORE_A), orderId).path("total_fee").asInt())
        .isEqualTo(COMPLETED_FEE);
    assertThat(orderCorrectionRepository.findByOrderIdOrderByCorrectedAtAscIdAsc(orderId))
        .isEmpty();
  }

  @Test
  @DisplayName("ポイント利用の行は門内でも編集できず、訂正を跨いで残ること")
  void pointRedemptionLinesSurviveTheGate() {
    String orderId = completedOrderUsingPoints("ポイント", 100);
    long pointEntries = pointEntryRepository.count();

    // ポイント利用の誤りはポイント機構経由で直す。門の要求に混ぜることはできない
    ResponseEntity<JsonNode> rejected =
        correct(
            managerHeaders(STORE_A),
            orderId,
            """
            {"reason":"ポイントの取り違え",
             "fee_lines":[{"kind":"POINT_REDEMPTION","name":"ポイント利用","amount":300}]}
            """);
    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    ResponseEntity<JsonNode> accepted =
        correct(
            managerHeaders(STORE_A),
            orderId,
            """
            {"reason":"金額の誤記","fee_lines":[{"kind":"SURCHARGE","name":"指名料","amount":8000}]}
            """);
    assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    JsonNode lines = orderJson(managerHeaders(STORE_A), orderId).path("fee_lines");
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0).path("kind").asString()).isEqualTo("POINT_REDEMPTION");
    assertThat(lines.get(0).path("amount").asInt()).as("減項は正値で返ること").isEqualTo(100);
    // 合計はポイント控除後の請求額なので、残った利用の行のぶん下がったまま
    assertThat(accepted.getBody().path("total_fee").asInt()).isEqualTo(7900);

    assertThat(pointEntryRepository.count()).as("門は台帳へ一切書かないこと").isEqualTo(pointEntries);
  }

  @Test
  @DisplayName("画面が見ていた版と食い違う訂正は 409 で差し戻され、本体も痕も動かないこと")
  void staleCorrectionsAreRefused() {
    // 全量置換なので、開いたまま別の操作者が訂正を済ませていると、送らなかった項目まで開いた時点の
    // 値で押し戻す。楽観ロックは要求ごとに現物を読み直すため、版の照合が無いと検出できない
    String orderId = completedOrder("陳腐化");
    long opened = currentVersion(managerHeaders(STORE_A), orderId);

    assertThat(
            correct(
                    managerHeaders(STORE_A),
                    orderId,
                    """
                    {"reason":"先に済んだ訂正",
                     "fee_lines":[{"kind":"SURCHARGE","name":"指名料","amount":9000}]}
                    """)
                .getStatusCode())
        .as("前提: 先の訂正が成立すること")
        .isEqualTo(HttpStatus.CREATED);

    ResponseEntity<JsonNode> stale =
        correctAt(
            managerHeaders(STORE_A),
            orderId,
            opened,
            """
            {"reason":"開いたままの画面からの訂正","fee_lines":[]}
            """);

    assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(orderJson(managerHeaders(STORE_A), orderId).path("total_fee").asInt())
        .as("撥ねた訂正は先の訂正を巻き戻さないこと")
        .isEqualTo(9000);
    assertThat(orderCorrectionRepository.findByOrderIdOrderByCorrectedAtAscIdAsc(orderId))
        .as("撥ねた訂正は痕を残さないこと")
        .hasSize(1);
  }

  @Test
  @DisplayName("取消済みの受注と、状態を戻そうとする要求が撥ねられること")
  void cancelledOrdersAndStateRollbackAreRefused() {
    // 誤取消の救済は同内容で受注を起こし直すこと。取消理由と実行者の保護を訂正口で迂回させない
    String cancelled = confirmedOrder("取消");
    ResponseEntity<Void> cancelResponse =
        rest.exchange(
            "/store/orders/" + cancelled + "/cancellation",
            HttpMethod.POST,
            new HttpEntity<>("{\"reason\":\"客都合\"}", storeHeaders(STORE_A)),
            Void.class);
    assertThat(cancelResponse.getStatusCode()).as("前提: 取消が成功すること").isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(
            correct(
                    managerHeaders(STORE_A),
                    cancelled,
                    """
            {"reason":"取消の訂正","fee_lines":[]}
            """)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    // 確定済み（未完了）も門の外。内容の修正は汎用更新が受け持つ
    String confirmed = confirmedOrder("未完了");
    assertThat(
            correct(
                    managerHeaders(STORE_A),
                    confirmed,
                    """
            {"reason":"先回りの訂正","fee_lines":[]}
            """)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    // 状態を戻す口は要求の型に存在しない（未知の項目は撥ねられる）
    assertThat(
            correct(
                    managerHeaders(STORE_A),
                    completedOrder("回退"),
                    """
                    {"reason":"完了の取り消し","status":"CONFIRMED","fee_lines":[]}
                    """)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  /** 現物の版を載せて訂正する。画面が読み直してから送る通常の経路にあたる。 */
  private ResponseEntity<JsonNode> correct(HttpHeaders headers, String orderId, String body) {
    return correctAt(headers, orderId, currentVersion(headers, orderId), body);
  }

  /** 版を明示して訂正する。陳腐化した要求の拒否を見るテストだけが直に使う。 */
  private ResponseEntity<JsonNode> correctAt(
      HttpHeaders headers, String orderId, long version, String body) {
    return rest.exchange(
        "/store/orders/" + orderId + "/corrections",
        HttpMethod.POST,
        new HttpEntity<>(
            body.replaceFirst("\\{", "{\"expected_version\":" + version + ","), headers),
        JsonNode.class);
  }

  private long currentVersion(HttpHeaders headers, String orderId) {
    return orderJson(headers, orderId).path("version").asLong();
  }

  private JsonNode orderJson(HttpHeaders headers, String orderId) {
    return rest.exchange(
            "/store/orders/" + orderId, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class)
        .getBody();
  }

  /** 会計 12000 円で完了した、顧客も会員も着かない受注（付与は起こらない）。 */
  private String completedOrder(String label) {
    return complete(confirmedOrder(label, null), null);
  }

  /** 会計 12000 円で完了した会員の受注。付与 120 ポイントが台帳へ入るので、訂正が付与を動かさないことを見られる。 */
  private String completedMemberOrder(String label) {
    return complete(confirmedOrder(label, linkedCustomer(label)), null);
  }

  /**
   * ポイント利用の行を持つ完了済みの受注。
   *
   * <p>利用には残高が要るので、同じ顧客で 1 件先に完了させて付与を積む — この経路（完了時の会員解決）が
   * ポイント利用の行が生まれる唯一の入口であり、台帳へ直接積んで作る形では門の対象になる行が同じ由来にならない。
   */
  private String completedOrderUsingPoints(String label, int usePoints) {
    String customerId = linkedCustomer(label);
    complete(confirmedOrder(label + "-獲得", customerId), null);
    return complete(confirmedOrder(label + "-利用", customerId), usePoints);
  }

  private String complete(String orderId, Integer usePoints) {
    String body =
        "{\"expected_version\":"
            + orderVersion(storeHeaders(STORE_A), orderId)
            + ",\"fee_lines\":[{\"kind\":\"SURCHARGE\",\"name\":\"会計\",\"amount\":"
            + COMPLETED_FEE
            + "}]"
            + (usePoints == null ? "" : ", \"use_points\": " + usePoints)
            + "}";
    ResponseEntity<JsonNode> completed =
        rest.exchange(
            "/store/orders/" + orderId + "/completion",
            HttpMethod.POST,
            new HttpEntity<>(body, storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(completed.getStatusCode()).as("前提: 完了が成功すること").isEqualTo(HttpStatus.OK);
    return orderId;
  }

  private String confirmedOrder(String label) {
    return confirmedOrder(label, null);
  }

  private String confirmedOrder(String label, String customerId) {
    String castId = createCast(label);
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
            + label
            + "\"}";
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/orders", new HttpEntity<>(body, storeHeaders(STORE_A)), JsonNode.class);
    assertThat(created.getStatusCode()).as("前提: 受注作成が成功すること").isEqualTo(HttpStatus.CREATED);
    return created.getBody().path("id").asString();
  }

  /** 新しい会員を登録し、新しい顧客行へ紐づけてその顧客 ID を返す。 */
  private String linkedCustomer(String label) {
    HttpHeaders anonymous = new HttpHeaders();
    anonymous.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> member =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \"correction-it-"
                    + nonce
                    + "-"
                    + System.nanoTime()
                    + "@kizuna.test\", \"password\": \"password1234\", \"display_name\": \"訂正検証会員\"}",
                anonymous),
            JsonNode.class);
    assertThat(member.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);

    ResponseEntity<JsonNode> customer =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>(
                "{\"name\": \"訂正検証-" + label + "-" + nonce + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(customer.getStatusCode().is2xxSuccessful()).as("前提: 顧客作成が成功すること").isTrue();
    String customerId = customer.getBody().path("id").asString();

    ResponseEntity<JsonNode> linked =
        rest.exchange(
            "/store/customers/" + customerId + "/member-link",
            HttpMethod.POST,
            new HttpEntity<>(
                "{\"member_code\": \"" + member.getBody().path("member_code").asString() + "\"}",
                storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(linked.getStatusCode()).as("前提: 会員の紐づけが成功すること").isEqualTo(HttpStatus.OK);
    return customerId;
  }

  private String createCast(String label) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>(
                "{\"name\": \"訂正検証-" + label + "-" + nonce + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode()).as("前提: キャスト作成が成功すること").isEqualTo(HttpStatus.CREATED);
    return created.getBody().path("id").asString();
  }
}
