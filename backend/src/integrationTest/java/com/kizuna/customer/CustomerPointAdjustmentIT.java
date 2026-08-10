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

  private ResponseEntity<JsonNode> adjust(
      long storeId,
      String bearerToken,
      String customerId,
      int delta,
      String reason,
      LocalDate expiresOn) {
    String body =
        "{\"delta\": "
            + delta
            + ", \"reason\": \""
            + reason
            + "\""
            + (expiresOn == null ? "" : ", \"expires_on\": \"" + expiresOn + "\"")
            + "}";
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
