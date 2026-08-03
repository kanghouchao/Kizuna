package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.order.domain.OrderRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
import tools.jackson.databind.JsonNode;

/**
 * 会員の予約申請から店舗確定までを本物の PostgreSQL で検証する統合テスト。
 *
 * <p>会員は店舗を授権されず storeFilter が働かないため、会員向け経路の隔離は問い合わせに載せた申請者の一致だけが担う。 そこで「他会員の予約が生ボディに一切現れないこと」を
 * カナリアで強く見る（帰属不一致の確認では、実データが混ざっていないことの証明にならない）。
 */
class MemberOrderIT extends CrossStoreTestSupport {

  @Autowired private OrderRepository orderRepository;

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
                    + LocalDate.now(ZoneId.of("Asia/Tokyo"))
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
  @DisplayName("予約受付 inbox には会員の未確定申請だけが現れること")
  void reservationRequestInboxHoldsOnlyPendingMemberRequests() {
    String requestId = requestReservation(memberAToken, STORE_A, "inbox 対象");

    // 店舗が手入力した受注に受付経路 WEB を付けても、申請ではないため inbox には現れてはならない。
    String castId = createCastAs(STORE_A, "inbox 判定用キャスト");
    String staffOrderId = createStoreOrderTaggedWeb(castId);

    ResponseEntity<JsonNode> inbox =
        rest.exchange(
            "/store/orders/reservation-requests",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(inbox.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> ids = new ArrayList<>();
    inbox.getBody().path("content").forEach(node -> ids.add(node.path("id").asString()));
    assertThat(ids).as("会員の未確定申請は現れること").contains(requestId);
    assertThat(ids).as("店舗が起こした受注は受付経路が WEB でも現れないこと").doesNotContain(staffOrderId);

    // 確定したものは処理済みなので inbox から外れる
    rest.exchange(
        "/store/orders/" + requestId + "/confirmation",
        HttpMethod.POST,
        new HttpEntity<>(storeHeaders(STORE_A)),
        JsonNode.class);
    ResponseEntity<JsonNode> afterConfirm =
        rest.exchange(
            "/store/orders/reservation-requests",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    List<String> remaining = new ArrayList<>();
    afterConfirm
        .getBody()
        .path("content")
        .forEach(node -> remaining.add(node.path("id").asString()));
    assertThat(remaining).doesNotContain(requestId);
  }

  @Test
  @DisplayName("取得件数の上限を超えた未処理の申請にも、ページを辿れば到達できること")
  void inboxPagesThroughEveryPendingRequest() {
    List<String> requested =
        List.of(
            requestReservation(memberAToken, STORE_A, "ページング 1"),
            requestReservation(memberAToken, STORE_A, "ページング 2"),
            requestReservation(memberAToken, STORE_A, "ページング 3"));

    // 1 件ずつしか返さない窓でも、総ページ数を辿ればすべての未処理の申請に届く。
    List<String> collected = new ArrayList<>();
    int page = 0;
    int totalPages;
    do {
      ResponseEntity<JsonNode> inbox =
          rest.exchange(
              "/store/orders/reservation-requests?page=" + page + "&size=1",
              HttpMethod.GET,
              new HttpEntity<>(storeHeaders(STORE_A)),
              JsonNode.class);
      assertThat(inbox.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode body = inbox.getBody();
      assertThat(body.path("content").size()).as("1 回の取得件数が上限で抑えられること").isLessThanOrEqualTo(1);
      body.path("content").forEach(node -> collected.add(node.path("id").asString()));
      totalPages = body.path("total_pages").asInt();
      page++;
    } while (page < totalPages);

    assertThat(collected).as("未処理の申請がすべて取得窓に現れること").containsAll(requested);
  }

  private String createCastAs(long storeId, String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", storeHeaders(storeId)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: キャスト作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  /** 店舗スタッフが手入力し、受付経路だけ WEB を付けた受注（申請ではない）。 */
  private String createStoreOrderTaggedWeb(String castId) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/orders",
            new HttpEntity<>(
                "{\"receptionist_id\": 3, \"business_date\": \""
                    + LocalDate.now(ZoneId.of("Asia/Tokyo"))
                    + "\", \"cast_id\": \""
                    + castId
                    + "\", \"reception_route\": \"WEB\"}",
                storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 店舗側の受注作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  @Test
  @DisplayName("会員行が消えても未確定の申請は inbox に残り、処理し終えられること")
  void inboxKeepsPendingRequestAfterTheMemberRowIsGone() {
    String orderId = requestReservation(memberAToken, STORE_A, "会員削除後も処理する申請");

    // 会員行の削除を DB 側の FK（SET NULL）と同じ形で再現する。会員コードのスナップショットは残る。
    orderRepository
        .findById(orderId)
        .ifPresent(
            order -> {
              order.detachRequesterMember();
              orderRepository.save(order);
            });

    ResponseEntity<JsonNode> inbox =
        rest.exchange(
            "/store/orders/reservation-requests",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(inbox.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> ids = new ArrayList<>();
    inbox.getBody().path("content").forEach(node -> ids.add(node.path("id").asString()));
    assertThat(ids).as("会員 ID が欠落しても未確定の申請は処理対象として残ること").contains(orderId);

    ResponseEntity<JsonNode> confirmed =
        rest.exchange(
            "/store/orders/" + orderId + "/confirmation",
            HttpMethod.POST,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(confirmed.getBody().path("status").asString()).isEqualTo("CONFIRMED");
  }

  @Test
  @DisplayName("申請後に台帳へ紐づけた会員でも、確定した受注が顧客の受注履歴に載ること")
  void confirmationAttachesCustomerLinkedAfterTheRequest() {
    // 初回来店の順序: 申請（このとき紐づけは無い）→ 店舗が会員コードを読んで台帳に紐づけ → 確定
    String orderId = requestReservation(memberAToken, STORE_A, "初回来店の申請");
    String memberCode = firstReservationMemberCode(memberAToken, orderId);

    ResponseEntity<JsonNode> customer =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>("{\"name\": \"初回来店の会員\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(customer.getStatusCode().is2xxSuccessful()).as("前提: 顧客作成が成功すること").isTrue();
    String customerId = customer.getBody().path("id").asString();

    ResponseEntity<JsonNode> linked =
        rest.exchange(
            "/store/customers/" + customerId + "/member-link",
            HttpMethod.POST,
            new HttpEntity<>("{\"member_code\": \"" + memberCode + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(linked.getStatusCode()).as("前提: 紐づけが成功すること").isEqualTo(HttpStatus.OK);

    rest.exchange(
        "/store/orders/" + orderId + "/confirmation",
        HttpMethod.POST,
        new HttpEntity<>(storeHeaders(STORE_A)),
        JsonNode.class);

    ResponseEntity<JsonNode> history =
        rest.exchange(
            "/store/orders?customer_id=" + customerId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> ids = new ArrayList<>();
    history.getBody().path("content").forEach(node -> ids.add(node.path("id").asString()));
    assertThat(ids).as("確定した申請が顧客の受注履歴に現れること").contains(orderId);
  }

  /** 店舗側から見た申請の会員コード（紐づけ操作に使う）。 */
  private String firstReservationMemberCode(String memberToken, String orderId) {
    ResponseEntity<JsonNode> storeView =
        rest.exchange(
            "/store/orders/" + orderId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    String code = storeView.getBody().path("requester_member_code").asString();
    assertThat(code).as("前提: 店舗側から申請者の会員コードが読めること").isNotBlank();
    return code;
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
  @DisplayName("申請の状態は汎用更新（PUT）では変更できず、専用の確定操作でのみ変わること")
  void genericUpdateCannotTransitionReservationRequests() {
    String orderId = requestReservation(memberAToken, STORE_A, "汎用更新の対象外");
    String castId = createCastAs(STORE_A, "汎用更新ガード用キャスト");

    // 汎用更新で CONFIRMED を送っても、確定時の指名再検証・顧客補完を迂回して遷移はできない
    ResponseEntity<JsonNode> tampered =
        rest.exchange(
            "/store/orders/" + orderId,
            HttpMethod.PUT,
            new HttpEntity<>(
                "{\"receptionist_id\": 3, \"cast_id\": \""
                    + castId
                    + "\", \"status\": \"CONFIRMED\"}",
                storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(tampered.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // 申請は未確定のまま残り、専用の確定操作では引き続き処理できる
    ResponseEntity<JsonNode> confirmed =
        rest.exchange(
            "/store/orders/" + orderId + "/confirmation",
            HttpMethod.POST,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(confirmed.getBody().path("status").asString()).isEqualTo("CONFIRMED");
  }

  @Test
  @DisplayName("他店舗は会員の申請を確定も謝絶もできず、申請はその後も処理できること")
  void otherStoreCannotConfirmOrDeclineForeignApplication() {
    String orderId = requestReservation(memberAToken, STORE_A, "他店舗からの操作対象外");

    ResponseEntity<JsonNode> foreignConfirm =
        rest.exchange(
            "/store/orders/" + orderId + "/confirmation",
            HttpMethod.POST,
            new HttpEntity<>(storeHeaders(STORE_B)),
            JsonNode.class);
    assertThat(foreignConfirm.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<JsonNode> foreignDecline =
        rest.exchange(
            "/store/orders/" + orderId + "/decline",
            HttpMethod.POST,
            new HttpEntity<>(storeHeaders(STORE_B)),
            JsonNode.class);
    assertThat(foreignDecline.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // 正向対照: 申請先の店舗ではそのまま確定できる（負向がルーティング起因でない証明）
    ResponseEntity<JsonNode> ownConfirm =
        rest.exchange(
            "/store/orders/" + orderId + "/confirmation",
            HttpMethod.POST,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(ownConfirm.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(ownConfirm.getBody().path("status").asString()).isEqualTo("CONFIRMED");
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
