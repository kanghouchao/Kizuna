package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderAttributionSource;
import com.kizuna.order.domain.OrderAttributionStatus;
import com.kizuna.order.domain.OrderReceiptToken;
import com.kizuna.order.domain.OrderReceiptTokenRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * 誤帰属の訂正（無効化と伝票トークンの再発行）を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>固定するのは 4 つ。①無効化が理由・実行者・時刻を<b>行として残す</b>こと（列の書き込みが実際に往復すること）。
 * ②無効化された来店が会員の履歴から消える一方でポイント台帳は動かないこと — 清算は手動調整であり、訂正は機械の級聯にしない（ADR 0009）。
 * ③再発行された伝票の申領期限が<b>再発行時点</b>から 90 日で数え直されること（同 ADR の意図的な例外）。 ④再発行された伝票で正しい本人が申領でき、根拠 RECEIPT_TOKEN
 * の帰属が成立すること。
 *
 * <p>シード設定は「100 円ごとに 1 ポイント付与、利用は 100 ポイント単位」。
 */
class OrderAttributionInvalidationIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  /** v0.5.0 の山田次郎シード（STORE_STAFF・授権店舗 = 店舗1）。店舗A の受付担当として使用。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  private static final int TOTAL_FEE = 12000;

  /** シード設定（100 円ごとに 1 ポイント）を {@link #TOTAL_FEE} に当てた付与額。 */
  private static final int EXPECTED_POINTS = 120;

  private static final String REASON = "別人の来店として取り違えたため";

  @Autowired private OrderAttributionRepository orderAttributionRepository;
  @Autowired private OrderReceiptTokenRepository orderReceiptTokenRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("無効化は理由・実行者・時刻を行に残し、帰属そのものの記録は書き換えないこと")
  void invalidationPersistsTheReasonActorAndTime() {
    RegisteredMember wrongMember = registerAndLogin("wrong");
    String orderId = completedOrderAttributedTo(wrongMember, "誤帰属担当-" + nonce);

    ResponseEntity<JsonNode> invalidated = invalidate(orderId, REASON);

    assertThat(invalidated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(invalidated.getBody().path("attributed").asBoolean()).isFalse();

    // 実データを読む。@Column の書き込み可否を取り違えると、行は INVALIDATED になりつつ理由だけ NULL になり、
    // 集約を往復させないテストは緑のままになる
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "select status, invalidated_reason, invalidated_by, invalidated_at"
                + " from t_order_attributions where order_id = ?",
            orderId);
    assertThat(row.get("status")).isEqualTo(OrderAttributionStatus.INVALIDATED.name());
    assertThat(row.get("invalidated_reason")).isEqualTo(REASON);
    assertThat(row.get("invalidated_by")).isEqualTo(seedStaffId());
    assertThat(row.get("invalidated_at")).isNotNull();

    // 誰の来店として記録されていたかは訂正後も辿れること（監査の対象そのもの）
    OrderAttribution attribution = attributionOf(orderId);
    assertThat(attribution.getMemberCode()).isEqualTo(wrongMember.memberCode());
    assertThat(attribution.getSource()).isEqualTo(OrderAttributionSource.COMPLETION);
  }

  @Test
  @DisplayName("無効化で来店は会員の履歴から消え、ポイント台帳は動かないこと（清算は手動調整）")
  void invalidationHidesTheVisitButLeavesTheLedgerUntouched() {
    RegisteredMember wrongMember = registerAndLogin("ledger");
    String castName = "台帳無波及担当-" + nonce;
    String orderId = completedOrderAttributedTo(wrongMember, castName);

    assertThat(castNamesInVisitsOf(wrongMember)).as("前提: 無効化の前は来店として見えること").contains(castName);
    assertThat(balanceOf(wrongMember)).as("前提: 付与が台帳へ入っていること").isEqualTo(EXPECTED_POINTS);

    assertThat(invalidate(orderId, REASON).getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(castNamesInVisitsOf(wrongMember)).as("無効化された来店は履歴から消えること").doesNotContain(castName);
    // 誤って付与されたポイントは残る。取り消すなら理由の残る手動調整で行う（ADR 0009）
    assertThat(balanceOf(wrongMember)).as("台帳は動かないこと").isEqualTo(EXPECTED_POINTS);
    assertThat(ledgerRowsFor(orderId)).as("台帳へ打ち消しの仕訳も積まないこと").isEqualTo(1);
  }

  @Test
  @DisplayName("理由の無い無効化は撥ねられ、帰属記録が有効なまま残ること")
  void invalidationWithoutAReasonIsRejected() {
    RegisteredMember wrongMember = registerAndLogin("noreason");
    String orderId = completedOrderAttributedTo(wrongMember, "理由なし担当-" + nonce);

    assertThat(invalidate(orderId, " ").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    assertThat(attributionOf(orderId).getStatus()).isEqualTo(OrderAttributionStatus.ACTIVE);
  }

  @Test
  @DisplayName("再発行の申領期限は原期限を引き継がず、再発行から 90 日で数え直されること")
  void reissuedTokenRestartsTheNinetyDayWindow() {
    // 完了時に会員へ達しなかった受注から始める。誤帰属が申領で成立した形なので、この受注には原期限を持つ
    // 伝票が実在し、「原期限を引き継ぐ」実装と「数え直す」実装を見分けられる（完了時帰属の受注には
    // 引き継ぐ元の期限がそもそも無く、どちらの実装でも同じ値になってしまう）
    RegisteredMember wrongMember = registerAndLogin("window");
    Issued original = completedOrderWithToken("期限再計算担当-" + nonce);
    assertThat(claim(wrongMember, original.token()).getStatusCode())
        .as("前提: 誤った本人が申領できること")
        .isEqualTo(HttpStatus.OK);
    assertThat(invalidate(original.orderId(), REASON).getStatusCode())
        .as("前提: 無効化できること")
        .isEqualTo(HttpStatus.OK);

    // 原期限を「もうすぐ切れる」側へ倒す。引き継ぐ実装ならここで倒した値がそのまま新しい伝票の期限になる
    OffsetDateTime shortenedExpiry = OffsetDateTime.now().plusDays(3);
    jdbcTemplate.update(
        "update t_order_receipt_tokens set expires_at = ? where order_id = ?",
        shortenedExpiry,
        original.orderId());

    ResponseEntity<JsonNode> reissued = reissue(original.orderId());

    assertThat(reissued.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(reissued.getBody().path("receipt_token").asString()).isNotBlank();
    OrderReceiptToken token =
        orderReceiptTokenRepository.findByOrderIdOrderByIdDesc(original.orderId()).get(0);
    assertThat(token.getIssuedAt()).isAfter(OffsetDateTime.now().minusMinutes(5));
    assertThat(ChronoUnit.DAYS.between(token.getIssuedAt(), token.getExpiresAt()))
        .isEqualTo(OrderReceiptToken.VALIDITY.toDays());
    assertThat(token.getExpiresAt()).as("誤帰属の期間に食い潰された原期限を引き継がないこと").isAfter(shortenedExpiry);
  }

  @Test
  @DisplayName("再発行の付与予定額は完了時に確定した額を引き継ぎ、受注の付与実績（非会員完了では 0）を読まないこと")
  void reissueCarriesForwardThePlannedPointsFixedAtCompletion() {
    RegisteredMember wrongMember = registerAndLogin("planned");
    RegisteredMember rightMember = registerAndLogin("plannedowner");
    Issued original = completedOrderWithToken("予定額引継担当-" + nonce);
    // 発行時の確定額を現行規則では出得ない値へ倒す。会計金額から計算し直す実装ならここで倒した値が消える
    int fixedPoints = 7;
    jdbcTemplate.update(
        "update t_order_receipt_tokens set planned_points = ? where order_id = ?",
        fixedPoints,
        original.orderId());
    assertThat(claim(wrongMember, original.token()).getStatusCode())
        .as("前提: 誤った本人が申領できること")
        .isEqualTo(HttpStatus.OK);
    assertThat(invalidate(original.orderId(), REASON).getStatusCode())
        .as("前提: 無効化できること")
        .isEqualTo(HttpStatus.OK);

    String raw = reissue(original.orderId()).getBody().path("receipt_token").asString();

    assertThat(claim(rightMember, raw).getBody().path("granted_points").asInt())
        .isEqualTo(fixedPoints);
    assertThat(balanceOf(rightMember)).isEqualTo(fixedPoints);
  }

  @Test
  @DisplayName("再発行された伝票で正しい本人が申領でき、根拠 RECEIPT_TOKEN の帰属が成立すること")
  void theRightMemberReclaimsTheVisitWithTheReissuedToken() {
    RegisteredMember wrongMember = registerAndLogin("thief");
    RegisteredMember rightMember = registerAndLogin("owner");
    String castName = "取り戻し担当-" + nonce;
    String orderId = completedOrderAttributedTo(wrongMember, castName);
    assertThat(invalidate(orderId, REASON).getStatusCode())
        .as("前提: 無効化できること")
        .isEqualTo(HttpStatus.OK);

    String raw = reissue(orderId).getBody().path("receipt_token").asString();
    ResponseEntity<JsonNode> claimed = claim(rightMember, raw);

    assertThat(claimed.getStatusCode()).isEqualTo(HttpStatus.OK);
    // 付与予定額は完了時点で確定した額を引き継ぐ（訂正の早い遅いで同じ会計が別のポイントにならない）
    assertThat(claimed.getBody().path("granted_points").asInt()).isEqualTo(EXPECTED_POINTS);

    List<OrderAttribution> rows = attributionsOf(orderId);
    assertThat(rows).as("無効化した記録は残り、新しい帰属が別の行として生まれること").hasSize(2);
    OrderAttribution active =
        rows.stream()
            .filter(row -> row.getStatus() == OrderAttributionStatus.ACTIVE)
            .findFirst()
            .orElseThrow();
    assertThat(active.getSource()).isEqualTo(OrderAttributionSource.RECEIPT_TOKEN);
    assertThat(active.getMemberCode()).isEqualTo(rightMember.memberCode());

    assertThat(castNamesInVisitsOf(rightMember)).as("正しい本人の履歴に現れること").contains(castName);
    assertThat(balanceOf(rightMember)).isEqualTo(EXPECTED_POINTS);
    assertThat(castNamesInVisitsOf(wrongMember))
        .as("誤って帰属していた側からは消えたままであること")
        .doesNotContain(castName);
    // 誤帰属の付与は残ったまま。清算は手動調整で行うため、同じ受注に付与が 2 本立つ
    assertThat(ledgerRowsFor(orderId)).isEqualTo(2);
  }

  @Test
  @DisplayName("有効な帰属のある受注・帰属したことのない受注へは再発行できないこと")
  void reissueIsRefusedUnlessTheOrderWasInvalidated() {
    RegisteredMember member = registerAndLogin("guard");
    String attributedOrder = completedOrderAttributedTo(member, "帰属中担当-" + nonce);
    assertThat(reissue(attributedOrder).getStatusCode())
        .as("先に無効化していない受注へは発行しないこと")
        .isEqualTo(HttpStatus.BAD_REQUEST);

    // 完了時に会員へ達しなかった受注は、そもそも完了の応答で伝票を受け取っている。訂正の経路を発行の経路にしない
    String neverAttributed = completedOrderWithoutMember("無帰属担当-" + nonce);
    assertThat(reissue(neverAttributed).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("申領できる伝票が既にある受注へは再発行できないこと（生きた伝票が 2 本並ばない）")
  void reissueIsRefusedWhileAClaimableTokenExists() {
    RegisteredMember wrongMember = registerAndLogin("double");
    String orderId = completedOrderAttributedTo(wrongMember, "二重発行担当-" + nonce);
    assertThat(invalidate(orderId, REASON).getStatusCode())
        .as("前提: 無効化できること")
        .isEqualTo(HttpStatus.OK);
    assertThat(reissue(orderId).getStatusCode()).as("前提: 1 度目の再発行ができること").isEqualTo(HttpStatus.OK);

    assertThat(reissue(orderId).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    assertThat(tokenCountFor(orderId)).as("生きた伝票は 1 本だけであること").isEqualTo(1);
  }

  @Test
  @DisplayName("並行する再発行は一方だけが成立し、申領できる伝票が 2 本並ばないこと")
  void concurrentReissuesLeaveOnlyOneClaimableToken() throws Exception {
    // 「申領できる伝票が無い」を確かめてから発行するまでが直列化されないと、両者が同じ「無い」を観測して
    // 2 本とも発行される。そうなると後から申領した側は帰属記録の部分一意違反（500 系）で落ち、#653 が
    // 意図して作った「申領できない伝票はすべて同形のエラー」が壊れる
    RegisteredMember wrongMember = registerAndLogin("race");
    String orderId = completedOrderAttributedTo(wrongMember, "並行再発行担当-" + nonce);
    assertThat(invalidate(orderId, REASON).getStatusCode())
        .as("前提: 無効化できること")
        .isEqualTo(HttpStatus.OK);

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<ResponseEntity<JsonNode>>> races = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        races.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  go.await(10, TimeUnit.SECONDS);
                  return reissue(orderId);
                }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).as("前提: 2 つの再発行が同時に構えること").isTrue();
      go.countDown();

      List<ResponseEntity<JsonNode>> responses = new ArrayList<>();
      for (Future<ResponseEntity<JsonNode>> race : races) {
        responses.add(race.get(30, TimeUnit.SECONDS));
      }
      assertThat(responses)
          .filteredOn(response -> response.getStatusCode() == HttpStatus.OK)
          .as("成立するのは一方だけ")
          .hasSize(1);
    } finally {
      pool.shutdownNow();
    }

    assertThat(tokenCountFor(orderId)).as("発行された伝票は 1 本だけであること").isEqualTo(1);
  }

  @Test
  @DisplayName("他店舗の受注の帰属は読めず、無効化も再発行もできないこと")
  void otherStoreCannotReachTheAttribution() {
    RegisteredMember member = registerAndLogin("cross");
    String castName = "他店舗担当-" + nonce;
    String orderId = completedOrderAttributedTo(member, castName);

    assertThat(
            rest.exchange(
                    "/store/orders/" + orderId + "/attribution",
                    HttpMethod.GET,
                    new HttpEntity<>(storeHeaders(STORE_A)),
                    JsonNode.class)
                .getBody()
                .path("member_code")
                .asString())
        .as("正向対照: 自店舗では会員コードまで読めること")
        .isEqualTo(member.memberCode());

    ResponseEntity<JsonNode> leaked =
        rest.exchange(
            "/store/orders/" + orderId + "/attribution",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_B)),
            JsonNode.class);
    assertThat(leaked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(leaked.getBody().toString())
        .as("他店舗の応答が会員コードを運ばないこと")
        .doesNotContain(member.memberCode());

    ResponseEntity<JsonNode> foreignInvalidation =
        rest.exchange(
            "/store/orders/" + orderId + "/attribution/invalidation",
            HttpMethod.POST,
            new HttpEntity<>("{\"reason\": \"" + REASON + "\"}", storeHeaders(STORE_B)),
            JsonNode.class);
    assertThat(foreignInvalidation.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    ResponseEntity<JsonNode> foreignReissue =
        rest.exchange(
            "/store/orders/" + orderId + "/receipt-token",
            HttpMethod.POST,
            new HttpEntity<>(storeHeaders(STORE_B)),
            JsonNode.class);
    assertThat(foreignReissue.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    assertThat(attributionOf(orderId).getStatus()).isEqualTo(OrderAttributionStatus.ACTIVE);
    assertThat(castNamesInVisitsOf(member)).contains(castName);
  }

  // ==================== 訂正の操作 ====================

  private ResponseEntity<JsonNode> invalidate(String orderId, String reason) {
    return rest.exchange(
        "/store/orders/" + orderId + "/attribution/invalidation",
        HttpMethod.POST,
        new HttpEntity<>("{\"reason\": \"" + reason + "\"}", storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> reissue(String orderId) {
    return rest.exchange(
        "/store/orders/" + orderId + "/receipt-token",
        HttpMethod.POST,
        new HttpEntity<>(storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> claim(RegisteredMember member, String rawToken) {
    return rest.exchange(
        "/platform/me/receipts/claim",
        HttpMethod.POST,
        new HttpEntity<>("{\"token\": \"" + rawToken + "\"}", bearer(member.token())),
        JsonNode.class);
  }

  // ==================== 会員側の読み口 ====================

  private List<String> castNamesInVisitsOf(RegisteredMember member) {
    ResponseEntity<JsonNode> response =
        rest.exchange(
            "/platform/me/visits",
            HttpMethod.GET,
            new HttpEntity<>(bearer(member.token())),
            JsonNode.class);
    assertThat(response.getStatusCode()).as("前提: 来店履歴が読めること").isEqualTo(HttpStatus.OK);
    List<String> castNames = new ArrayList<>();
    for (JsonNode visit : response.getBody().path("content")) {
      castNames.add(visit.path("cast_name").asString());
    }
    return castNames;
  }

  private long balanceOf(RegisteredMember member) {
    ResponseEntity<JsonNode> response =
        rest.exchange(
            "/platform/me/points/balance",
            HttpMethod.GET,
            new HttpEntity<>(bearer(member.token())),
            JsonNode.class);
    assertThat(response.getStatusCode()).as("前提: 残高が読めること").isEqualTo(HttpStatus.OK);
    return response.getBody().path("balance").asLong();
  }

  // ==================== 実データの読み出し ====================

  private OrderAttribution attributionOf(String orderId) {
    List<OrderAttribution> found = attributionsOf(orderId);
    assertThat(found).as("受注 %s の帰属記録が 1 件だけであること", orderId).hasSize(1);
    return found.get(0);
  }

  private List<OrderAttribution> attributionsOf(String orderId) {
    return orderAttributionRepository.findAll().stream()
        .filter(row -> orderId.equals(row.getOrderId()))
        .toList();
  }

  private int ledgerRowsFor(String orderId) {
    return jdbcTemplate.queryForObject(
        "select count(*) from t_point_entries where order_id = ?", Integer.class, orderId);
  }

  private int tokenCountFor(String orderId) {
    return jdbcTemplate.queryForObject(
        "select count(*) from t_order_receipt_tokens where order_id = ?", Integer.class, orderId);
  }

  private long seedStaffId() {
    return jdbcTemplate.queryForObject(
        "select id from t_users where email = ?", Long.class, "yamada.jiro@kizuna.test");
  }

  // ==================== 受注の用意 ====================

  /** 会員へ帰属した完了を 1 件作る（顧客 → 有効な関連 → 会員の一本道を通す）。 */
  private String completedOrderAttributedTo(RegisteredMember member, String castName) {
    String customerId = createCustomer(castName);
    linkMember(customerId, member.memberCode());
    String orderId = confirmedOrder(customerId, castName);
    assertThat(complete(orderId).getStatusCode()).as("前提: 受注を完了できること").isEqualTo(HttpStatus.OK);
    assertThat(attributionOf(orderId).getSource()).isEqualTo(OrderAttributionSource.COMPLETION);
    return orderId;
  }

  /** 会員へ帰属しない完了を 1 件作る（完了の応答が伝票を運ぶ枝）。 */
  private String completedOrderWithoutMember(String castName) {
    String orderId = confirmedOrder(null, castName);
    assertThat(complete(orderId).getStatusCode()).as("前提: 受注を完了できること").isEqualTo(HttpStatus.OK);
    return orderId;
  }

  /** 発行された伝票トークンとその受注。 */
  private record Issued(String orderId, String token) {}

  /** 会員へ帰属しない完了を 1 件作り、発行された生値を受け取る。 */
  private Issued completedOrderWithToken(String castName) {
    String orderId = confirmedOrder(null, castName);
    ResponseEntity<JsonNode> completed = complete(orderId);
    assertThat(completed.getStatusCode()).as("前提: 受注を完了できること").isEqualTo(HttpStatus.OK);
    String raw = completed.getBody().path("receipt_token").asString();
    assertThat(raw).as("前提: 完了応答が伝票トークンを運ぶこと").isNotBlank();
    return new Issued(orderId, raw);
  }

  private String confirmedOrder(String customerId, String castName) {
    String castId = createCast(castName);
    String orderId = createOrder(castId, customerId, castName);
    assertThat(updateStatus(orderId, castId, "CONFIRMED")).as("前提: 受注を確定できること").isTrue();
    return orderId;
  }

  private ResponseEntity<JsonNode> complete(String orderId) {
    return rest.exchange(
        "/store/orders/" + orderId + "/completion",
        HttpMethod.POST,
        new HttpEntity<>("{\"total_fee\": " + TOTAL_FEE + "}", storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private String createOrder(String castId, String customerId, String remarks) {
    String body =
        "{\"receptionist_id\": "
            + SEED_RECEPTIONIST_ID
            + ", \"business_date\": \""
            + LocalDate.now()
            + "\", \"cast_id\": \""
            + castId
            + "\", \"pax\": 2"
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

  private boolean updateStatus(String orderId, String castId, String status) {
    String body =
        "{\"receptionist_id\": "
            + SEED_RECEPTIONIST_ID
            + ", \"cast_id\": \""
            + castId
            + "\", \"status\": \""
            + status
            + "\"}";
    return rest.exchange(
            "/store/orders/" + orderId,
            HttpMethod.PUT,
            new HttpEntity<>(body, storeHeaders(STORE_A)),
            JsonNode.class)
        .getStatusCode()
        .is2xxSuccessful();
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

  private String createCustomer(String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 顧客作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
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

  // ==================== 会員 ====================

  /** 訂正の前後を会員側の読み口で確かめるための本人確認材料。 */
  private record RegisteredMember(long id, String memberCode, String token) {}

  /**
   * 会員を 1 人作ってログインまで済ませる。
   *
   * <p>{@code prefix} は同じテスト内での呼び分け。email の local 部は 64 文字までなので、区別に要る分より長い接頭辞を 足さない（超えると登録が 400
   * になり、前提の失敗として現れる）。
   */
  private RegisteredMember registerAndLogin(String prefix) {
    String email = prefix + "-attr-inv-" + nonce + "@kizuna.test";
    ResponseEntity<JsonNode> registration =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \""
                    + email
                    + "\", \"password\": \""
                    + PASSWORD
                    + "\", \"display_name\": \"帰属訂正検証会員\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(registration.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);
    String memberCode = registration.getBody().path("member_code").asString();
    long registeredId =
        memberRepository.findByMemberCode(memberCode).map(Member::getId).orElseThrow();

    ResponseEntity<JsonNode> login =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(login.getStatusCode()).as("前提: 会員としてログインできること").isEqualTo(HttpStatus.OK);
    return new RegisteredMember(registeredId, memberCode, login.getBody().path("token").asString());
  }

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private static HttpHeaders bearer(String bearerToken) {
    HttpHeaders headers = jsonHeaders();
    headers.setBearerAuth(bearerToken);
    return headers;
  }
}
