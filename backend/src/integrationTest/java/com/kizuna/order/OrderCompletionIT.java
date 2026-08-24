package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
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
 * 受注完了処理を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>固定するのは「ポイントが台帳へ入る経路は完了処理ただ一つ」という不変条件と、その周りの拒否 —
 * 二重完了・残高超過・単位外・非会員の利用・汎用更新からの完了遷移。加えて、会員はプラットフォーム級の身分なので A 店で得たポイントを B
 * 店の会計で使えることを跨店で確かめる（台帳は店舗で分割されない）。
 *
 * <p>シード設定は「100 円ごとに 1 ポイント付与、利用は 100 ポイント単位」。
 */
class OrderCompletionIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  /** demo シード（seed/05-demo.yaml）の山田次郎（STORE_STAFF・授権店舗 = 店舗1）。店舗A の受付担当として使用。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  /** 会計金額 12000 円 → シード設定で 120 ポイント付与。 */
  private static final int TOTAL_FEE = 12000;

  private static final int EXPECTED_GRANT = 120;

  @Autowired private PointEntryRepository pointEntryRepository;
  @Autowired private MemberRepository memberRepository;

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("会員の受注を完了すると付与が台帳へ積まれ、二度目の完了は撥ねられること")
  void completionGrantsOnceAndRefusesToRepeat() {
    String memberCode = registerMember("grant");
    String orderId = confirmedMemberOrder(STORE_A, token, SEED_RECEPTIONIST_ID, memberCode, "付与");

    ResponseEntity<JsonNode> completed = complete(STORE_A, token, orderId, TOTAL_FEE, null);

    assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
    // 完了の応答は伝票トークン専用の型。会計とポイントの結果は詳細の読み口で確かめる
    JsonNode detail = orderJson(STORE_A, token, orderId);
    assertThat(detail.path("status").asString()).isEqualTo("COMPLETED");
    assertThat(detail.path("total_fee").asInt()).isEqualTo(TOTAL_FEE);
    assertThat(detail.path("auto_grant_points").asInt()).isEqualTo(EXPECTED_GRANT);

    long memberId = memberIdOf(memberCode);
    List<PointEntry> credits = pointEntryRepository.findCredits(memberId);
    assertThat(credits).hasSize(1);
    assertThat(credits.get(0).getAmount()).isEqualTo(EXPECTED_GRANT);
    assertThat(credits.get(0).getOrderId()).isEqualTo(orderId);
    assertThat(credits.get(0).getOriginatingStoreId()).isEqualTo(STORE_A);

    // 二度目の完了は撥ねる。通ると同じ受注で付与が二重に記帳される
    ResponseEntity<JsonNode> again = complete(STORE_A, token, orderId, TOTAL_FEE, null);
    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(pointEntryRepository.findCredits(memberId)).as("撥ねた完了は台帳へ何も積まないこと").hasSize(1);
  }

  @Test
  @DisplayName("A 店で得たポイントを B 店の会計で使えること（台帳は店舗で分割されない）")
  void pointsEarnedInOneStoreAreSpendableInAnother() {
    String managerToken = loginAs("tanaka.hanako@kizuna.test", "pass");
    long receptionistInB = firstReceptionistId(STORE_B, managerToken);
    String memberCode = registerMember("cross");

    String earned = confirmedMemberOrder(STORE_A, token, SEED_RECEPTIONIST_ID, memberCode, "跨店・獲得");
    assertThat(complete(STORE_A, token, earned, TOTAL_FEE, null).getStatusCode())
        .as("前提: A 店の完了で付与されること")
        .isEqualTo(HttpStatus.OK);

    String spent =
        confirmedMemberOrder(STORE_B, managerToken, receptionistInB, memberCode, "跨店・利用");
    ResponseEntity<JsonNode> completed = complete(STORE_B, managerToken, spent, TOTAL_FEE, 100);

    assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode spentDetail = orderJson(STORE_B, managerToken, spent);
    assertThat(spentDetail.path("used_points").asInt()).isEqualTo(100);
    assertThat(spentDetail.path("auto_grant_points").asInt()).isEqualTo(EXPECTED_GRANT);
    // 利用は台帳の減算仕訳と対で明細へも入る。合計はそのぶん下がり、内訳の和と一致し続ける
    assertThat(spentDetail.path("total_fee").asInt()).isEqualTo(TOTAL_FEE - 100);
    JsonNode redemption = spentDetail.path("fee_lines").get(1);
    assertThat(redemption.path("kind").asString()).isEqualTo("POINT_REDEMPTION");
    assertThat(redemption.path("amount").asInt()).as("減項は正値で返ること").isEqualTo(100);
    assertThat(redemption.path("system_owned").asBoolean()).isTrue();

    long memberId = memberIdOf(memberCode);
    PointEntry usage = usageEntryOf(memberId);
    assertThat(usage.getAmount()).isEqualTo(-100);
    assertThat(usage.getOrderId()).isEqualTo(spent);
    // 帰属は利用した店舗。付与した店舗ではない
    assertThat(usage.getOriginatingStoreId()).isEqualTo(STORE_B);
  }

  @Test
  @DisplayName("残高を超える利用と単位外の利用が撥ねられ、受注も確定済みのまま残ること")
  void usageBeyondBalanceOrOffUnitIsRejected() {
    String memberCode = registerMember("reject");
    String orderId = confirmedMemberOrder(STORE_A, token, SEED_RECEPTIONIST_ID, memberCode, "利用拒否");

    // 残高 0 の会員に 100 ポイントの利用
    assertThat(complete(STORE_A, token, orderId, TOTAL_FEE, 100).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    // 単位（100）の倍数でない利用
    assertThat(complete(STORE_A, token, orderId, TOTAL_FEE, 150).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    assertThat(statusOf(STORE_A, token, orderId)).as("撥ねた完了は受注を進めないこと").isEqualTo("CONFIRMED");
    assertThat(pointEntryRepository.findCredits(memberIdOf(memberCode)))
        .as("撥ねた完了は付与も記帳しないこと")
        .isEmpty();

    // 正向対照: 利用を指定しなければ同じ受注が完了する（負向 400 が経路の不在でない証明）
    assertThat(complete(STORE_A, token, orderId, TOTAL_FEE, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("会計金額を超える利用が撥ねられ、同額の利用は通ること")
  void usageBeyondTheTotalFeeIsRejected() {
    String memberCode = registerMember("overfee");
    String customerId = createCustomer("会計超過");
    linkMember(STORE_A, token, customerId, memberCode);
    String earning = confirmedOrder(STORE_A, token, SEED_RECEPTIONIST_ID, customerId, "会計超過・獲得");
    assertThat(complete(STORE_A, token, earning, TOTAL_FEE, null).getStatusCode())
        .as("前提: 利用に足りる残高（%d ポイント）を用意できること", EXPECTED_GRANT)
        .isEqualTo(HttpStatus.OK);

    // 残高（120）は足りているので、撥ねる理由は会計金額（50 円）を超える利用であること
    String spending = confirmedOrder(STORE_A, token, SEED_RECEPTIONIST_ID, customerId, "会計超過・利用");
    assertThat(complete(STORE_A, token, spending, 50, 100).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(statusOf(STORE_A, token, spending)).as("撥ねた完了は受注を進めないこと").isEqualTo("CONFIRMED");

    // 正向対照: 同額なら全額のポイント払いとして通る（負向 400 が経路の不在でない証明）
    ResponseEntity<JsonNode> completed = complete(STORE_A, token, spending, 100, 100);
    assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(orderJson(STORE_A, token, spending).path("used_points").asInt()).isEqualTo(100);
  }

  @Test
  @DisplayName("非会員の受注はポイントを利用できず、付与 0 で完了すること")
  void nonMemberOrderCannotUsePointsAndGrantsNothing() {
    String orderId =
        confirmedOrder(STORE_A, token, SEED_RECEPTIONIST_ID, createCustomer("非会員"), "非会員");

    assertThat(complete(STORE_A, token, orderId, TOTAL_FEE, 100).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    ResponseEntity<JsonNode> completed = complete(STORE_A, token, orderId, TOTAL_FEE, null);
    assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(completed.getBody().path("auto_grant_points").asInt()).isZero();
    assertThat(completed.getBody().path("used_points").asInt()).isZero();
  }

  @Test
  @DisplayName("事前計算は紐づけの有無で残高の有無が変わること")
  void previewCarriesTheBalanceOnlyForLinkedOrders() {
    String memberCode = registerMember("preview");
    String linkedOrder =
        confirmedMemberOrder(STORE_A, token, SEED_RECEPTIONIST_ID, memberCode, "事前計算・会員");

    ResponseEntity<JsonNode> linked = preview(STORE_A, token, linkedOrder, TOTAL_FEE);
    assertThat(linked.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(linked.getBody().path("member_linked").asBoolean()).isTrue();
    assertThat(linked.getBody().path("point_balance").asInt()).isZero();
    assertThat(linked.getBody().path("usage_unit").asInt()).isEqualTo(100);
    assertThat(linked.getBody().path("grant_points").asInt()).isEqualTo(EXPECTED_GRANT);

    String unlinkedOrder =
        confirmedOrder(STORE_A, token, SEED_RECEPTIONIST_ID, createCustomer("事前計算・非会員"), "事前計算");
    ResponseEntity<JsonNode> unlinked = preview(STORE_A, token, unlinkedOrder, TOTAL_FEE);
    assertThat(unlinked.getBody().path("member_linked").asBoolean()).isFalse();
    // 応答は non_null 包含のため、残高の無い受注では項目ごと落ちる
    assertThat(unlinked.getBody().hasNonNull("point_balance")).isFalse();
    // 確定は非会員へ付与しないので、見込みの付与も 0（食い違うと画面が出した予定が嘘になる）
    assertThat(unlinked.getBody().path("grant_points").asInt()).isZero();
  }

  // ==================== 受注の用意 ====================

  /** 会員に紐づいた顧客を持つ確定済みの受注。 */
  private String confirmedMemberOrder(
      long storeId, String bearerToken, long receptionistId, String memberCode, String label) {
    String customerId = createCustomerAs(storeId, bearerToken, label + "-" + nonce);
    linkMember(storeId, bearerToken, customerId, memberCode);
    return confirmedOrder(storeId, bearerToken, receptionistId, customerId, label);
  }

  /** 店舗が起こす受注は確定で出生するため、作成だけで完了の前提が揃う。 */
  private String confirmedOrder(
      long storeId, String bearerToken, long receptionistId, String customerId, String label) {
    String castId = createCast(storeId, bearerToken, label + "-" + nonce);
    return createOrder(storeId, bearerToken, receptionistId, castId, customerId, label);
  }

  private String createOrder(
      long storeId,
      String bearerToken,
      long receptionistId,
      String castId,
      String customerId,
      String remarks) {
    String body =
        "{\"receptionist_id\": "
            + receptionistId
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
            "/store/orders",
            new HttpEntity<>(body, headersFor(storeId, bearerToken)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful())
        .as("前提: store %d での受注作成が成功すること", storeId)
        .isTrue();
    return created.getBody().path("id").asString();
  }

  private String createCast(long storeId, String bearerToken, String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", headersFor(storeId, bearerToken)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful())
        .as("前提: store %d でのキャスト作成が成功すること", storeId)
        .isTrue();
    return created.getBody().path("id").asString();
  }

  private String createCustomer(String name) {
    return createCustomerAs(STORE_A, token, name + "-" + nonce);
  }

  private String createCustomerAs(long storeId, String bearerToken, String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", headersFor(storeId, bearerToken)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful())
        .as("前提: store %d での顧客作成が成功すること", storeId)
        .isTrue();
    return created.getBody().path("id").asString();
  }

  // ==================== 完了処理の呼出 ====================

  private ResponseEntity<JsonNode> complete(
      long storeId, String bearerToken, String orderId, int totalFee, Integer usePoints) {
    String body =
        "{\"expected_version\":"
            + orderVersion(headersFor(storeId, bearerToken), orderId)
            + ",\"fee_lines\":[{\"kind\":\"SURCHARGE\",\"name\":\"会計\",\"amount\":"
            + totalFee
            + "}]"
            + (usePoints == null ? "" : ", \"use_points\": " + usePoints)
            + "}";
    return rest.exchange(
        "/store/orders/" + orderId + "/completion",
        HttpMethod.POST,
        new HttpEntity<>(body, headersFor(storeId, bearerToken)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> preview(
      long storeId, String bearerToken, String orderId, int totalFee) {
    return rest.exchange(
        "/store/orders/" + orderId + "/completion-preview?total_fee=" + totalFee,
        HttpMethod.GET,
        new HttpEntity<>(headersFor(storeId, bearerToken)),
        JsonNode.class);
  }

  private String statusOf(long storeId, String bearerToken, String orderId) {
    return orderJson(storeId, bearerToken, orderId).path("status").asString();
  }

  /** 受注 1 件の詳細。完了の応答が伝票トークンだけになったので、会計の結果はここから読む。 */
  private JsonNode orderJson(long storeId, String bearerToken, String orderId) {
    return rest.exchange(
            "/store/orders/" + orderId,
            HttpMethod.GET,
            new HttpEntity<>(headersFor(storeId, bearerToken)),
            JsonNode.class)
        .getBody();
  }

  // ==================== 会員・台帳 ====================

  /** 新しい会員を登録してその会員コードを返す。 */
  private String registerMember(String prefix) {
    String email = prefix + "-completion-it-" + nonce + "-" + System.nanoTime() + "@kizuna.test";
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
                    + "\", \"display_name\": \"完了処理検証会員\"}",
                headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);
    return res.getBody().path("member_code").asString();
  }

  private void linkMember(long storeId, String bearerToken, String customerId, String memberCode) {
    ResponseEntity<JsonNode> linked =
        rest.exchange(
            "/store/customers/" + customerId + "/member-link",
            HttpMethod.POST,
            new HttpEntity<>(
                "{\"member_code\": \"" + memberCode + "\"}", headersFor(storeId, bearerToken)),
            JsonNode.class);
    assertThat(linked.getStatusCode()).as("前提: 会員の紐づけが成功すること").isEqualTo(HttpStatus.OK);
  }

  private long memberIdOf(String memberCode) {
    return memberRepository.findByMemberCode(memberCode).map(Member::getId).orElseThrow();
  }

  /** 会員の減算仕訳（利用）を 1 件だけ引く。 */
  private PointEntry usageEntryOf(long memberId) {
    List<PointEntry> debits =
        pointEntryRepository.findAll().stream()
            .filter(entry -> entry.getMemberId() == memberId && entry.getAmount() < 0)
            .toList();
    assertThat(debits).as("利用の仕訳が 1 件だけ積まれること").hasSize(1);
    return debits.get(0);
  }

  // ==================== 認証 ====================

  /** 店舗B の受付候補。授権店舗が固定の id に依存しないよう、読み口が返す候補から採る。 */
  private long firstReceptionistId(long storeId, String bearerToken) {
    ResponseEntity<JsonNode> candidates =
        rest.exchange(
            "/store/orders/receptionists",
            HttpMethod.GET,
            new HttpEntity<>(headersFor(storeId, bearerToken)),
            JsonNode.class);
    assertThat(candidates.getStatusCode())
        .as("前提: store %d の受付候補を読めること", storeId)
        .isEqualTo(HttpStatus.OK);
    assertThat(candidates.getBody().isEmpty()).as("前提: store %d に受付候補がいること", storeId).isFalse();
    return candidates.getBody().get(0).path("id").asLong();
  }

  private String loginAs(String email, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}", headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: %s でのログインが成功すること", email).isEqualTo(HttpStatus.OK);
    return res.getBody().path("token").asString();
  }

  private static HttpHeaders headersFor(long storeId, String bearerToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(storeId));
    headers.setBearerAuth(bearerToken);
    return headers;
  }
}
