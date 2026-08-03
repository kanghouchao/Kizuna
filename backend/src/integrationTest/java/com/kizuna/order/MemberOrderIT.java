package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 会員の予約申請から店舗確定までを本物の PostgreSQL で検証する統合テスト。
 *
 * <p>会員は店舗を授権されず storeFilter が働かないため、会員向け経路の隔離は問い合わせに載せた申請者の一致だけが担う。 そこで「他会員の予約が生ボディに一切現れないこと」を
 * カナリアで強く見る（帰属不一致の確認では、実データが混ざっていないことの証明にならない）。
 */
class MemberOrderIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  /** 他会員の予約だけが持つ、他に一致しようがない備考。 */
  private static final String CANARY_REMARKS = "MEMBER-ORDER-CANARY-4f2b91c7";

  private String memberAToken;
  private String memberBToken;

  @BeforeEach
  void registerTwoMembers() {
    memberAToken = registerAndLogin("会員A");
    memberBToken = registerAndLogin("会員B");
  }

  private String registerAndLogin(String displayName) {
    String email = "member-order-it-" + System.nanoTime() + "@kizuna.test";
    ResponseEntity<JsonNode> registration =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \""
                    + email
                    + "\", \"password\": \""
                    + PASSWORD
                    + "\", \"display_name\": \""
                    + displayName
                    + "\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(registration.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);

    ResponseEntity<JsonNode> login =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(login.getStatusCode()).as("前提: 会員としてログインできること").isEqualTo(HttpStatus.OK);
    String token = login.getBody().path("token").asString();
    assertThat(token).isNotBlank();
    return token;
  }

  private String requestReservation(String memberToken, long storeId, String remarks) {
    ResponseEntity<JsonNode> requested =
        rest.postForEntity(
            "/platform/me/orders",
            new HttpEntity<>(
                "{\"store_id\": "
                    + storeId
                    + ", \"business_date\": \""
                    + LocalDate.now()
                    + "\", \"pax\": 3, \"remarks\": \""
                    + remarks
                    + "\"}",
                bearer(memberToken)),
            JsonNode.class);
    assertThat(requested.getStatusCode()).as("前提: 予約申請が成功すること").isEqualTo(HttpStatus.CREATED);
    String id = requested.getBody().path("id").asString();
    assertThat(id).isNotBlank();
    return id;
  }

  @Test
  @DisplayName("会員の申請が Web 受付の未確定受注として起き、店舗の確定で同じ受注が確定になること")
  void requestBecomesConfirmedOrder() {
    String orderId = requestReservation(memberAToken, STORE_A, "統合テスト申請");

    // 店舗側から見ると申請は未確定（CREATED）の Web 受付として現れる
    ResponseEntity<JsonNode> storeView =
        rest.exchange(
            "/store/orders/" + orderId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(storeView.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(storeView.getBody().path("status").asString()).isEqualTo("CREATED");
    assertThat(storeView.getBody().path("reception_route").asString()).isEqualTo("WEB");
    assertThat(storeView.getBody().path("pax").asInt()).isEqualTo(3);
    assertThat(storeView.getBody().path("requester_member_code").asString()).isNotBlank();

    ResponseEntity<JsonNode> confirmed =
        rest.exchange(
            "/store/orders/" + orderId + "/confirmation",
            HttpMethod.POST,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(confirmed.getBody().path("status").asString()).isEqualTo("CONFIRMED");
    assertThat(confirmed.getBody().path("receptionist_id").asLong())
        .as("確定した店舗スタッフが受付担当として補われること")
        .isPositive();
    assertThat(confirmed.getBody().path("requester_member_code").asString())
        .as("確定後も申請者が追跡できること")
        .isNotBlank();

    // 会員側の一覧でも確定として見える（申請と受注が同一の行であることの現れ）
    JsonNode own = firstReservation(memberAToken);
    assertThat(own.path("id").asString()).isEqualTo(orderId);
    assertThat(own.path("status").asString()).isEqualTo("CONFIRMED");
    assertThat(own.path("store_name").asString()).isNotBlank();
  }

  @Test
  @DisplayName("会員の一覧に他会員の予約が一切現れないこと")
  void memberListNeverExposesOtherMembersReservations() {
    String canaryOrderId = requestReservation(memberBToken, STORE_A, CANARY_REMARKS);

    // 正向対照: カナリアは店舗側からは確かに読める（断言対象が「漏れうるデータ」であることの証明）
    ResponseEntity<String> storeView =
        rest.exchange(
            "/store/orders/" + canaryOrderId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            String.class);
    assertThat(storeView.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(storeView.getBody()).contains(CANARY_REMARKS);

    requestReservation(memberAToken, STORE_A, "会員Aの申請");

    ResponseEntity<String> list =
        rest.exchange(
            "/platform/me/orders",
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberAToken)),
            String.class);
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(list.getBody())
        .as("他会員の予約の実データ・ID が生ボディに現れないこと")
        .doesNotContain(CANARY_REMARKS)
        .doesNotContain(canaryOrderId);
  }

  @Test
  @DisplayName("確定前は本人が取り下げられ、確定後は取り下げられないこと")
  void memberCanWithdrawOnlyBeforeConfirmation() {
    String orderId = requestReservation(memberAToken, STORE_A, "取り下げ対象");

    ResponseEntity<JsonNode> cancelled =
        rest.exchange(
            "/platform/me/orders/" + orderId + "/cancellation",
            HttpMethod.POST,
            new HttpEntity<>(bearer(memberAToken)),
            JsonNode.class);
    assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(cancelled.getBody().path("status").asString()).isEqualTo("CANCELLED");

    String confirmedId = requestReservation(memberAToken, STORE_A, "確定後は取り下げ不可");
    ResponseEntity<JsonNode> confirmed =
        rest.exchange(
            "/store/orders/" + confirmedId + "/confirmation",
            HttpMethod.POST,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<JsonNode> tooLate =
        rest.exchange(
            "/platform/me/orders/" + confirmedId + "/cancellation",
            HttpMethod.POST,
            new HttpEntity<>(bearer(memberAToken)),
            JsonNode.class);
    assertThat(tooLate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("他会員の予約は取り下げられず、存在も明かさないこと")
  void memberCannotWithdrawAnotherMembersReservation() {
    String othersOrderId = requestReservation(memberBToken, STORE_A, "他会員の予約");

    ResponseEntity<JsonNode> denied =
        rest.exchange(
            "/platform/me/orders/" + othersOrderId + "/cancellation",
            HttpMethod.POST,
            new HttpEntity<>(bearer(memberAToken)),
            JsonNode.class);
    assertThat(denied.getStatusCode())
        .as("権限違反ではなく不在として扱うこと（予約の存在を明かさない）")
        .isEqualTo(HttpStatus.NOT_FOUND);

    // 会員B の予約は健在
    JsonNode own = firstReservation(memberBToken);
    assertThat(own.path("id").asString()).isEqualTo(othersOrderId);
    assertThat(own.path("status").asString()).isEqualTo("CREATED");
  }

  @Test
  @DisplayName("店舗が謝絶した申請が会員側でキャンセルとして見えること")
  void declinedRequestAppearsCancelledToMember() {
    String orderId = requestReservation(memberAToken, STORE_A, "謝絶対象");

    ResponseEntity<JsonNode> declined =
        rest.exchange(
            "/store/orders/" + orderId + "/decline",
            HttpMethod.POST,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(declined.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode own = firstReservation(memberAToken);
    assertThat(own.path("id").asString()).isEqualTo(orderId);
    assertThat(own.path("status").asString()).isEqualTo("CANCELLED");
  }

  // トークンを持たない要求は認証の失敗ではなく認可の拒否として扱われる（401 は失効・改竄トークンの側）。
  @Test
  @DisplayName("匿名では会員の予約経路に到達できないこと")
  void anonymousCannotReachMemberReservationRoutes() {
    ResponseEntity<JsonNode> anonymous =
        rest.exchange(
            "/platform/me/orders", HttpMethod.GET, new HttpEntity<>(jsonHeaders()), JsonNode.class);
    assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("壊れた Bearer トークンでは会員の予約経路が 401 になること")
  void brokenBearerIsUnauthorized() {
    ResponseEntity<JsonNode> broken =
        rest.exchange(
            "/platform/me/orders",
            HttpMethod.GET,
            new HttpEntity<>(bearer("not-a-real-token")),
            JsonNode.class);
    assertThat(broken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("店舗スタッフは会員の予約経路に到達できないこと")
  void storeStaffCannotReachMemberReservationRoutes() {
    ResponseEntity<JsonNode> staff =
        rest.exchange(
            "/platform/me/orders", HttpMethod.GET, new HttpEntity<>(bearer(token)), JsonNode.class);
    assertThat(staff.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  /** 会員本人の一覧の先頭（業務日降順・id 降順のため、最後に申請したものが先頭に来る）。 */
  private JsonNode firstReservation(String memberToken) {
    ResponseEntity<JsonNode> list =
        rest.exchange(
            "/platform/me/orders",
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberToken)),
            JsonNode.class);
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode first = list.getBody().path("content").path(0);
    assertThat(first.isObject()).as("前提: 一覧に予約が現れること").isTrue();
    return first;
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
