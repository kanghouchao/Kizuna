package com.kizuna.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.point.domain.PointEntryType;
import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
 * 受注を宛先とするポイント巻き戻しを本物の PostgreSQL で検証する統合テスト。
 *
 * <p>固定するのは 5 つ。①付与の未消費残が取消で無効化され、利用が<b>元のロットへ期限そのまま</b>返ること（利用取消が新しい
 * ロットにならないので残高が二重に増えない）。②同じ受注への二度目が撥ねられ、初回の理由・実行者が書き換わらないこと。 ③巻き戻し済みの受注は<b>台帳の仕訳がゼロでも</b>事後申領を拒むこと
 * — 拒否の材料は操作記録である。④行内で表せる不変量が DB CHECK で強制されること。⑤権限が POINT_ADJUST（店長限定）であること。
 *
 * <p>シード設定は「100 円ごとに 1 ポイント付与、利用は 100 ポイント単位」。
 */
class PointRollbackIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  /** demo シード（seed/05-demo.yaml）の山田次郎（STORE_STAFF・授権店舗 = 店舗1）。受付担当として使用。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  private static final int TOTAL_FEE = 12000;

  /** シード設定（100 円ごとに 1 ポイント）を {@link #TOTAL_FEE} に当てた付与額。 */
  private static final int EXPECTED_GRANT = 120;

  /** 原資として先に積むロット。期限が巻き戻しで保たれることを見るため必ず期限付きにする。 */
  private static final int SEED_LOT = 1000;

  private static final LocalDate LOT_EXPIRY = LocalDate.now().plusYears(1);

  private static final int USED_POINTS = 300;

  /** 利用の単位（100 ポイント）ちょうどの付与になる会計金額と、その付与額。使い切りを作るのに要る。 */
  private static final int UNIT_FEE = 10_000;

  private static final int UNIT_GRANT = 100;

  @Autowired private PointEntryRepository pointEntryRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("巻き戻しは付与を取消し、利用を元のロットへ期限そのまま返して残高を元へ戻すこと")
  void rollbackCancelsGrantsAndReturnsUsedPointsToTheOriginalLot() {
    Attributed attributed = completedOrderUsingPoints();
    long lotId = seedLotOf(attributed.memberId());
    assertThat(balanceOf(attributed.customerId()))
        .as("前提: 完了で付与が乗り、利用が引かれていること")
        .isEqualTo(SEED_LOT - USED_POINTS + EXPECTED_GRANT);

    ResponseEntity<JsonNode> rolledBack = rollback(attributed.orderId(), "誤完了の全否定");

    assertThat(rolledBack.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(rolledBack.getBody().path("cancelled_points").asInt()).isEqualTo(EXPECTED_GRANT);
    assertThat(rolledBack.getBody().path("restored_points").asInt()).isEqualTo(USED_POINTS);

    // 利用取消は新しいロットにならない。ロットとして数えられていれば返した分が二重に現れ、
    // 残高は SEED_LOT + USED_POINTS になる。
    assertThat(balanceOf(attributed.customerId())).as("残高は原資の額そのものへ戻ること").isEqualTo(SEED_LOT);
    assertThat(expiresOnOf(lotId)).as("返した先のロットの期限は元のまま").isEqualTo(LOT_EXPIRY);

    PointEntry reversal = onlyEntryOfType(attributed.memberId(), PointEntryType.USE_CANCEL);
    assertThat(reversal.getAmount()).as("逆転は加算").isEqualTo(USED_POINTS);
    assertThat(reversal.getExpiresOn()).as("自身はロットにならないので期限を持たない").isNull();
    assertThat(reversal.getOrderId()).as("受注ごとの付与合計へ混ざらない").isNull();
    assertThat(reversal.getReason()).isEqualTo("誤完了の全否定");
    assertThat(reversal.getOriginalEntryId())
        .isEqualTo(onlyEntryOfType(attributed.memberId(), PointEntryType.USE).getId());

    PointEntry cancellation = onlyEntryOfType(attributed.memberId(), PointEntryType.CANCEL);
    assertThat(cancellation.getAmount()).isEqualTo(-EXPECTED_GRANT);
    assertThat(cancellation.getReason()).as("取消の仕訳も理由を持つこと").isEqualTo("誤完了の全否定");
  }

  @Test
  @DisplayName("同じ受注への二度目の巻き戻しは 409 で撥ねられ、初回の理由が書き換わらないこと")
  void secondRollbackIsRejectedAndTheFirstRecordSurvives() {
    Attributed attributed = completedOrderUsingPoints();
    assertThat(rollback(attributed.orderId(), "初回の理由").getStatusCode())
        .as("前提: 初回が通ること")
        .isEqualTo(HttpStatus.CREATED);

    ResponseEntity<JsonNode> second = rollback(attributed.orderId(), "二度目の理由");

    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(rollbackReasonOf(attributed.orderId())).isEqualTo("初回の理由");
    assertThat(entriesOfType(attributed.memberId(), PointEntryType.CANCEL))
        .as("台帳にも二度は積まれないこと")
        .hasSize(1);
  }

  @Test
  @DisplayName("巻き戻し済みの受注は、台帳の仕訳がゼロでも事後申領を同形のエラーで拒むこと")
  void rolledBackOrderRefusesTheReceiptClaim() {
    // 会員へ帰属しない完了なので、この時点で台帳には 1 行も無い。拒否の材料は操作記録しかない。
    Issued issued = completedOrderWithToken();
    ResponseEntity<JsonNode> rolledBack = rollback(issued.orderId(), "誤完了の全否定");
    assertThat(rolledBack.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(rolledBack.getBody().path("cancelled_points").asInt()).isZero();
    assertThat(ledgerRowsFor(issued.orderId())).as("前提: 台帳に仕訳が無いこと").isZero();

    RegisteredMember claimant = registerAndLogin("claim");
    ResponseEntity<JsonNode> claimed = claim(claimant, issued.token());

    assertThat(claimed.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(claimed.getBody().path("error").asString())
        .as("不在・期限切れ・使用済みと同形の文言であること")
        .contains("この伝票は申領できません");
    assertThat(ledgerRowsFor(issued.orderId())).as("申領が付与を積み直さないこと").isZero();
  }

  @Test
  @DisplayName("巻き戻し済みの受注の付与が、別の受注の巻き戻しで残高へ復活しないこと")
  void rollbackNeverRevivesGrantsOfAnAlreadyRolledBackOrder() {
    // 受注 A の付与を受注 B が使い切ると、A の巻き戻しは打ち消す対象を持たない（記録だけが残る）。
    // その後 B を巻き戻すと A のロットへ量が戻るが、A は二度目を受け付けないため、ここで
    // 打ち消さなければ無効にしたはずの付与が使える残高として復活する。
    RegisteredMember member = registerAndLogin("revive");
    String customerId = linkedCustomer(member.memberCode());
    // 利用は 100 ポイント単位なので、使い切れるよう付与も 100 ちょうどになる会計にする。
    String orderA = createOrder(createCast("復活検証A-" + nonce), customerId);
    complete(orderA, UNIT_FEE, null);
    assertThat(balanceOf(customerId)).as("前提: A の付与だけが残高にあること").isEqualTo(UNIT_GRANT);

    String orderB = createOrder(createCast("復活検証B-" + nonce), customerId);
    complete(orderB, UNIT_FEE, UNIT_GRANT);
    assertThat(balanceOf(customerId)).as("前提: B が A の付与を使い切り、B の付与だけが残ること").isEqualTo(UNIT_GRANT);

    assertThat(rollback(orderA, "A の全否定").getBody().path("cancelled_points").asInt())
        .as("前提: A には打ち消す残余が無いこと")
        .isZero();

    ResponseEntity<JsonNode> rolledBackB = rollback(orderB, "B の全否定");

    assertThat(rolledBackB.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(rolledBackB.getBody().path("restored_points").asInt()).isEqualTo(UNIT_GRANT);
    assertThat(rolledBackB.getBody().path("cancelled_points").asInt())
        .as("B 自身の付与に加えて、A へ戻した分も打ち消すこと")
        .isEqualTo(UNIT_GRANT * 2);
    assertThat(balanceOf(customerId)).as("無効の付与は残高へ戻らないこと").isZero();
  }

  @Test
  @DisplayName("確定済み（未完了）の受注は巻き戻せないこと")
  void confirmedOrderCannotBeRolledBack() {
    String orderId = createOrder(createCast("未完了担当-" + nonce), null);

    ResponseEntity<JsonNode> rejected = rollback(orderId, "未完了への巻き戻し");

    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(rollbackRowsFor(orderId)).as("記録も書かれないこと").isZero();
  }

  @Test
  @DisplayName("POINT_ADJUST を持たない店員は巻き戻しにも下見にも届かないこと")
  void staffWithoutPointAdjustIsForbidden() {
    Issued issued = completedOrderWithToken();

    ResponseEntity<JsonNode> forbidden =
        rest.exchange(
            "/store/orders/" + issued.orderId() + "/point-rollback",
            HttpMethod.POST,
            new HttpEntity<>("{\"reason\": \"権限のない巻き戻し\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    ResponseEntity<JsonNode> previewForbidden =
        rest.exchange(
            "/store/orders/" + issued.orderId() + "/point-rollback-preview",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);

    assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(previewForbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(rollbackRowsFor(issued.orderId())).isZero();
  }

  @Test
  @DisplayName("下見は動く見込みの量を示し、実行後は済みとして 0 を示すこと")
  void previewReportsWhatWouldMoveAndThenReportsDone() {
    Attributed attributed = completedOrderUsingPoints();

    JsonNode before = preview(attributed.orderId());
    assertThat(before.path("already_rolled_back").asBoolean()).isFalse();
    assertThat(before.path("cancellable_points").asInt()).isEqualTo(EXPECTED_GRANT);
    assertThat(before.path("reversible_used_points").asInt()).isEqualTo(USED_POINTS);
    assertThat(before.path("member_code").asString()).isEqualTo(attributed.memberCode());

    assertThat(rollback(attributed.orderId(), "誤完了の全否定").getStatusCode())
        .isEqualTo(HttpStatus.CREATED);

    JsonNode after = preview(attributed.orderId());
    assertThat(after.path("already_rolled_back").asBoolean()).isTrue();
    assertThat(after.path("cancellable_points").asInt()).isZero();
    assertThat(after.path("reversible_used_points").asInt()).isZero();
  }

  @Test
  @DisplayName("受注 ID を持たない付与・利用の INSERT が DB CHECK で落ちること")
  void databaseRejectsOrderBasedEntriesWithoutAnOrderId() {
    long memberId = registerAndLogin("check").id();

    assertThatThrownBy(() -> insertEntry(memberId, "ORDER_GRANT", 100, null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_t_point_entries_order_required");
    assertThatThrownBy(() -> insertEntry(memberId, "USE", -100, null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_t_point_entries_order_required");

    // 対照: 受注 ID を添えれば同じ形の INSERT が通る（CHECK 以外の理由で落ちていない証明）。
    String orderId = createOrder(createCast("CHECK対照担当-" + nonce), null);
    insertEntry(memberId, "ORDER_GRANT", 100, orderId, null);
    assertThat(ledgerRowsFor(orderId)).isEqualTo(1);
  }

  @Test
  @DisplayName("元の利用を指さない利用取消の INSERT が DB CHECK で落ち、二度目の逆転が一意索引で落ちること")
  void databaseRejectsUnanchoredAndDuplicatedUseCancels() {
    long memberId = registerAndLogin("reverse").id();
    String orderId = createOrder(createCast("逆転対照担当-" + nonce), null);
    insertEntry(memberId, "USE", -100, orderId, null);
    long useId =
        jdbcTemplate.queryForObject(
            "select id from t_point_entries where order_id = ? and entry_type = 'USE'",
            Long.class,
            orderId);

    assertThatThrownBy(() -> insertEntry(memberId, "USE_CANCEL", 100, null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_t_point_entries_use_cancel_original");

    // 対照: 元の利用を指せば通る。二度目は参照の一意性が撥ねる（返した量が二重に戻らない）。
    insertEntry(memberId, "USE_CANCEL", 100, null, useId);
    assertThatThrownBy(() -> insertEntry(memberId, "USE_CANCEL", 100, null, useId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uq_t_point_entries_use_cancel_original");
  }

  // ==================== 端点の呼出 ====================

  private ResponseEntity<JsonNode> rollback(String orderId, String reason) {
    return rest.exchange(
        "/store/orders/" + orderId + "/point-rollback",
        HttpMethod.POST,
        new HttpEntity<>("{\"reason\": \"" + reason + "\"}", managerHeaders(STORE_A)),
        JsonNode.class);
  }

  private JsonNode preview(String orderId) {
    ResponseEntity<JsonNode> response =
        rest.exchange(
            "/store/orders/" + orderId + "/point-rollback-preview",
            HttpMethod.GET,
            new HttpEntity<>(managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(response.getStatusCode()).as("前提: 下見が読めること").isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  private ResponseEntity<JsonNode> claim(RegisteredMember as, String rawToken) {
    return rest.postForEntity(
        "/platform/me/receipts",
        new HttpEntity<>("{\"token\": \"" + rawToken + "\"}", bearer(as.token())),
        JsonNode.class);
  }

  // ==================== 受注の用意 ====================

  /** 会員へ帰属し、原資のロットからポイントを使って完了した受注。 */
  private record Attributed(String orderId, long memberId, String memberCode, String customerId) {}

  /** 発行された伝票トークンとその受注（会員へ帰属しない完了）。 */
  private record Issued(String orderId, String token) {}

  private Attributed completedOrderUsingPoints() {
    RegisteredMember member = registerAndLogin("rollback");
    String customerId = linkedCustomer(member.memberCode());
    seedLot(customerId);
    String orderId = createOrder(createCast("巻き戻し担当-" + nonce), customerId);
    complete(orderId, USED_POINTS);
    return new Attributed(orderId, member.id(), member.memberCode(), customerId);
  }

  private Issued completedOrderWithToken() {
    String orderId = createOrder(createCast("申領担当-" + nonce), null);
    JsonNode completed = complete(orderId, null);
    String raw = completed.path("receipt_token").asString();
    assertThat(raw).as("前提: 完了応答が伝票トークンを運ぶこと").isNotBlank();
    return new Issued(orderId, raw);
  }

  private JsonNode complete(String orderId, Integer usePoints) {
    return complete(orderId, TOTAL_FEE, usePoints);
  }

  private JsonNode complete(String orderId, int totalFee, Integer usePoints) {
    ResponseEntity<JsonNode> completed =
        rest.exchange(
            "/store/orders/" + orderId + "/completion",
            HttpMethod.POST,
            new HttpEntity<>(
                "{\"expected_version\":"
                    + orderVersion(managerHeaders(STORE_A), orderId)
                    + ",\"fee_lines\":[{\"kind\":\"SURCHARGE\",\"name\":\"会計\",\"amount\":"
                    + totalFee
                    + "}]"
                    + (usePoints == null ? "" : ",\"use_points\":" + usePoints)
                    + "}",
                managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(completed.getStatusCode()).as("前提: 受注を完了できること").isEqualTo(HttpStatus.OK);
    return completed.getBody();
  }

  private String createOrder(String castId, String customerId) {
    String body =
        "{\"receptionist_id\": "
            + SEED_RECEPTIONIST_ID
            + ", \"business_date\": \""
            + LocalDate.now()
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

  // ==================== 顧客・会員・台帳 ====================

  private String linkedCustomer(String memberCode) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>(
                "{\"name\": \"巻き戻し顧客-" + nonce + "-" + System.nanoTime() + "\"}",
                managerHeaders(STORE_A)),
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

  /** 利用の原資を期限付きで積む。期限が巻き戻しで保たれることを見るため手動調整で作る。 */
  private void seedLot(String customerId) {
    ResponseEntity<JsonNode> adjusted =
        rest.exchange(
            "/store/customers/" + customerId + "/point-adjustments",
            HttpMethod.POST,
            new HttpEntity<>(
                "{\"delta\": "
                    + SEED_LOT
                    + ", \"reason\": \"巻き戻し検証の原資\", \"expires_on\": \""
                    + LOT_EXPIRY
                    + "\", \"idempotency_key\": \""
                    + UUID.randomUUID()
                    + "\"}",
                managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(adjusted.getStatusCode()).as("前提: 原資の付与が成功すること").isEqualTo(HttpStatus.OK);
  }

  private long seedLotOf(long memberId) {
    return onlyEntryOfType(memberId, PointEntryType.MANUAL_ADJUST).getId();
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

  private LocalDate expiresOnOf(long entryId) {
    return jdbcTemplate.queryForObject(
        "select expires_on from t_point_entries where id = ?", LocalDate.class, entryId);
  }

  private PointEntry onlyEntryOfType(long memberId, PointEntryType type) {
    List<PointEntry> found = entriesOfType(memberId, type);
    assertThat(found).as("会員 %d の %s 仕訳が 1 件だけであること", memberId, type).hasSize(1);
    return found.get(0);
  }

  private List<PointEntry> entriesOfType(long memberId, PointEntryType type) {
    return pointEntryRepository.findAll().stream()
        .filter(entry -> entry.getMemberId() == memberId && entry.getEntryType() == type)
        .toList();
  }

  private int ledgerRowsFor(String orderId) {
    return jdbcTemplate.queryForObject(
        "select count(*) from t_point_entries where order_id = ?", Integer.class, orderId);
  }

  private int rollbackRowsFor(String orderId) {
    return jdbcTemplate.queryForObject(
        "select count(*) from t_point_rollbacks where order_id = ?", Integer.class, orderId);
  }

  private String rollbackReasonOf(String orderId) {
    return jdbcTemplate.queryForObject(
        "select reason from t_point_rollbacks where order_id = ?", String.class, orderId);
  }

  /** 域検証を通さない直挿。実体の構築では届かない DB 側の CHECK だけを撃つ。 */
  private void insertEntry(
      long memberId, String entryType, int amount, String orderId, Long originalEntryId) {
    jdbcTemplate.update(
        """
        insert into t_point_entries
          (member_id, entry_type, amount, order_id, original_entry_id, reason,
           created_at, updated_at, version)
        values (?, ?, ?, ?, ?, 'DB CHECK 検証', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        memberId,
        entryType,
        amount,
        orderId,
        originalEntryId);
  }

  // ==================== 会員の登録 ====================

  private record RegisteredMember(long id, String memberCode, String token) {}

  private RegisteredMember registerAndLogin(String prefix) {
    String email = prefix + "-rollback-it-" + nonce + "-" + System.nanoTime() + "@kizuna.test";
    ResponseEntity<JsonNode> registration =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \""
                    + email
                    + "\", \"password\": \""
                    + PASSWORD
                    + "\", \"display_name\": \"巻き戻し検証会員\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(registration.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);
    String memberCode = registration.getBody().path("member_code").asString();
    long memberId = memberRepository.findByMemberCode(memberCode).map(Member::getId).orElseThrow();

    ResponseEntity<JsonNode> login =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(login.getStatusCode()).as("前提: 会員としてログインできること").isEqualTo(HttpStatus.OK);
    return new RegisteredMember(memberId, memberCode, login.getBody().path("token").asString());
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
