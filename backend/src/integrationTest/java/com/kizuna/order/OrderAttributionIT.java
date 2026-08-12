package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderAttributionSource;
import com.kizuna.order.domain.OrderAttributionStatus;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
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
import tools.jackson.databind.JsonNode;

/**
 * 受注帰属記録を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>固定するのは「会員の来店可視性はこの記録だけから読める」ための不変量 — 完了時に会員へ達した受注は必ず記録を得ること、 会計金額 0
 * でも記録は生まれること（帰属はポイントと独立）、達しなかった受注は記録を得ないこと、そして 一度確定した記録が関連の解除や会員の削除で失われないこと。
 *
 * <p>シード設定は「100 円ごとに 1 ポイント付与、利用は 100 ポイント単位」。
 */
class OrderAttributionIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  /** v0.5.0 の山田次郎シード（STORE_STAFF・授権店舗 = 店舗1）。店舗A の受付担当として使用。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  private static final int TOTAL_FEE = 12000;

  @Autowired private OrderAttributionRepository orderAttributionRepository;
  @Autowired private PointEntryRepository pointEntryRepository;
  @Autowired private MemberRepository memberRepository;

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("会員に紐づく顧客の受注を完了すると、根拠 COMPLETION の帰属記録が生まれること")
  void completingAMemberOrderRecordsTheAttribution() {
    String memberCode = registerMember("completion");
    String customerId = createCustomer("完了帰属");
    linkMember(customerId, memberCode);
    String orderId = confirmedOrder(customerId, "完了帰属");

    assertThat(complete(orderId, TOTAL_FEE, null).getStatusCode()).isEqualTo(HttpStatus.OK);

    OrderAttribution attribution = attributionOf(orderId);
    assertThat(attribution.getMemberId()).isEqualTo(memberIdOf(memberCode));
    assertThat(attribution.getMemberCode()).isEqualTo(memberCode);
    assertThat(attribution.getSource()).isEqualTo(OrderAttributionSource.COMPLETION);
    assertThat(attribution.getStatus()).isEqualTo(OrderAttributionStatus.ACTIVE);
    assertThat(attribution.getAttributedAt()).isNotNull();
  }

  @Test
  @DisplayName("撥ねられた完了は帰属記録も台帳の付与も残さず、通った完了は両方を残すこと")
  void rejectedCompletionLeavesNeitherAttributionNorLedgerRow() {
    String memberCode = registerMember("rejected");
    String customerId = createCustomer("撥ねた完了");
    linkMember(customerId, memberCode);
    String orderId = confirmedOrder(customerId, "撥ねた完了");

    // 残高 0 の会員に 100 ポイントの利用を要求する
    assertThat(complete(orderId, TOTAL_FEE, 100).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    assertThat(orderAttributionRepository.findAll())
        .as("撥ねた完了は帰属記録を残さないこと")
        .noneMatch(row -> orderId.equals(row.getOrderId()));
    assertThat(pointEntryRepository.findCredits(memberIdOf(memberCode)))
        .as("撥ねた完了は台帳へ何も積まないこと")
        .isEmpty();

    // 正向対照: 利用を指定しなければ同じ受注が完了し、記録と付与が揃って生まれる
    assertThat(complete(orderId, TOTAL_FEE, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(attributionOf(orderId).getMemberCode()).isEqualTo(memberCode);
    assertThat(pointEntryRepository.findCredits(memberIdOf(memberCode))).hasSize(1);
  }

  @Test
  @DisplayName("0 円完了でも帰属記録が生まれ、台帳には付与行が積まれないこと")
  void zeroFeeCompletionStillRecordsTheAttribution() {
    String memberCode = registerMember("zerofee");
    String customerId = createCustomer("0円完了");
    linkMember(customerId, memberCode);
    String orderId = confirmedOrder(customerId, "0円完了");

    ResponseEntity<JsonNode> completed = complete(orderId, 0, null);

    assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(completed.getBody().path("auto_grant_points").asInt()).isZero();
    // 帰属は来店可視性の事実であり、ポイントの有無とは独立している
    assertThat(attributionOf(orderId).getMemberCode()).isEqualTo(memberCode);
    assertThat(pointEntryRepository.findCredits(memberIdOf(memberCode)))
        .as("付与 0 は台帳へ行を書かないこと")
        .isEmpty();
  }

  @Test
  @DisplayName("関連を解除しても確定済みの帰属記録が残ること（解除は以後にだけ働く）")
  void releasingTheLinkKeepsTheAttribution() {
    String memberCode = registerMember("release");
    String customerId = createCustomer("解除後");
    linkMember(customerId, memberCode);
    String orderId = confirmedOrder(customerId, "解除後");
    assertThat(complete(orderId, TOTAL_FEE, null).getStatusCode())
        .as("前提: 帰属記録の生まれる完了ができること")
        .isEqualTo(HttpStatus.OK);

    ResponseEntity<Void> unlinked =
        rest.exchange(
            "/store/customers/" + customerId + "/member-link",
            HttpMethod.DELETE,
            new HttpEntity<>(storeHeaders(STORE_A)),
            Void.class);
    assertThat(unlinked.getStatusCode().is2xxSuccessful()).as("前提: 関連を解除できること").isTrue();

    OrderAttribution attribution = attributionOf(orderId);
    assertThat(attribution.getMemberId()).isEqualTo(memberIdOf(memberCode));
    assertThat(attribution.getMemberCode()).isEqualTo(memberCode);
    assertThat(attribution.getStatus()).isEqualTo(OrderAttributionStatus.ACTIVE);
  }

  @Test
  @DisplayName("非会員顧客の完了と無帰属受注の完了では帰属記録が生まれないこと")
  void completionThatReachesNoMemberRecordsNothing() {
    String nonMemberOrder = confirmedOrder(createCustomer("非会員"), "非会員");
    String unattributedOrder = confirmedOrder(null, "無帰属");

    assertThat(complete(nonMemberOrder, TOTAL_FEE, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(complete(unattributedOrder, TOTAL_FEE, null).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    assertThat(orderAttributionRepository.findAll())
        .as("顧客経路が会員に達しない完了は記録を作らないこと")
        .noneMatch(
            row ->
                nonMemberOrder.equals(row.getOrderId())
                    || unattributedOrder.equals(row.getOrderId()));
  }

  @Test
  @DisplayName("会員を削除しても帰属記録は残り、会員参照だけが欠落してスナップショットは保たれること")
  void deletingTheMemberKeepsTheAttributionAndItsSnapshot() {
    String memberCode = registerMember("deleted");
    String customerId = createCustomer("会員削除");
    linkMember(customerId, memberCode);
    String orderId = confirmedOrder(customerId, "会員削除");
    assertThat(complete(orderId, TOTAL_FEE, null).getStatusCode())
        .as("前提: 帰属記録の生まれる完了ができること")
        .isEqualTo(HttpStatus.OK);

    memberRepository.deleteById(memberIdOf(memberCode));

    OrderAttribution attribution = attributionOf(orderId);
    assertThat(attribution.getMemberId()).as("会員参照は FK の SET NULL で欠落すること").isNull();
    assertThat(attribution.getMemberCode()).as("誰の来店だったかはスナップショットで辿れること").isEqualTo(memberCode);
  }

  @Test
  @DisplayName("有効な帰属は受注 1 件につき高々 1 件であること（部分一意索引が機械強制する）")
  void anOrderCannotCarryTwoActiveAttributions() {
    String memberCode = registerMember("unique");
    String customerId = createCustomer("二重帰属");
    linkMember(customerId, memberCode);
    String orderId = confirmedOrder(customerId, "二重帰属");
    assertThat(complete(orderId, TOTAL_FEE, null).getStatusCode())
        .as("前提: 1 件目の帰属記録が生まれること")
        .isEqualTo(HttpStatus.OK);

    OrderAttribution duplicate =
        OrderAttribution.onCompletion(
            orderId, memberIdOf(memberCode), memberCode, OffsetDateTime.now());

    assertThatThrownBy(() -> orderAttributionRepository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ==================== 帰属記録の読み出し ====================

  private OrderAttribution attributionOf(String orderId) {
    List<OrderAttribution> found =
        orderAttributionRepository.findAll().stream()
            .filter(row -> orderId.equals(row.getOrderId()))
            .toList();
    assertThat(found).as("受注 %s の帰属記録が 1 件だけ生まれること", orderId).hasSize(1);
    return found.get(0);
  }

  // ==================== 受注の用意 ====================

  private String confirmedOrder(String customerId, String label) {
    String castId = createCast(label);
    String orderId = createOrder(castId, customerId, label);
    assertThat(updateStatus(orderId, castId, "CONFIRMED")).as("前提: 受注を確定できること").isTrue();
    return orderId;
  }

  private String createOrder(String castId, String customerId, String remarks) {
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
            new HttpEntity<>("{\"name\": \"" + name + "-" + nonce + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: キャスト作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private String createCustomer(String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>("{\"name\": \"" + name + "-" + nonce + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 顧客作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private ResponseEntity<JsonNode> complete(String orderId, int totalFee, Integer usePoints) {
    String body =
        "{\"total_fee\": "
            + totalFee
            + (usePoints == null ? "" : ", \"use_points\": " + usePoints)
            + "}";
    return rest.exchange(
        "/store/orders/" + orderId + "/completion",
        HttpMethod.POST,
        new HttpEntity<>(body, storeHeaders(STORE_A)),
        JsonNode.class);
  }

  // ==================== 会員・関連 ====================

  private String registerMember(String prefix) {
    String email = prefix + "-attribution-it-" + nonce + "-" + System.nanoTime() + "@kizuna.test";
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
                    + "\", \"display_name\": \"帰属記録検証会員\"}",
                headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);
    return res.getBody().path("member_code").asString();
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

  private long memberIdOf(String memberCode) {
    return memberRepository.findByMemberCode(memberCode).map(Member::getId).orElseThrow();
  }
}
