package com.kizuna.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRank;
import com.kizuna.member.domain.MemberRankHistory;
import com.kizuna.member.domain.MemberRankHistoryRepository;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.settings.domain.SystemConfig;
import com.kizuna.settings.domain.SystemConfigRepository;
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
 * 会員ランクの昇格を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>固定するのは 4 つ。①判定が付与の記帳と同期して走ること（受注完了・伝票トークンの事後申領の両経路）。②昇格条件が OR で、
 * 完了受注の回数と付与の純額のどちらか一方の達成で上がること。③指標が取消仕訳の控除後の純額であり、それでもランクは降格しないこと（棘輪）。 ④閾値が SystemConfig
 * から読まれ、変更が次回の判定へ反映されること。
 *
 * <p>シード設定は「100 円ごとに 1 ポイント付与」、閾値は「SILVER = 5 回 or 5,000pt / GOLD = 20 回 or 20,000pt」。
 */
class MemberRankIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  /** demo シード（seed/05-demo.yaml）の山田次郎（STORE_STAFF・授権店舗 = 店舗1）。店舗A の受付担当として使用。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  /** 種子閾値の SILVER 側。回数条件は 5 回、純額条件は 5,000pt。 */
  private static final int SILVER_VISIT_COUNT = 5;

  /** シード設定（100 円 = 1pt）でちょうど 5,000pt になる会計金額。 */
  private static final int FEE_FOR_SILVER_POINTS = 500_000;

  /** 回数だけで上げるための最小会計。付与は 1pt しか積まれないので純額条件には遠く届かない。 */
  private static final int TINY_FEE = 100;

  @Autowired private MemberRepository memberRepository;
  @Autowired private MemberRankHistoryRepository memberRankHistoryRepository;
  @Autowired private PointEntryRepository pointEntryRepository;
  @Autowired private PointLedgerService pointLedgerService;
  @Autowired private SystemConfigRepository systemConfigRepository;

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("新規会員は BRONZE から始まり、閾値に届かないうちは上がらないこと")
  void newMemberStartsAtBronzeAndStaysUntilAThresholdIsMet() {
    RegisteredMember member = registerAndLogin("bronze");
    assertThat(rankOf(member)).isEqualTo(MemberRank.BRONZE);

    String customerId = linkedCustomer(member, "初期ランク");
    for (int i = 0; i < SILVER_VISIT_COUNT - 1; i++) {
      completeLinkedOrder(customerId, "初期ランク" + i, TINY_FEE);
    }

    assertThat(rankOf(member)).as("回数も純額も閾値に届かなければ上がらないこと").isEqualTo(MemberRank.BRONZE);
    assertThat(memberRankHistoryRepository.findByMemberIdOrderByIdAsc(member.id())).isEmpty();
  }

  @Test
  @DisplayName("完了受注の回数だけで昇格すること（純額は閾値に遠く届かない）")
  void promotesOnVisitCountAlone() {
    RegisteredMember member = registerAndLogin("visits");
    String customerId = linkedCustomer(member, "回数昇格");

    for (int i = 0; i < SILVER_VISIT_COUNT; i++) {
      completeLinkedOrder(customerId, "回数昇格" + i, TINY_FEE);
    }

    assertThat(netGrantedPointsOf(member)).as("前提: 純額側は閾値に届いていないこと").isLessThan(5000);
    assertThat(rankOf(member)).isEqualTo(MemberRank.SILVER);
  }

  @Test
  @DisplayName("付与の純額だけで昇格すること（来店は 1 回）")
  void promotesOnGrantedPointsAlone() {
    RegisteredMember member = registerAndLogin("points");
    String customerId = linkedCustomer(member, "純額昇格");

    completeLinkedOrder(customerId, "純額昇格", FEE_FOR_SILVER_POINTS);

    assertThat(rankOf(member)).isEqualTo(MemberRank.SILVER);
    List<MemberRankHistory> histories =
        memberRankHistoryRepository.findByMemberIdOrderByIdAsc(member.id());
    assertThat(histories).hasSize(1);
    MemberRankHistory promotion = histories.get(0);
    assertThat(promotion.getPreviousRank()).isEqualTo(MemberRank.BRONZE);
    assertThat(promotion.getNewRank()).isEqualTo(MemberRank.SILVER);
    assertThat(promotion.getPromotedAt()).isNotNull();
    assertThat(promotion.getTriggeringEntryId())
        .as("根拠は契機となった付与仕訳そのものを指すこと")
        .isEqualTo(latestGrantIdOf(member));
  }

  @Test
  @DisplayName("取消された付与は純額から控除され、控除後の額で判定されること")
  void theMetricIsNetOfCancellations() {
    RegisteredMember member = registerAndLogin("net");
    String customerId = linkedCustomer(member, "純額控除");

    // 総額では 5,000pt に届くが、うち 4,000pt を取り消す
    completeLinkedOrder(customerId, "純額控除1", 400_000);
    pointLedgerService.cancel(latestGrantIdOf(member), null);
    completeLinkedOrder(customerId, "純額控除2", 100_000);

    assertThat(grossGrantedPointsOf(member)).as("前提: 総額では閾値に届いていること").isEqualTo(5000);
    assertThat(netGrantedPointsOf(member)).isEqualTo(1000);
    assertThat(rankOf(member)).as("控除後の純額で判じるので上がらないこと").isEqualTo(MemberRank.BRONZE);
  }

  @Test
  @DisplayName("昇格の根拠が取消されて純額が閾値を割ってもランクは戻らないこと（棘輪）")
  void neverDemotesAfterTheGrantIsCancelled() {
    RegisteredMember member = registerAndLogin("ratchet");
    String customerId = linkedCustomer(member, "棘輪");
    completeLinkedOrder(customerId, "棘輪1", FEE_FOR_SILVER_POINTS);
    assertThat(rankOf(member)).as("前提: 純額で SILVER へ上がっていること").isEqualTo(MemberRank.SILVER);

    pointLedgerService.cancel(latestGrantIdOf(member), null);
    // 次の判定を起こす。取消の後に指標を読み直しても、下位へは倒れない
    completeLinkedOrder(customerId, "棘輪2", TINY_FEE);

    assertThat(netGrantedPointsOf(member)).as("前提: 純額は閾値を割っていること").isLessThan(5000);
    assertThat(rankOf(member)).isEqualTo(MemberRank.SILVER);
    assertThat(memberRankHistoryRepository.findByMemberIdOrderByIdAsc(member.id()))
        .as("降格は履歴にも現れないこと")
        .hasSize(1);
  }

  @Test
  @DisplayName("昇格の前後で同じ会計金額の付与額が変わらないこと（ランク別付与率は存在しない）")
  void theGrantRateDoesNotDependOnRank() {
    RegisteredMember member = registerAndLogin("rate");
    String customerId = linkedCustomer(member, "付与率");

    int beforePromotion = completeLinkedOrder(customerId, "付与率1", FEE_FOR_SILVER_POINTS);
    assertThat(rankOf(member)).as("前提: ここで昇格していること").isEqualTo(MemberRank.SILVER);
    int afterPromotion = completeLinkedOrder(customerId, "付与率2", FEE_FOR_SILVER_POINTS);

    assertThat(afterPromotion).isEqualTo(beforePromotion);
  }

  @Test
  @DisplayName("伝票トークンの事後申領でも判定が走ること（帰属が成立し付与が記帳される契機）")
  void promotesOnReceiptClaimToo() {
    RegisteredMember member = registerAndLogin("claim");
    // 会員へ帰属しない完了を作り、その伝票を本人が申領する
    String rawToken = completedOrderWithToken("事後申領", FEE_FOR_SILVER_POINTS);

    ResponseEntity<JsonNode> claimed =
        rest.exchange(
            "/platform/me/receipts",
            HttpMethod.POST,
            new HttpEntity<>("{\"token\": \"" + rawToken + "\"}", bearer(member.token())),
            JsonNode.class);

    assertThat(claimed.getStatusCode()).as("前提: 申領が成功すること").isEqualTo(HttpStatus.CREATED);
    assertThat(rankOf(member)).isEqualTo(MemberRank.SILVER);
  }

  @Test
  @DisplayName("閾値は SystemConfig から読まれ、変更が次回の判定へ反映されること")
  void thresholdsComeFromSystemConfig() {
    RegisteredMember member = registerAndLogin("config");
    String customerId = linkedCustomer(member, "閾値変更");
    completeLinkedOrder(customerId, "閾値変更1", TINY_FEE);
    assertThat(rankOf(member)).as("前提: 種子閾値では 1 回で上がらないこと").isEqualTo(MemberRank.BRONZE);

    String original = configValueOf("member_rank_silver_visit_count");
    try {
      setConfigValue("member_rank_silver_visit_count", "2");
      completeLinkedOrder(customerId, "閾値変更2", TINY_FEE);
      assertThat(rankOf(member)).isEqualTo(MemberRank.SILVER);
    } finally {
      setConfigValue("member_rank_silver_visit_count", original);
    }
  }

  // ==================== 台帳・ランクの読み出し ====================

  private MemberRank rankOf(RegisteredMember member) {
    return memberRepository.findById(member.id()).map(Member::getRank).orElseThrow();
  }

  private long netGrantedPointsOf(RegisteredMember member) {
    return pointEntryRepository.sumNetOrderGrants(member.id());
  }

  private long grossGrantedPointsOf(RegisteredMember member) {
    return pointEntryRepository.findCredits(member.id()).stream()
        .mapToLong(PointEntry::getAmount)
        .sum();
  }

  /** 直近に記帳された付与仕訳。昇格の根拠がこれと一致することの確認と、取消の対象の指定に使う。 */
  private long latestGrantIdOf(RegisteredMember member) {
    return pointEntryRepository.findCredits(member.id()).stream()
        .mapToLong(PointEntry::getId)
        .max()
        .orElseThrow();
  }

  private String configValueOf(String key) {
    return systemConfigRepository
        .findByConfigKey(key)
        .map(SystemConfig::getConfigValue)
        .orElseThrow();
  }

  private void setConfigValue(String key, String value) {
    SystemConfig config = systemConfigRepository.findByConfigKey(key).orElseThrow();
    config.setConfigValue(value);
    systemConfigRepository.saveAndFlush(config);
  }

  // ==================== 受注の用意 ====================

  /** 紐づけ済み顧客の受注を 1 件完了する。戻り値は受注へ書かれた付与ポイント。 */
  private int completeLinkedOrder(String customerId, String label, int totalFee) {
    String orderId = createOrder(customerId, label);
    ResponseEntity<JsonNode> completed =
        rest.exchange(
            "/store/orders/" + orderId + "/completion",
            HttpMethod.POST,
            new HttpEntity<>(completionBody(orderId, totalFee), storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(completed.getStatusCode()).as("前提: 受注を完了できること").isEqualTo(HttpStatus.OK);
    return completed.getBody().path("auto_grant_points").asInt();
  }

  /** 会員へ帰属しない完了を 1 件作り、発行された伝票トークンの生値を受け取る。 */
  private String completedOrderWithToken(String label, int totalFee) {
    String orderId = createOrder(null, label);
    ResponseEntity<JsonNode> completed =
        rest.exchange(
            "/store/orders/" + orderId + "/completion",
            HttpMethod.POST,
            new HttpEntity<>(completionBody(orderId, totalFee), storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(completed.getStatusCode()).as("前提: 受注を完了できること").isEqualTo(HttpStatus.OK);
    String raw = completed.getBody().path("receipt_token").asString();
    assertThat(raw).as("前提: 完了応答が伝票トークンを運ぶこと").isNotBlank();
    return raw;
  }

  private String completionBody(String orderId, int totalFee) {
    return "{\"expected_version\":"
        + orderVersion(storeHeaders(STORE_A), orderId)
        + ",\"fee_lines\":[{\"kind\":\"SURCHARGE\",\"name\":\"会計\",\"amount\":"
        + totalFee
        + "}]}";
  }

  private String createOrder(String customerId, String label) {
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
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 受注作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private String createCast(String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>(
                "{\"name\": \"" + name + "-" + System.nanoTime() + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: キャスト作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  // ==================== 会員・関連 ====================

  private record RegisteredMember(long id, String memberCode, String token) {}

  private String linkedCustomer(RegisteredMember member, String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>("{\"name\": \"" + name + "-" + nonce + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 顧客作成が成功すること").isTrue();
    String customerId = created.getBody().path("id").asString();

    ResponseEntity<JsonNode> linked =
        rest.exchange(
            "/store/customers/" + customerId + "/member-link",
            HttpMethod.POST,
            new HttpEntity<>(
                "{\"member_code\": \"" + member.memberCode() + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(linked.getStatusCode()).as("前提: 会員の紐づけが成功すること").isEqualTo(HttpStatus.OK);
    return customerId;
  }

  private RegisteredMember registerAndLogin(String prefix) {
    String email = prefix + "-member-rank-it-" + nonce + "-" + System.nanoTime() + "@kizuna.test";
    ResponseEntity<JsonNode> registration =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \""
                    + email
                    + "\", \"password\": \""
                    + PASSWORD
                    + "\", \"display_name\": \"ランク検証会員\"}",
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
