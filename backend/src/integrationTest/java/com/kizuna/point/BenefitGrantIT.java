package com.kizuna.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.point.domain.BenefitRuleRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 特典規則が台帳へ付与を産む経路を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>固定するのは 6 つ。①完了と②事後申領の双方で来店特典が記帳され、産地の規則と根拠受注を名乗ること。③期限が規則の
 * 「付与ポイント有効期間」から落ちること。④適用期間の窓が<b>根拠受注の営業日</b>で判じられること（規則が閉じた後に届いた
 * 申領でも、窓内の受注なら付与される）。⑤停用済み・適用店舗外・一人一回限りの二件目が付与を産まないこと。 ⑥記帳済みの付与が規則の物理削除を FK で封じ、巻き戻しが種別を問わず付与を拾うこと。
 *
 * <p>規則はプラットフォーム全体に効くので、各テストの後に停用して次のテストと他の統合テストへ持ち越さない。
 */
class BenefitGrantIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  /** demo シード（seed/05-demo.yaml）の山田次郎（STORE_STAFF・授権店舗 = 店舗1）。受付担当として使用。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  /** BENEFIT_MANAGE を持つ HQ 管理者シード。規則の作成は console 限定なのでこの身分で叩く。 */
  private static final String HQ_EMAIL = "admin@kizuna.test";

  private static final int TOTAL_FEE = 12000;

  /** シード設定（100 円ごとに 1 ポイント）を {@link #TOTAL_FEE} に当てた受注付与。特典とは別勘定で積まれる。 */
  private static final int EXPECTED_ORDER_GRANT = 120;

  private static final int BENEFIT_POINTS = 500;

  private static final int VALIDITY_DAYS = 180;

  @Autowired private MemberRepository memberRepository;
  @Autowired private BenefitRuleRepository benefitRuleRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final long nonce = System.nanoTime();

  private String hqToken;

  @BeforeEach
  void loginAsHqAdmin() {
    hqToken = login(HQ_EMAIL);
  }

  /** 規則は店舗を跨いで効くため、置いたまま次のテストへ渡すと無関係な完了が特典を積む。 */
  @AfterEach
  void deactivateRulesLeftBehind() {
    jdbcTemplate.update("update t_benefit_rules set enabled = false where enabled");
  }

  @Test
  @DisplayName("完了で来店特典が記帳され、産地の規則・根拠受注・規則由来の期限を持つこと")
  void completionPostsTheVisitBenefitWithItsRuleOrderAndExpiry() {
    long ruleId = createRule(allStores("EVERY_TIME", VALIDITY_DAYS));
    RegisteredMember member = registerAndLogin("visit");
    String customerId = linkedCustomer(member.memberCode());

    String orderId = createOrder(LocalDate.now(), customerId);
    complete(orderId, null);

    BenefitRow benefit = onlyBenefitGrantOf(member.id());
    assertThat(benefit.amount()).isEqualTo(BENEFIT_POINTS);
    assertThat(benefit.benefitRuleId()).isEqualTo(ruleId);
    assertThat(benefit.orderId()).as("巻き戻しが拾えるよう受注を名乗ること").isEqualTo(orderId);
    assertThat(benefit.originatingStoreId()).isEqualTo(STORE_A);
    assertThat(benefit.expiresOn())
        .as("期限は規則の付与ポイント有効期間から落ちること")
        .isEqualTo(LocalDate.now().plusDays(VALIDITY_DAYS));
    assertThat(balanceOf(customerId))
        .as("受注付与とは別勘定で残高へ積み上がること")
        .isEqualTo(EXPECTED_ORDER_GRANT + BENEFIT_POINTS);
  }

  @Test
  @DisplayName("無期限指定の規則が産む特典付与は期限を持たないこと")
  void anUnlimitedRuleProducesAnUnexpiringGrant() {
    createRule(allStores("EVERY_TIME", null));
    RegisteredMember member = registerAndLogin("unlimited");

    complete(createOrder(LocalDate.now(), linkedCustomer(member.memberCode())), null);

    assertThat(onlyBenefitGrantOf(member.id()).expiresOn()).isNull();
  }

  @Test
  @DisplayName("伝票トークンの事後申領でも同じ規則で来店特典が記帳されること")
  void theReceiptClaimPostsTheSameVisitBenefit() {
    long ruleId = createRule(allStores("EVERY_TIME", null));
    String orderId = createOrder(LocalDate.now(), null);
    String rawToken = completeWithoutMember(orderId);
    RegisteredMember claimant = registerAndLogin("claim");

    assertThat(claim(claimant, rawToken).getStatusCode()).isEqualTo(HttpStatus.CREATED);

    BenefitRow benefit = onlyBenefitGrantOf(claimant.id());
    assertThat(benefit.benefitRuleId()).isEqualTo(ruleId);
    assertThat(benefit.orderId()).isEqualTo(orderId);
    assertThat(benefit.amount()).isEqualTo(BENEFIT_POINTS);
  }

  @Test
  @DisplayName("適用期間の窓は根拠受注の営業日で判じ、申領した日では判じないこと")
  void theWindowIsJudgedByTheOrderDateAndNotByTheClaimDate() {
    LocalDate orderDate = LocalDate.now().minusDays(3);
    // 窓は既に閉じている。申領は行政上の遅延にすぎず、発火した事実は根拠受注の側にある。
    createRule(
        """
        {"name":"特典IT_閉じた窓-%d","type":"VISIT","store_scope_type":"ALL_STORES",
         "effective_from":"%s","effective_until":"%s",
         "repeat_policy":"EVERY_TIME","points":%d}
        """
            .formatted(
                nonce, orderDate.minusDays(1), LocalDate.now().minusDays(1), BENEFIT_POINTS));
    String orderId = createOrder(orderDate, null);
    String rawToken = completeWithoutMember(orderId);
    RegisteredMember claimant = registerAndLogin("late");

    assertThat(claim(claimant, rawToken).getStatusCode()).isEqualTo(HttpStatus.CREATED);

    assertThat(onlyBenefitGrantOf(claimant.id()).orderId()).isEqualTo(orderId);
  }

  @Test
  @DisplayName("窓の外の営業日の受注には、窓が今も開いていても付与しないこと")
  void anOrderOutsideTheWindowGetsNothing() {
    createRule(
        """
        {"name":"特典IT_今日から-%d","type":"VISIT","store_scope_type":"ALL_STORES",
         "effective_from":"%s","repeat_policy":"EVERY_TIME","points":%d}
        """
            .formatted(nonce, LocalDate.now(), BENEFIT_POINTS));
    RegisteredMember member = registerAndLogin("before-window");

    complete(createOrder(LocalDate.now().minusDays(1), linkedCustomer(member.memberCode())), null);

    assertThat(benefitGrantsOf(member.id())).isEmpty();
  }

  @Test
  @DisplayName("一人一回限りの規則は同一会員の二件目の受注で付与を産まないこと")
  void aOncePerMemberRuleFiresOnlyOnce() {
    createRule(allStores("ONCE_PER_MEMBER", null));
    RegisteredMember member = registerAndLogin("once");
    String customerId = linkedCustomer(member.memberCode());

    complete(createOrder(LocalDate.now(), customerId), null);
    complete(createOrder(LocalDate.now(), customerId), null);

    assertThat(benefitGrantsOf(member.id())).as("二件目の根拠受注は付与を産まないこと").hasSize(1);
    assertThat(balanceOf(customerId))
        .as("受注付与は二件ぶん、特典は一件ぶんであること")
        .isEqualTo(EXPECTED_ORDER_GRANT * 2 + BENEFIT_POINTS);
  }

  @Test
  @DisplayName("停用中の規則と適用店舗外の規則は付与を産まないこと")
  void deactivatedAndOutOfScopeRulesProduceNothing() {
    long deactivated = createRule(allStores("EVERY_TIME", null));
    deactivate(deactivated);
    createRule(
        """
        {"name":"特典IT_別店舗-%d","type":"VISIT","store_scope_type":"SPECIFIC_STORES",
         "store_ids":[%d],"repeat_policy":"EVERY_TIME","points":%d}
        """
            .formatted(nonce, STORE_B, BENEFIT_POINTS));
    RegisteredMember member = registerAndLogin("silent");
    String customerId = linkedCustomer(member.memberCode());

    complete(createOrder(LocalDate.now(), customerId), null);

    assertThat(benefitGrantsOf(member.id())).isEmpty();
    assertThat(balanceOf(customerId)).isEqualTo(EXPECTED_ORDER_GRANT);
  }

  @Test
  @DisplayName("巻き戻しは種別を問わず受注を根拠とする加算を拾い、特典付与も取り消すこと")
  void rollbackAlsoCancelsTheBenefitGrant() {
    createRule(allStores("EVERY_TIME", null));
    RegisteredMember member = registerAndLogin("rollback");
    String customerId = linkedCustomer(member.memberCode());
    String orderId = createOrder(LocalDate.now(), customerId);
    complete(orderId, null);
    assertThat(balanceOf(customerId))
        .as("前提: 受注付与と特典付与の双方が残高にあること")
        .isEqualTo(EXPECTED_ORDER_GRANT + BENEFIT_POINTS);

    ResponseEntity<JsonNode> rolledBack = rollback(orderId, "誤完了の全否定");

    assertThat(rolledBack.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(rolledBack.getBody().path("cancelled_points").asInt())
        .as("特典付与も打ち消す対象に入ること")
        .isEqualTo(EXPECTED_ORDER_GRANT + BENEFIT_POINTS);
    assertThat(balanceOf(customerId)).isZero();
  }

  @Test
  @DisplayName("付与仕訳に指されている規則は物理削除できないこと")
  void aRuleReferencedByAGrantCannotBeDeleted() {
    long ruleId = createRule(allStores("EVERY_TIME", null));
    RegisteredMember member = registerAndLogin("restrict");
    complete(createOrder(LocalDate.now(), linkedCustomer(member.memberCode())), null);
    assertThat(benefitGrantsOf(member.id())).as("前提: 規則を指す付与があること").hasSize(1);

    assertThatThrownBy(() -> benefitRuleRepository.deleteById(ruleId))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(benefitRuleRepository.findById(ruleId)).as("規則は残ること").isPresent();
  }

  @Test
  @DisplayName("特典付与でない仕訳が規則を名乗る INSERT と、規則を名乗らない特典付与の INSERT が DB CHECK で落ちること")
  void databaseBindsTheBenefitRuleReferenceToTheBenefitGrantType() {
    long ruleId = createRule(allStores("EVERY_TIME", null));
    long memberId = registerAndLogin("check").id();
    String orderId = createOrder(LocalDate.now(), null);

    assertThatThrownBy(() -> insertEntry(memberId, "BENEFIT_GRANT", 100, orderId, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_t_point_entries_benefit_rule_shape");
    assertThatThrownBy(() -> insertEntry(memberId, "ORDER_GRANT", 100, orderId, ruleId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_t_point_entries_benefit_rule_shape");

    // 対照: 組み合わせが揃えば同じ形の INSERT が通る（CHECK 以外の理由で落ちていない証明）。
    insertEntry(memberId, "BENEFIT_GRANT", 100, orderId, ruleId);
    // 同じ発火事象の二度目は一意索引が撥ねる（重複可否に依らず不正）。
    assertThatThrownBy(() -> insertEntry(memberId, "BENEFIT_GRANT", 100, orderId, ruleId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uq_t_point_entries_benefit_firing");
  }

  // ==================== 規則の用意 ====================

  private String allStores(String repeatPolicy, Integer validityDays) {
    return """
        {"name":"特典IT_%s-%d","type":"VISIT","store_scope_type":"ALL_STORES",
         "repeat_policy":"%s",%s"points":%d}
        """
        .formatted(
            repeatPolicy,
            System.nanoTime(),
            repeatPolicy,
            validityDays == null ? "" : "\"grant_validity_days\":" + validityDays + ",",
            BENEFIT_POINTS);
  }

  private long createRule(String body) {
    ResponseEntity<JsonNode> created =
        rest.exchange(
            "/platform/benefit-rules",
            HttpMethod.POST,
            new HttpEntity<>(body, bearerJson(hqToken)),
            JsonNode.class);
    assertThat(created.getStatusCode()).as("前提: 規則を作成できること").isEqualTo(HttpStatus.CREATED);
    return created.getBody().path("id").asLong();
  }

  private void deactivate(long ruleId) {
    long version =
        rest.exchange(
                "/platform/benefit-rules/" + ruleId,
                HttpMethod.GET,
                new HttpEntity<>(bearerJson(hqToken)),
                JsonNode.class)
            .getBody()
            .path("version")
            .asLong();
    ResponseEntity<JsonNode> deactivated =
        rest.exchange(
            "/platform/benefit-rules/" + ruleId + "/deactivation",
            HttpMethod.POST,
            new HttpEntity<>("{\"version\":%d}".formatted(version), bearerJson(hqToken)),
            JsonNode.class);
    assertThat(deactivated.getStatusCode()).as("前提: 規則を停用できること").isEqualTo(HttpStatus.NO_CONTENT);
  }

  // ==================== 受注の用意 ====================

  private String createOrder(LocalDate businessDate, String customerId) {
    String castId = createCast("特典IT担当-" + System.nanoTime());
    String body =
        "{\"receptionist_id\": "
            + SEED_RECEPTIONIST_ID
            + ", \"business_date\": \""
            + businessDate
            + "\", \"cast_id\": \""
            + castId
            + "\", \"pax\": 2"
            + (customerId == null ? "" : ", \"customer_id\": \"" + customerId + "\"")
            + "}";
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/orders", new HttpEntity<>(body, managerHeaders(STORE_A)), JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 受注作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private String createCast(String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: キャスト作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private JsonNode complete(String orderId, Integer usePoints) {
    ResponseEntity<JsonNode> completed =
        rest.exchange(
            "/store/orders/" + orderId + "/completion",
            HttpMethod.POST,
            new HttpEntity<>(
                "{\"expected_version\":"
                    + orderVersion(managerHeaders(STORE_A), orderId)
                    + ",\"fee_lines\":[{\"kind\":\"SURCHARGE\",\"name\":\"会計\",\"amount\":"
                    + TOTAL_FEE
                    + "}]"
                    + (usePoints == null ? "" : ",\"use_points\":" + usePoints)
                    + "}",
                managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(completed.getStatusCode()).as("前提: 受注を完了できること").isEqualTo(HttpStatus.OK);
    return completed.getBody();
  }

  /** 会員へ帰属しない完了。発行された伝票トークンの生値を返す。 */
  private String completeWithoutMember(String orderId) {
    String raw = complete(orderId, null).path("receipt_token").asString();
    assertThat(raw).as("前提: 完了応答が伝票トークンを運ぶこと").isNotBlank();
    return raw;
  }

  private ResponseEntity<JsonNode> claim(RegisteredMember as, String rawToken) {
    return rest.postForEntity(
        "/platform/me/receipts",
        new HttpEntity<>("{\"token\": \"" + rawToken + "\"}", bearerJson(as.token())),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> rollback(String orderId, String reason) {
    return rest.exchange(
        "/store/orders/" + orderId + "/point-rollback",
        HttpMethod.POST,
        new HttpEntity<>("{\"reason\": \"" + reason + "\"}", managerHeaders(STORE_A)),
        JsonNode.class);
  }

  // ==================== 顧客・会員・台帳 ====================

  private String linkedCustomer(String memberCode) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>(
                "{\"name\": \"特典IT顧客-" + System.nanoTime() + "\"}", managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 顧客作成が成功すること").isTrue();
    String customerId = created.getBody().path("id").asString();
    ResponseEntity<JsonNode> linked =
        rest.exchange(
            "/store/customers/" + customerId + "/member-link",
            HttpMethod.POST,
            new HttpEntity<>("{\"member_code\": \"" + memberCode + "\"}", managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(linked.getStatusCode()).as("前提: 会員の紐づけが成功すること").isEqualTo(HttpStatus.OK);
    return customerId;
  }

  /** 残高は本番の読み口から取る。テストが台帳の合計を組み直すと、同じ誤りを両側で犯しても緑になる。 */
  private long balanceOf(String customerId) {
    ResponseEntity<JsonNode> response =
        rest.exchange(
            "/store/customers/" + customerId + "/member-point-balance",
            HttpMethod.GET,
            new HttpEntity<>(managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(response.getStatusCode()).as("前提: 残高が読めること").isEqualTo(HttpStatus.OK);
    return response.getBody().path("balance").asLong();
  }

  /** 台帳の特典付与 1 行。日付は列の型で読む（{@code queryForList} は DATE を java.sql.Date で返す）。 */
  private record BenefitRow(
      int amount,
      long benefitRuleId,
      String orderId,
      long originatingStoreId,
      LocalDate expiresOn) {}

  private BenefitRow onlyBenefitGrantOf(long memberId) {
    List<BenefitRow> found = benefitGrantsOf(memberId);
    assertThat(found).as("会員 %d の特典付与が 1 件だけであること", memberId).hasSize(1);
    return found.get(0);
  }

  private List<BenefitRow> benefitGrantsOf(long memberId) {
    return jdbcTemplate.query(
        """
        select amount, benefit_rule_id, order_id, originating_store_id, expires_on
        from t_point_entries
        where member_id = ? and entry_type = 'BENEFIT_GRANT'
        order by id
        """,
        (rs, rowNum) ->
            new BenefitRow(
                rs.getInt("amount"),
                rs.getLong("benefit_rule_id"),
                rs.getString("order_id"),
                rs.getLong("originating_store_id"),
                rs.getObject("expires_on", LocalDate.class)),
        memberId);
  }

  /** 域検証を通さない直挿。実体の構築では届かない DB 側の CHECK と一意索引だけを撃つ。 */
  private void insertEntry(
      long memberId, String entryType, int amount, String orderId, Long benefitRuleId) {
    jdbcTemplate.update(
        """
        insert into t_point_entries
          (member_id, entry_type, amount, order_id, benefit_rule_id, created_at, updated_at, version)
        values (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        memberId,
        entryType,
        amount,
        orderId,
        benefitRuleId);
  }

  // ==================== 会員の登録 ====================

  private record RegisteredMember(long id, String memberCode, String token) {}

  private RegisteredMember registerAndLogin(String prefix) {
    String email = prefix + "-benefit-it-" + nonce + "-" + System.nanoTime() + "@kizuna.test";
    ResponseEntity<JsonNode> registration =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \""
                    + email
                    + "\", \"password\": \""
                    + PASSWORD
                    + "\", \"display_name\": \"特典検証会員\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(registration.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);
    String memberCode = registration.getBody().path("member_code").asString();
    long memberId = memberRepository.findByMemberCode(memberCode).map(Member::getId).orElseThrow();

    return new RegisteredMember(memberId, memberCode, loginWithPassword(email, PASSWORD));
  }

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private static HttpHeaders bearerJson(String bearerToken) {
    HttpHeaders headers = jsonHeaders();
    headers.setBearerAuth(bearerToken);
    return headers;
  }
}
