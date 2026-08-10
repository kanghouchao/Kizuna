package com.kizuna.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.point.domain.PointAllocationRepository;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.point.domain.PointEntryType;
import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * 店舗 CRM からの会員ポイント残高照会と手動調整を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>固定するのは、残高が顧客ではなく紐づく会員の台帳から来ること、手動調整が理由付きの仕訳として台帳へ残ること、
 * そして調整が照会と別の権限（POINT_ADJUST・店長限定）で仕切られること。
 *
 * <p>権限の差は 2 人のシードユーザーで見る。基底クラスの yamada は店舗スタッフで CUSTOMER_MANAGE を持つが POINT_ADJUST は持たず、店長 tanaka
 * は両方を持つ。同じ端点に対して片方が 403、片方が 200 になることで、 403 が端点の不在でないことも同時に示される。
 */
class CustomerPointAdjustmentIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  /** 加算ロットの有効期限。残高は期限切れのロットを数えないため、必ず将来日にする。 */
  private static final LocalDate FUTURE_EXPIRY = LocalDate.now().plusYears(1);

  @Autowired private PointEntryRepository pointEntryRepository;
  @Autowired private PointAllocationRepository pointAllocationRepository;
  @Autowired private MemberRepository memberRepository;

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("店長の加算は残高を上げ、理由と発生店舗を持つ手動調整の仕訳として台帳に残ること")
  void creditRaisesTheBalanceAndLeavesAManualAdjustEntry() {
    String managerToken = loginAs("tanaka.hanako@kizuna.test");
    String memberCode = registerMember("credit");
    String customerId = linkedCustomer(managerToken, "加算", memberCode);

    ResponseEntity<JsonNode> adjusted =
        adjust(STORE_A, managerToken, customerId, 1000, "来店記念の付与", FUTURE_EXPIRY);

    assertThat(adjusted.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(adjusted.getBody().path("linked").asBoolean()).isTrue();
    assertThat(adjusted.getBody().path("balance").asInt()).isEqualTo(1000);

    List<PointEntry> entries = entriesOf(memberCode);
    assertThat(entries).hasSize(1);
    PointEntry entry = entries.get(0);
    assertThat(entry.getEntryType()).isEqualTo(PointEntryType.MANUAL_ADJUST);
    assertThat(entry.getAmount()).isEqualTo(1000);
    assertThat(entry.getReason()).isEqualTo("来店記念の付与");
    assertThat(entry.getExpiresOn()).isEqualTo(FUTURE_EXPIRY);
    // 発生店舗は帰属情報。残高の作用域ではない
    assertThat(entry.getOriginatingStoreId()).isEqualTo(STORE_A);
    assertThat(entry.getActorUserId()).as("実行者が仕訳に残ること").isNotNull();
  }

  @Test
  @DisplayName("減算は残高を減らし、加算ロットを引き当てる仕訳になること")
  void debitConsumesTheBalance() {
    String managerToken = loginAs("tanaka.hanako@kizuna.test");
    String memberCode = registerMember("debit");
    String customerId = linkedCustomer(managerToken, "減算", memberCode);
    assertThat(
            adjust(STORE_A, managerToken, customerId, 1000, "原資の付与", FUTURE_EXPIRY).getStatusCode())
        .as("前提: 減算の原資となる加算が通ること")
        .isEqualTo(HttpStatus.OK);

    ResponseEntity<JsonNode> adjusted =
        adjust(STORE_A, managerToken, customerId, -300, "二重付与の訂正", null);

    assertThat(adjusted.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(adjusted.getBody().path("balance").asInt()).isEqualTo(700);

    List<PointEntry> entries = entriesOf(memberCode);
    PointEntry debit =
        entries.stream().filter(entry -> entry.getAmount() < 0).findFirst().orElseThrow();
    assertThat(debit.getEntryType()).isEqualTo(PointEntryType.MANUAL_ADJUST);
    assertThat(debit.getReason()).isEqualTo("二重付与の訂正");
    assertThat(debit.getExpiresOn()).as("減算に有効期限は付かないこと").isNull();

    // 引き当ては減算仕訳の遅延関連なので、消費済み量は加算ロット側の集計として見る
    long creditId =
        entries.stream().filter(entry -> entry.getAmount() > 0).findFirst().orElseThrow().getId();
    assertThat(pointAllocationRepository.findConsumedBySourceEntryIds(List.of(creditId)))
        .singleElement()
        .satisfies(
            consumption -> assertThat(consumption.getConsumed()).as("加算ロットの消費済み量").isEqualTo(300L));

    // 残高の照会も同じ値を返す（調整の応答が台帳と食い違わないこと）
    assertThat(balance(STORE_A, managerToken, customerId).getBody().path("balance").asInt())
        .isEqualTo(700);
  }

  @Test
  @DisplayName("未紐づけの顧客は残高が載らず、調整も撥ねられること")
  void unlinkedCustomerHasNoBalanceAndCannotBeAdjusted() {
    String managerToken = loginAs("tanaka.hanako@kizuna.test");
    String customerId = createCustomerAs(STORE_A, managerToken, "未紐づけ-" + nonce);

    ResponseEntity<JsonNode> read = balance(STORE_A, managerToken, customerId);
    assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(read.getBody().path("linked").asBoolean()).isFalse();
    // 応答は non_null 包含のため、残高の無い顧客では項目ごと落ちる
    assertThat(read.getBody().hasNonNull("balance")).isFalse();

    assertThat(adjust(STORE_A, managerToken, customerId, 100, "紐づけ無しの調整", null).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("増減 0 の調整は撥ねられ、台帳へ何も積まれないこと")
  void zeroDeltaIsRejected() {
    String managerToken = loginAs("tanaka.hanako@kizuna.test");
    String memberCode = registerMember("zero");
    String customerId = linkedCustomer(managerToken, "零調整", memberCode);

    assertThat(adjust(STORE_A, managerToken, customerId, 0, "増減の無い調整", null).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(entriesOf(memberCode)).as("撥ねた調整は仕訳を積まないこと").isEmpty();
  }

  @Test
  @DisplayName("店舗スタッフは残高を読めるが調整はできないこと（403）")
  void staffCanReadTheBalanceButCannotAdjustIt() {
    String managerToken = loginAs("tanaka.hanako@kizuna.test");
    String memberCode = registerMember("staff");
    String customerId = linkedCustomer(managerToken, "権限", memberCode);

    // token は基底クラスの yamada（店舗スタッフ・CUSTOMER_MANAGE のみ）
    assertThat(balance(STORE_A, token, customerId).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(adjust(STORE_A, token, customerId, 500, "スタッフによる調整", null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(entriesOf(memberCode)).as("拒否された調整は仕訳を積まないこと").isEmpty();
  }

  @Test
  @DisplayName("commit 成立後の同一キー再送は記帳せず現在残高を返すこと")
  void replayAfterCommitDoesNotAppendASecondEntry() {
    String managerToken = loginAs("tanaka.hanako@kizuna.test");
    String memberCode = registerMember("replay");
    String customerId = linkedCustomer(managerToken, "再送", memberCode);
    String key = UUID.randomUUID().toString();

    assertThat(adjust(STORE_A, managerToken, customerId, 500, "回復再送の検証", null, key).getStatusCode())
        .as("前提: 初回の調整が成立すること")
        .isEqualTo(HttpStatus.OK);

    ResponseEntity<JsonNode> replay =
        adjust(STORE_A, managerToken, customerId, 500, "回復再送の検証", null, key);

    assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(replay.getBody().path("balance").asInt()).isEqualTo(500);
    List<PointEntry> entries = entriesOf(memberCode);
    assertThat(entries).as("再送は 2 件目を積まないこと").hasSize(1);
    assertThat(entries.get(0).getIdempotencyKey()).isEqualTo(key);
  }

  @Test
  @DisplayName("再送までに他の記帳が挟まっても、再送はスナップショットでなく現在残高を返すこと")
  void replayReturnsTheCurrentBalanceNotASnapshot() {
    String managerToken = loginAs("tanaka.hanako@kizuna.test");
    String memberCode = registerMember("current");
    String customerId = linkedCustomer(managerToken, "現在残高", memberCode);
    String key = UUID.randomUUID().toString();

    assertThat(adjust(STORE_A, managerToken, customerId, 500, "初回の付与", null, key).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(adjust(STORE_A, managerToken, customerId, -200, "間に挟まる減算", null).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    ResponseEntity<JsonNode> replay =
        adjust(STORE_A, managerToken, customerId, 500, "初回の付与", null, key);

    assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(replay.getBody().path("balance").asInt()).as("初回時点の 500 ではなく現在の 300").isEqualTo(300);
    assertThat(entriesOf(memberCode)).hasSize(2);
  }

  @Test
  @DisplayName("同一キーで内容の異なる要求は 409 になり、初回の成立が文言で伝わること")
  void sameKeyWithDifferentContentIsRefusedWithConflict() {
    String managerToken = loginAs("tanaka.hanako@kizuna.test");
    String memberCode = registerMember("mismatch");
    String customerId = linkedCustomer(managerToken, "不一致", memberCode);
    String key = UUID.randomUUID().toString();

    assertThat(adjust(STORE_A, managerToken, customerId, 500, "初回の付与", null, key).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    ResponseEntity<JsonNode> mismatched =
        adjust(STORE_A, managerToken, customerId, 300, "初回の付与", null, key);

    assertThat(mismatched.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(mismatched.getBody().path("error").asString()).contains("初回の調整は既に成立");
    assertThat(entriesOf(memberCode)).as("内容不一致の要求は記帳しないこと").hasSize(1);
  }

  @Test
  @DisplayName("同じ内容でも異なるキーなら正当な 2 回目として積まれること")
  void sameContentWithADifferentKeyIsALegitimateSecondAdjustment() {
    String managerToken = loginAs("tanaka.hanako@kizuna.test");
    String memberCode = registerMember("second");
    String customerId = linkedCustomer(managerToken, "二回目", memberCode);

    assertThat(adjust(STORE_A, managerToken, customerId, 500, "同内容の調整", null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    ResponseEntity<JsonNode> second =
        adjust(STORE_A, managerToken, customerId, 500, "同内容の調整", null);

    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(second.getBody().path("balance").asInt()).isEqualTo(1000);
    assertThat(entriesOf(memberCode)).hasSize(2);
  }

  @Test
  @DisplayName("同一顧客への同時再送でも台帳には 1 件しか積まれず、双方が成功応答になること")
  void concurrentReplayOnTheSameCustomerAppendsExactlyOnce() throws Exception {
    String managerToken = loginAs("tanaka.hanako@kizuna.test");
    String memberCode = registerMember("race");
    String customerId = linkedCustomer(managerToken, "同時", memberCode);
    String key = UUID.randomUUID().toString();

    List<ResponseEntity<JsonNode>> responses =
        inParallel(
            () -> adjust(STORE_A, managerToken, customerId, 500, "同時再送の検証", null, key),
            () -> adjust(STORE_A, managerToken, customerId, 500, "同時再送の検証", null, key));

    assertThat(responses)
        .extracting(ResponseEntity::getStatusCode)
        .containsExactly(HttpStatus.OK, HttpStatus.OK);
    assertThat(entriesOf(memberCode)).as("同時再送でも記帳は 1 件だけ").hasSize(1);
  }

  @Test
  @DisplayName("顧客を跨いだ同一キーの衝突は一方だけが記帳され、他方は 5xx でなく 409 になること")
  void crossCustomerKeyCollisionNeverYieldsAServerError() throws Exception {
    String managerToken = loginAs("tanaka.hanako@kizuna.test");
    String memberCodeX = registerMember("collide-x");
    String memberCodeY = registerMember("collide-y");
    String customerX = linkedCustomer(managerToken, "衝突X", memberCodeX);
    String customerY = linkedCustomer(managerToken, "衝突Y", memberCodeY);
    String key = UUID.randomUUID().toString();

    // 顧客が異なると行ロックの直列化が効かず、勝者だけが記帳される。敗者は事前検査（逐次化した場合）
    // でも一意制約の敗北（真の同時）でも、同じ内容比較に落ちて会員不一致の 409 になる。
    List<ResponseEntity<JsonNode>> responses =
        inParallel(
            () -> adjust(STORE_A, managerToken, customerX, 500, "衝突検証", null, key),
            () -> adjust(STORE_A, managerToken, customerY, 500, "衝突検証", null, key));

    assertThat(responses)
        .extracting(response -> response.getStatusCode().value())
        .containsExactlyInAnyOrder(200, 409);
    long entriesWithKey =
        pointEntryRepository.findAll().stream()
            .filter(entry -> key.equals(entry.getIdempotencyKey()))
            .count();
    assertThat(entriesWithKey).as("キーを持つ記帳は 1 件だけ").isEqualTo(1);
  }

  @Test
  @DisplayName("他店舗の顧客 ID は不可視のため残高照会でも調整でも 404 になること")
  void foreignStoreCustomerIsInvisible() {
    String managerToken = loginAs("tanaka.hanako@kizuna.test");
    String customerInB = createCustomerAs(STORE_B, managerToken, "不可視-" + nonce);

    // tanaka は両店舗に授権されるためヘッダは通り、越境は storeFilter による 404 として現れる
    assertThat(balance(STORE_A, managerToken, customerInB).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(adjust(STORE_A, managerToken, customerInB, 100, "越境の調整", null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  // ==================== 端点の呼出 ====================

  private ResponseEntity<JsonNode> balance(long storeId, String bearerToken, String customerId) {
    return rest.exchange(
        "/store/customers/" + customerId + "/member-point-balance",
        HttpMethod.GET,
        new HttpEntity<>(headersFor(storeId, bearerToken)),
        JsonNode.class);
  }

  /** 冪等キーを明示しない調整。1 回きりの操作として毎回新しいキーを載せる。 */
  private ResponseEntity<JsonNode> adjust(
      long storeId,
      String bearerToken,
      String customerId,
      int delta,
      String reason,
      LocalDate expiresOn) {
    return adjust(
        storeId, bearerToken, customerId, delta, reason, expiresOn, UUID.randomUUID().toString());
  }

  private ResponseEntity<JsonNode> adjust(
      long storeId,
      String bearerToken,
      String customerId,
      int delta,
      String reason,
      LocalDate expiresOn,
      String idempotencyKey) {
    String body =
        "{\"delta\": "
            + delta
            + ", \"reason\": \""
            + reason
            + "\""
            + (expiresOn == null ? "" : ", \"expires_on\": \"" + expiresOn + "\"")
            + ", \"idempotency_key\": \""
            + idempotencyKey
            + "\"}";
    return rest.exchange(
        "/store/customers/" + customerId + "/point-adjustments",
        HttpMethod.POST,
        new HttpEntity<>(body, headersFor(storeId, bearerToken)),
        JsonNode.class);
  }

  // ==================== 顧客・会員・台帳 ====================

  /** 新しい会員に紐づいた店舗A の顧客を作ってその ID を返す。 */
  private String linkedCustomer(String bearerToken, String label, String memberCode) {
    String customerId = createCustomerAs(STORE_A, bearerToken, label + "-" + nonce);
    ResponseEntity<JsonNode> linked =
        rest.exchange(
            "/store/customers/" + customerId + "/member-link",
            HttpMethod.POST,
            new HttpEntity<>(
                "{\"member_code\": \"" + memberCode + "\"}", headersFor(STORE_A, bearerToken)),
            JsonNode.class);
    assertThat(linked.getStatusCode()).as("前提: 会員の紐づけが成功すること").isEqualTo(HttpStatus.OK);
    return customerId;
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

  /** 新しい会員を登録してその会員コードを返す。 */
  private String registerMember(String prefix) {
    String email = prefix + "-point-it-" + nonce + "-" + System.nanoTime() + "@kizuna.test";
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
                    + "\", \"display_name\": \"ポイント調整検証会員\"}",
                headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);
    return res.getBody().path("member_code").asString();
  }

  /** その会員の台帳の全仕訳。 */
  private List<PointEntry> entriesOf(String memberCode) {
    long memberId = memberRepository.findByMemberCode(memberCode).map(Member::getId).orElseThrow();
    return pointEntryRepository.findAll().stream()
        .filter(entry -> entry.getMemberId() == memberId)
        .toList();
  }

  /** 2 つの呼出を同時に開始して両方の応答を返す。 */
  private List<ResponseEntity<JsonNode>> inParallel(
      Callable<ResponseEntity<JsonNode>> first, Callable<ResponseEntity<JsonNode>> second)
      throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch start = new CountDownLatch(1);
      Future<ResponseEntity<JsonNode>> a =
          pool.submit(
              () -> {
                start.await();
                return first.call();
              });
      Future<ResponseEntity<JsonNode>> b =
          pool.submit(
              () -> {
                start.await();
                return second.call();
              });
      start.countDown();
      return List.of(a.get(), b.get());
    } finally {
      pool.shutdownNow();
    }
  }

  // ==================== 認証 ====================

  private String loginAs(String email) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>("{\"email\": \"" + email + "\", \"password\": \"pass\"}", headers),
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
