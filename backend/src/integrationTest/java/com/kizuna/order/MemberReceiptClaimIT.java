package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderAttributionSource;
import com.kizuna.order.domain.OrderAttributionStatus;
import com.kizuna.order.domain.OrderReceiptTokenStatus;
import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
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
 * 伝票トークンの申領を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>固定するのは 3 つ。①申領の効果は帰属記録（根拠 RECEIPT_TOKEN）と<b>発行時に確定した固定額</b>の記帳に閉じ、店舗台帳
 * （顧客行・行級の関連）へは波及しないこと。②申領できないトークン（不在・期限切れ・使用済み・並行申領の敗者）が すべて<b>同形のエラー</b>で返り、応答から受注の存在を辿れないこと。
 * ③トークンの状態遷移そのものが再送を遮断すること（冪等キーを持たない — ADR 0007）。
 *
 * <p>シード設定は「100 円ごとに 1 ポイント付与、利用は 100 ポイント単位」。
 */
class MemberReceiptClaimIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  /** v0.5.0 の山田次郎シード（STORE_STAFF・授権店舗 = 店舗1）。店舗A の受付担当として使用。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  private static final int TOTAL_FEE = 12000;

  /** シード設定（100 円ごとに 1 ポイント）を {@link #TOTAL_FEE} に当てた付与額。 */
  private static final int EXPECTED_PLANNED_POINTS = 120;

  @Autowired private OrderAttributionRepository orderAttributionRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final long nonce = System.nanoTime();

  private RegisteredMember member;

  @BeforeEach
  void registerClaimingMember() {
    member = registerAndLogin("claim");
  }

  @Test
  @DisplayName("有効なトークンの申領で帰属記録と固定額の付与が成立し、残高と来店履歴に現れること")
  void claimingAValidTokenAttributesTheVisitAndBooksThePlannedPoints() {
    String castName = "申領担当-" + nonce;
    Issued issued = completedOrderWithToken(null, castName, TOTAL_FEE);

    ResponseEntity<JsonNode> claimed = claim(member, issued.token());

    assertThat(claimed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(claimed.getBody().path("granted_points").asInt()).isEqualTo(EXPECTED_PLANNED_POINTS);

    OrderAttribution attribution = attributionOf(issued.orderId());
    assertThat(attribution.getSource()).isEqualTo(OrderAttributionSource.RECEIPT_TOKEN);
    assertThat(attribution.getStatus()).isEqualTo(OrderAttributionStatus.ACTIVE);
    assertThat(attribution.getMemberId()).isEqualTo(member.id());
    assertThat(attribution.getMemberCode()).isEqualTo(member.memberCode());
    assertThat(tokenStatusOf(issued.orderId())).isEqualTo(OrderReceiptTokenStatus.CLAIMED.name());

    // 会員から見える形（読み口）でも成立していること。記録だけあって画面に出ないのでは申領の意味が無い
    JsonNode visit = visits().path("content").path(0);
    assertThat(visit.path("cast_name").asString()).isEqualTo(castName);
    assertThat(visit.path("granted_points").asInt()).isEqualTo(EXPECTED_PLANNED_POINTS);
    assertThat(balance()).isEqualTo(EXPECTED_PLANNED_POINTS);
  }

  @Test
  @DisplayName("記帳するのは発行時に確定した付与予定額であり、会計金額から計算し直さないこと")
  void claimBooksThePlannedAmountFixedAtIssuance() {
    // 申領時点の付与設定を読むと、同じ会計が申領の早い遅いで別のポイントになる（ADR 0008）。
    // 設定を動かす代わりに、発行済みの予定額そのものを現行規則では出得ない値へ倒して見分ける
    Issued issued = completedOrderWithToken(null, "固定額担当-" + nonce, TOTAL_FEE);
    int fixedPoints = 7;
    jdbcTemplate.update(
        "update t_order_receipt_tokens set planned_points = ? where order_id = ?",
        fixedPoints,
        issued.orderId());

    ResponseEntity<JsonNode> claimed = claim(member, issued.token());

    assertThat(claimed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(claimed.getBody().path("granted_points").asInt()).isEqualTo(fixedPoints);
    assertThat(balance()).as("台帳へ入るのも発行時の固定額であること").isEqualTo(fixedPoints);
  }

  @Test
  @DisplayName("付与予定額 0 の申領は来店として見えるだけで、台帳に行を書かないこと")
  void claimingAZeroFeeReceiptOnlyMakesTheVisitVisible() {
    String castName = "0円申領担当-" + nonce;
    Issued issued = completedOrderWithToken(null, castName, 0);

    ResponseEntity<JsonNode> claimed = claim(member, issued.token());

    assertThat(claimed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(claimed.getBody().path("granted_points").asInt()).isZero();
    assertThat(ledgerRowsFor(issued.orderId())).as("台帳に仕訳を書かないこと").isZero();
    // 帰属は付与の有無と独立している。来店としては見えなければならない
    assertThat(visits().path("content").path(0).path("cast_name").asString()).isEqualTo(castName);
  }

  @Test
  @DisplayName("申領が店舗の顧客台帳（顧客行・行級の関連）へ波及しないこと")
  void claimNeverTouchesTheStoreCustomerLedger() {
    // 申領が証明するのは受注 1 件の帰属だけで、その店舗と会員の継続的な関係ではない（ADR 0008）。
    // 顧客に着いていながら会員へ達しなかった受注が、波及の起こりうる形
    String customerId = createCustomer("非会員のまま-" + nonce);
    Issued issued = completedOrderWithToken(customerId, "無波及担当-" + nonce, TOTAL_FEE);
    int customersBefore = customerCountAtStoreA();

    assertThat(claim(member, issued.token()).getStatusCode()).isEqualTo(HttpStatus.OK);

    // 正向対照: 申領そのものは確かに成立している（下の不在の断言が空振りでないことの証明）
    assertThat(attributionOf(issued.orderId()).getSource())
        .isEqualTo(OrderAttributionSource.RECEIPT_TOKEN);
    assertThat(linkRowsFor(member.id())).as("会員と顧客の関連を作らないこと").isZero();
    assertThat(customerCountAtStoreA()).as("台帳に顧客行を増やさないこと").isEqualTo(customersBefore);
    assertThat(activeLinkCodesOfCustomer(customerId)).as("既存の顧客行へ会員を結び付けないこと").isEmpty();
  }

  @Test
  @DisplayName("不在・壊れた値・期限切れ・使用済み・失効済みのトークンが同形のエラーで返ること")
  void everyUnusableTokenFailsWithTheSameShape() {
    // 応答を撃ち分けると、受注の存在と完了状態を応答の違いから辿れてしまう
    Issued expired = completedOrderWithToken(null, "期限切れ担当-" + nonce, TOTAL_FEE);
    jdbcTemplate.update(
        "update t_order_receipt_tokens set expires_at = now() - interval '1 day' where order_id = ?",
        expired.orderId());
    // 再発行が前の 1 本を殺した後の状態。訂正の途中で古い QR を持っている客がここへ来る
    Issued revoked = completedOrderWithToken(null, "失効済み担当-" + nonce, TOTAL_FEE);
    jdbcTemplate.update(
        "update t_order_receipt_tokens set status = ? where order_id = ?",
        OrderReceiptTokenStatus.REVOKED.name(),
        revoked.orderId());
    Issued used = completedOrderWithToken(null, "使用済み担当-" + nonce, TOTAL_FEE);
    assertThat(claim(member, used.token()).getStatusCode())
        .as("前提: 1 度目は申領できること")
        .isEqualTo(HttpStatus.OK);

    ResponseEntity<String> unknown = claimRaw(member, "存在しない伝票-" + nonce);
    ResponseEntity<String> malformed = claimRaw(member, "%%壊れた値%%");
    ResponseEntity<String> expiredResponse = claimRaw(member, expired.token());
    ResponseEntity<String> revokedResponse = claimRaw(member, revoked.token());
    ResponseEntity<String> usedResponse = claimRaw(member, used.token());

    assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(List.of(malformed, expiredResponse, revokedResponse, usedResponse))
        .as("状態も本文も不在のトークンと区別が付かないこと")
        .allSatisfy(
            response -> {
              assertThat(response.getStatusCode()).isEqualTo(unknown.getStatusCode());
              assertThat(response.getBody()).isEqualTo(unknown.getBody());
            });
    // 期限切れのトークンは申領されないまま残る（同形のエラーは「静かに成立していた」ではない）
    assertThat(tokenStatusOf(expired.orderId())).isEqualTo(OrderReceiptTokenStatus.ISSUED.name());
  }

  @Test
  @DisplayName("二重申領は一度しか成立せず、付与も帰属も 1 件だけであること")
  void claimingTwiceBooksOnlyOnce() {
    // 再送の遮断は冪等キーではなく前提状態（未申領）の消滅が担う（ADR 0007 の判定基準）
    Issued issued = completedOrderWithToken(null, "二重申領担当-" + nonce, TOTAL_FEE);
    assertThat(claim(member, issued.token()).getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(claimRaw(member, issued.token()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    assertThat(attributionsOf(issued.orderId())).hasSize(1);
    assertThat(ledgerRowsFor(issued.orderId())).isEqualTo(1);
    assertThat(balance()).isEqualTo(EXPECTED_PLANNED_POINTS);
  }

  @Test
  @DisplayName("並行申領は一方だけが成立し、敗者も同形のエラーへ落ちること")
  void concurrentClaimsConvergeOnASingleWinner() throws Exception {
    // 敗者が帰属記録の部分一意違反（500 系）で落ちると、応答の違いから受注の存在が漏れる。
    // 収束はトークン行のロックが担い、敗者の読みは勝者の確定後に「使用済み」を観測する
    Issued issued = completedOrderWithToken(null, "並行申領担当-" + nonce, TOTAL_FEE);
    String uniformBody = claimRaw(member, "存在しない伝票-並行-" + nonce).getBody();

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<ResponseEntity<String>>> races = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        races.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  go.await(10, TimeUnit.SECONDS);
                  return claimRaw(member, issued.token());
                }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).as("前提: 2 つの申領が同時に構えること").isTrue();
      go.countDown();

      List<ResponseEntity<String>> responses = new ArrayList<>();
      for (Future<ResponseEntity<String>> race : races) {
        responses.add(race.get(30, TimeUnit.SECONDS));
      }
      assertThat(responses)
          .filteredOn(response -> response.getStatusCode() == HttpStatus.OK)
          .as("成立するのは一方だけ")
          .hasSize(1);
      assertThat(responses)
          .filteredOn(response -> response.getStatusCode() != HttpStatus.OK)
          .as("敗者は不在のトークンと同形で返ること")
          .singleElement()
          .satisfies(
              response -> {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(response.getBody()).isEqualTo(uniformBody);
              });
    } finally {
      pool.shutdownNow();
    }

    assertThat(attributionsOf(issued.orderId())).hasSize(1);
    assertThat(ledgerRowsFor(issued.orderId())).isEqualTo(1);
  }

  @Test
  @DisplayName("会員以外の認証主体は申領できないこと")
  void staffCannotClaimAReceipt() {
    // 帰属先は認証主体本人に固定される。店舗スタッフが申領できると、伝票の所持だけで他人の来店を作れる
    Issued issued = completedOrderWithToken(null, "権限担当-" + nonce, TOTAL_FEE);

    HttpHeaders headers = bearer(token);
    ResponseEntity<String> forbidden =
        rest.postForEntity(
            "/platform/me/receipts/claim",
            new HttpEntity<>(claimBody(issued.token()), headers),
            String.class);

    assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(attributionsOf(issued.orderId())).isEmpty();
  }

  // ==================== 申領 ====================

  private ResponseEntity<JsonNode> claim(RegisteredMember as, String rawToken) {
    return rest.postForEntity(
        "/platform/me/receipts/claim",
        new HttpEntity<>(claimBody(rawToken), bearer(as.token())),
        JsonNode.class);
  }

  /** 応答の形そのものを比べるため、本文を解釈せず生文字列で受ける。 */
  private ResponseEntity<String> claimRaw(RegisteredMember as, String rawToken) {
    return rest.postForEntity(
        "/platform/me/receipts/claim",
        new HttpEntity<>(claimBody(rawToken), bearer(as.token())),
        String.class);
  }

  private static String claimBody(String rawToken) {
    return "{\"token\": \"" + rawToken.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
  }

  // ==================== 会員側の読み口 ====================

  private JsonNode visits() {
    ResponseEntity<JsonNode> response =
        rest.exchange(
            "/platform/me/visits",
            HttpMethod.GET,
            new HttpEntity<>(bearer(member.token())),
            JsonNode.class);
    assertThat(response.getStatusCode()).as("前提: 来店履歴が読めること").isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  private long balance() {
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

  private String tokenStatusOf(String orderId) {
    return jdbcTemplate.queryForObject(
        "select status from t_order_receipt_tokens where order_id = ?", String.class, orderId);
  }

  private int ledgerRowsFor(String orderId) {
    return jdbcTemplate.queryForObject(
        "select count(*) from t_point_entries where order_id = ?", Integer.class, orderId);
  }

  private int linkRowsFor(long memberId) {
    return jdbcTemplate.queryForObject(
        "select count(*) from t_customer_member_links where member_id = ?",
        Integer.class,
        memberId);
  }

  private int customerCountAtStoreA() {
    return jdbcTemplate.queryForObject(
        "select count(*) from t_customers where store_id = ?", Integer.class, STORE_A);
  }

  private List<String> activeLinkCodesOfCustomer(String customerId) {
    return jdbcTemplate.queryForList(
        "select member_code from t_customer_member_links where customer_id = ? and status = 'ACTIVE'",
        String.class,
        customerId);
  }

  // ==================== 受注の用意 ====================

  /** 発行された伝票トークンとその受注。 */
  private record Issued(String orderId, String token) {}

  /** 会員へ帰属しない完了を 1 件作り、発行された生値を受け取る。 */
  private Issued completedOrderWithToken(String customerId, String castName, int totalFee) {
    String castId = createCast(castName);
    String orderId = createOrder(castId, customerId, castName);
    assertThat(updateStatus(orderId, castId, "CONFIRMED")).as("前提: 受注を確定できること").isTrue();
    ResponseEntity<JsonNode> completed =
        rest.exchange(
            "/store/orders/" + orderId + "/completion",
            HttpMethod.POST,
            new HttpEntity<>("{\"total_fee\": " + totalFee + "}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(completed.getStatusCode()).as("前提: 受注を完了できること").isEqualTo(HttpStatus.OK);
    String raw = completed.getBody().path("receipt_token").asString();
    assertThat(raw).as("前提: 完了応答が伝票トークンを運ぶこと").isNotBlank();
    return new Issued(orderId, raw);
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

  // ==================== 会員 ====================

  /** 申領する会員の本人確認材料。 */
  private record RegisteredMember(long id, String memberCode, String token) {}

  private RegisteredMember registerAndLogin(String prefix) {
    String email = prefix + "-receipt-claim-it-" + nonce + "-" + System.nanoTime() + "@kizuna.test";
    ResponseEntity<JsonNode> registration =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \""
                    + email
                    + "\", \"password\": \""
                    + PASSWORD
                    + "\", \"display_name\": \"伝票申領検証会員\"}",
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
