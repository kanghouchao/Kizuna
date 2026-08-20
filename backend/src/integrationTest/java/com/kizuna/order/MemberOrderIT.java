package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.order.domain.OrderApplication;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.domain.OrderApplicationStatus;
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
 * <p>申請は受注と別の付随記録（t_order_applications）で、店舗の確定が CONFIRMED の受注を生成して order_id を回写する（ADR 0017）。
 *
 * <p>会員は店舗を授権されず storeFilter が働かないため、会員向け経路の隔離は問い合わせに載せた申請者の一致だけが担う。 そこで「他会員の申請が生ボディに一切現れないこと」を
 * カナリアで強く見る（帰属不一致の確認では、実データが混ざっていないことの証明にならない）。
 */
class MemberOrderIT extends CrossStoreTestSupport {

  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderApplicationRepository orderApplicationRepository;

  private static final String PASSWORD = "password1234";

  /** 他会員の申請だけが持つ、他に一致しようがない備考。 */
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

  private static LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Tokyo"));
  }

  private String requestReservation(String memberToken, long storeId, String remarks) {
    ResponseEntity<JsonNode> requested =
        rest.postForEntity(
            "/platform/me/order-applications",
            new HttpEntity<>(
                "{\"store_id\": "
                    + storeId
                    + ", \"business_date\": \""
                    + today()
                    + "\", \"pax\": 3, \"declared_name\": \"名乗り太郎\", \"remarks\": \""
                    + remarks
                    + "\"}",
                bearer(memberToken)),
            JsonNode.class);
    assertThat(requested.getStatusCode()).as("前提: 予約申請が成功すること").isEqualTo(HttpStatus.CREATED);
    String id = requested.getBody().path("id").asString();
    assertThat(id).isNotBlank();
    return id;
  }

  /** 店舗の確定操作。内容の調整が主題でないテストは、申請どおりの内容（当日・3 名）で確定する。 */
  private ResponseEntity<JsonNode> confirm(long storeId, String applicationId) {
    return confirm(storeId, applicationId, "{\"business_date\": \"" + today() + "\", \"pax\": 3}");
  }

  private ResponseEntity<JsonNode> confirm(long storeId, String applicationId, String body) {
    return rest.exchange(
        "/store/order-applications/" + applicationId + "/confirmation",
        HttpMethod.POST,
        new HttpEntity<>(body, storeHeaders(storeId)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> decline(long storeId, String applicationId, String reason) {
    return rest.exchange(
        "/store/order-applications/" + applicationId + "/refusal",
        HttpMethod.POST,
        new HttpEntity<>("{\"reason\": \"" + reason + "\"}", storeHeaders(storeId)),
        JsonNode.class);
  }

  @Test
  @DisplayName("会員の申請は申請行だけを起こし、受注には行が生まれないこと")
  void requestCreatesAnApplicationRowAndNoOrderRow() {
    String applicationId = requestReservation(memberAToken, STORE_A, "統合テスト申請");

    OrderApplication application = orderApplicationRepository.findById(applicationId).orElseThrow();
    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.PENDING);
    assertThat(application.getOrderId()).as("申請時点では受注が生まれていないこと").isNull();
    assertThat(application.getRequesterMemberCode()).isNotBlank();
    assertThat(orderRepository.findById(applicationId))
        .as("申請の id が受注の空間に現れないこと（別記録である）")
        .isEmpty();

    // 店舗の受注読み口からも申請には到達できない（申請は受注の前室で、受注ではない）
    ResponseEntity<JsonNode> asOrder =
        rest.exchange(
            "/store/orders/" + applicationId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(asOrder.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("店舗確定が申請内容を基に CONFIRMED の受注を生成し、申請行へ order_id を回写し、申請原文が不変のまま対照できること")
  void confirmationCreatesAConfirmedOrderAndWritesItBack() {
    String applicationId = requestReservation(memberAToken, STORE_A, "確定対象の申請");

    // 店舗は確定時に内容を調整できる（人数 3 → 5）。申請原文はそのまま残る
    ResponseEntity<JsonNode> confirmed =
        confirm(
            STORE_A,
            applicationId,
            "{\"business_date\": \"" + today() + "\", \"pax\": 5, \"remarks\": \"店舗が調整した\"}");
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String orderId = confirmed.getBody().path("id").asString();
    assertThat(orderId).isNotBlank();
    assertThat(confirmed.getBody().path("status").asString()).isEqualTo("CONFIRMED");
    assertThat(confirmed.getBody().path("reception_route").asString()).isEqualTo("WEB");
    assertThat(confirmed.getBody().path("pax").asInt()).as("受注は確定内容を持つこと").isEqualTo(5);
    assertThat(confirmed.getBody().path("receptionist_id").asLong())
        .as("確定した店舗スタッフが受付担当として補われること")
        .isPositive();
    assertThat(confirmed.getBody().path("requester_member_code").asString())
        .as("確定後も申請者が追跡できること")
        .isNotBlank();

    OrderApplication application = orderApplicationRepository.findById(applicationId).orElseThrow();
    assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.CONFIRMED);
    assertThat(application.getOrderId()).as("申請行へ受注の id が回写されること").isEqualTo(orderId);
    assertThat(application.getPax()).as("申請原文（人数 3）は確定の調整で書き換わらないこと").isEqualTo(3);
    assertThat(application.getRemarks()).isEqualTo("確定対象の申請");

    // 会員側の一覧では申請が確定として見える
    JsonNode own = firstReservation(memberAToken);
    assertThat(own.path("id").asString()).isEqualTo(applicationId);
    assertThat(own.path("status").asString()).isEqualTo("CONFIRMED");
    assertThat(own.path("store_name").asString()).isNotBlank();
  }

  @Test
  @DisplayName("受付箱には未処理の申請だけが現れ、処理し終えると外れること")
  void inboxHoldsPendingApplicationsUntilTheyAreProcessed() {
    String applicationId = requestReservation(memberAToken, STORE_A, "受付箱の対象");

    List<String> ids = allPendingIds();
    assertThat(ids).as("未処理の申請は現れること").contains(applicationId);

    ResponseEntity<JsonNode> confirmed = confirm(STORE_A, applicationId);
    assertThat(confirmed.getStatusCode()).as("前提: 確定が成功すること").isEqualTo(HttpStatus.CREATED);
    assertThat(allPendingIds()).doesNotContain(applicationId);
  }

  @Test
  @DisplayName("取得件数の上限を超えた未処理の申請にも、続きを辿れば到達できること")
  void inboxPagesThroughEveryPendingApplication() {
    List<String> requested =
        List.of(
            requestReservation(memberAToken, STORE_A, "ページング 1"),
            requestReservation(memberAToken, STORE_A, "ページング 2"),
            requestReservation(memberAToken, STORE_A, "ページング 3"));

    // 1 件ずつしか返さない窓でも、続きの位置を辿ればすべての未処理の申請に届く。到達性は
    // サーバ側のページ上限に依らない（位置は件数ではなく並びの鍵で指すため）。
    List<String> collected = new ArrayList<>();
    String cursor = null;
    do {
      JsonNode body = fetchInbox(cursor, 1);
      assertThat(body.path("content").size()).as("1 回の取得件数が上限で抑えられること").isLessThanOrEqualTo(1);
      collected.addAll(idsOf(body));
      cursor = nextCursor(body);
    } while (cursor != null && collected.size() <= PAGING_GUARD);

    assertThat(collected).as("未処理の申請がすべて取得窓に現れること").containsAll(requested);
  }

  @Test
  @DisplayName("読み込み済みの申請を確定した直後に続きを取っても、境界の申請を飛ばさないこと")
  void inboxDoesNotSkipTheBoundaryApplicationAfterOneIsConfirmed() {
    String first = requestReservation(memberAToken, STORE_A, "境界 1");
    String second = requestReservation(memberAToken, STORE_A, "境界 2");
    String third = requestReservation(memberAToken, STORE_A, "境界 3");

    // first まで読み進めた状態を作る（続きの位置は first を指す）。
    String cursor = cursorAfter(first);

    // 読み込み済みの範囲にある申請を処理する。位置を「何件目か」で指す取得だと、ここで後続が繰り上がる。
    ResponseEntity<JsonNode> confirmed = confirm(STORE_A, first);
    assertThat(confirmed.getStatusCode()).as("前提: 確定が成功すること").isEqualTo(HttpStatus.CREATED);

    JsonNode resumed = fetchInbox(cursor, 1);
    assertThat(idsOf(resumed)).as("処理で後続が繰り上がらず、直後の申請が飛ばされないこと").containsExactly(second);
    assertThat(idsOf(fetchInbox(nextCursor(resumed), 1)))
        .as("その先も 1 件ずつ順に続くこと")
        .containsExactly(third);
  }

  @Test
  @DisplayName("会員本人の予約一覧も続きを辿ればすべてに到達できること")
  void memberReservationsPageThroughEveryReservation() {
    List<String> requested =
        List.of(
            requestReservation(memberAToken, STORE_A, "会員ページング 1"),
            requestReservation(memberAToken, STORE_A, "会員ページング 2"),
            requestReservation(memberAToken, STORE_A, "会員ページング 3"));

    List<String> collected = new ArrayList<>();
    String cursor = null;
    do {
      ResponseEntity<JsonNode> list =
          rest.exchange(
              "/platform/me/order-applications?size=1"
                  + (cursor == null ? "" : "&cursor=" + cursor),
              HttpMethod.GET,
              new HttpEntity<>(bearer(memberAToken)),
              JsonNode.class);
      assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode body = list.getBody();
      assertThat(body.path("content").size()).as("1 回の取得件数が上限で抑えられること").isLessThanOrEqualTo(1);
      collected.addAll(idsOf(body));
      cursor = nextCursor(body);
    } while (cursor != null && collected.size() <= PAGING_GUARD);

    assertThat(collected).as("本人の申請がすべて取得窓に現れること").containsAll(requested);
  }

  /** 続きを辿る試験が、位置が進まない不具合でぶら下がらないための打ち切り。 */
  private static final int PAGING_GUARD = 200;

  private JsonNode fetchInbox(String cursor, int size) {
    ResponseEntity<JsonNode> inbox =
        rest.exchange(
            "/store/order-applications?statuses=PENDING&size="
                + size
                + (cursor == null ? "" : "&cursor=" + cursor),
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(inbox.getStatusCode()).isEqualTo(HttpStatus.OK);
    return inbox.getBody();
  }

  /**
   * 未処理の申請を続きの位置を辿って全件集める。
   *
   * <p>先頭ページだけを見て在不在を断言すると、未処理の申請が取得窓の件数まで積み上がった時点で 自分の申請が窓から落ち、判定が他のテストの残す件数に左右される。
   */
  private List<String> allPendingIds() {
    List<String> collected = new ArrayList<>();
    String cursor = null;
    do {
      JsonNode body = fetchInbox(cursor, 20);
      collected.addAll(idsOf(body));
      cursor = nextCursor(body);
    } while (cursor != null && collected.size() <= PAGING_GUARD);
    return collected;
  }

  private static List<String> idsOf(JsonNode body) {
    List<String> ids = new ArrayList<>();
    body.path("content").forEach(node -> ids.add(node.path("id").asString()));
    return ids;
  }

  /** 続きが無いときは応答から項目ごと省かれる（null を出さない応答方針）。 */
  private static String nextCursor(JsonNode body) {
    return body.has("next_cursor") ? body.path("next_cursor").asString() : null;
  }

  /** 古い順に 1 件ずつ辿り、指定の申請を返した取得の「続きの位置」を返す。 */
  private String cursorAfter(String applicationId) {
    String cursor = null;
    for (int visited = 0; visited < PAGING_GUARD; visited++) {
      JsonNode body = fetchInbox(cursor, 1);
      List<String> ids = idsOf(body);
      cursor = nextCursor(body);
      if (ids.contains(applicationId)) {
        return cursor;
      }
      assertThat(cursor).as("前提: 対象の申請まで読み進められること").isNotNull();
    }
    throw new AssertionError("前提: 対象の申請に到達できること: " + applicationId);
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

  /** 店舗スタッフが手入力した受注（申請ではない）。確定で出生する。 */
  private String createStoreOrder(String castId) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/orders",
            new HttpEntity<>(
                "{\"receptionist_id\": 3, \"business_date\": \""
                    + today()
                    + "\", \"cast_id\": \""
                    + castId
                    + "\"}",
                storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 店舗側の受注作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  @Test
  @DisplayName("会員行が消えても未処理の申請は受付箱に残り、処理し終えられること")
  void inboxKeepsPendingApplicationAfterTheMemberRowIsGone() {
    String applicationId = requestReservation(memberAToken, STORE_A, "会員削除後も処理する申請");

    // 会員行の削除を DB 側の FK（SET NULL）と同じ形で再現する。会員コードのスナップショットは残る。
    OrderApplication application = orderApplicationRepository.findById(applicationId).orElseThrow();
    application.detachRequesterMember();
    orderApplicationRepository.save(application);

    assertThat(allPendingIds()).as("会員 ID が欠落しても未処理の申請は処理対象として残ること").contains(applicationId);

    ResponseEntity<JsonNode> confirmed = confirm(STORE_A, applicationId);
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(confirmed.getBody().path("status").asString()).isEqualTo("CONFIRMED");
    assertThat(confirmed.getBody().path("customer_id").isMissingNode())
        .as("整備する先が無いため顧客未設定のまま成立すること（無帰属受注は正規の状態）")
        .isTrue();
  }

  @Test
  @DisplayName("申請後に台帳へ紐づけた会員でも、確定した受注が顧客の受注履歴に載ること")
  void confirmationAttachesCustomerLinkedAfterTheRequest() {
    // 初回来店の順序: 申請（このとき紐づけは無い）→ 店舗が会員コードを読んで台帳に紐づけ → 確定
    String applicationId = requestReservation(memberAToken, STORE_A, "初回来店の申請");
    String memberCode = inboxMemberCode(applicationId);

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

    ResponseEntity<JsonNode> confirmed = confirm(STORE_A, applicationId);
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String orderId = confirmed.getBody().path("id").asString();

    ResponseEntity<JsonNode> history =
        rest.exchange(
            "/store/orders?customer_id=" + customerId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> ids = new ArrayList<>();
    history.getBody().path("content").forEach(node -> ids.add(node.path("id").asString()));
    assertThat(ids).as("確定で生まれた受注が顧客の受注履歴に現れること").contains(orderId);
  }

  /** 受付箱から見た申請の会員コード（紐づけ操作に使う）。 */
  private String inboxMemberCode(String applicationId) {
    OrderApplication application = orderApplicationRepository.findById(applicationId).orElseThrow();
    String code = application.getRequesterMemberCode();
    assertThat(code).as("前提: 申請者の会員コードが読めること").isNotBlank();
    return code;
  }

  @Test
  @DisplayName("会員の一覧に他会員の申請が一切現れないこと")
  void memberListNeverExposesOtherMembersApplications() {
    String canaryApplicationId = requestReservation(memberBToken, STORE_A, CANARY_REMARKS);

    // 正向対照: カナリアは店舗の受付箱からは確かに読める（断言対象が「漏れうるデータ」であることの証明)
    ResponseEntity<String> inbox =
        rest.exchange(
            "/store/order-applications?statuses=PENDING&size=2000",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            String.class);
    assertThat(inbox.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(inbox.getBody()).contains(CANARY_REMARKS);

    requestReservation(memberAToken, STORE_A, "会員Aの申請");

    ResponseEntity<String> list =
        rest.exchange(
            "/platform/me/order-applications",
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberAToken)),
            String.class);
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(list.getBody())
        .as("他会員の申請の実データ・ID が生ボディに現れないこと")
        .doesNotContain(CANARY_REMARKS)
        .doesNotContain(canaryApplicationId);
  }

  @Test
  @DisplayName("確定前は本人が取り下げられ、確定後は取り下げられないこと")
  void memberCanWithdrawOnlyBeforeConfirmation() {
    String applicationId = requestReservation(memberAToken, STORE_A, "取り下げ対象");

    ResponseEntity<JsonNode> withdrawn =
        rest.exchange(
            "/platform/me/order-applications/" + applicationId + "/withdrawal",
            HttpMethod.POST,
            new HttpEntity<>(bearer(memberAToken)),
            JsonNode.class);
    assertThat(withdrawn.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(withdrawn.getBody().path("status").asString()).isEqualTo("WITHDRAWN");

    String confirmedId = requestReservation(memberAToken, STORE_A, "確定後は取り下げ不可");
    ResponseEntity<JsonNode> confirmed = confirm(STORE_A, confirmedId);
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ResponseEntity<JsonNode> tooLate =
        rest.exchange(
            "/platform/me/order-applications/" + confirmedId + "/withdrawal",
            HttpMethod.POST,
            new HttpEntity<>(bearer(memberAToken)),
            JsonNode.class);
    assertThat(tooLate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("他会員の申請は取り下げられず、存在も明かさないこと")
  void memberCannotWithdrawAnotherMembersApplication() {
    String othersApplicationId = requestReservation(memberBToken, STORE_A, "他会員の申請");

    ResponseEntity<JsonNode> denied =
        rest.exchange(
            "/platform/me/order-applications/" + othersApplicationId + "/withdrawal",
            HttpMethod.POST,
            new HttpEntity<>(bearer(memberAToken)),
            JsonNode.class);
    assertThat(denied.getStatusCode())
        .as("権限違反ではなく不在として扱うこと（申請の存在を明かさない）")
        .isEqualTo(HttpStatus.NOT_FOUND);

    // 会員B の申請は健在
    JsonNode own = firstReservation(memberBToken);
    assertThat(own.path("id").asString()).isEqualTo(othersApplicationId);
    assertThat(own.path("status").asString()).isEqualTo("PENDING");
  }

  @Test
  @DisplayName("申請は受注の汎用更新（PUT /store/orders）から到達できないこと")
  void genericOrderUpdateCannotReachApplications() {
    String applicationId = requestReservation(memberAToken, STORE_A, "汎用更新の対象外");

    // 申請は受注ではない。受注の書き込み口をどう叩いても申請行には届かない
    ResponseEntity<JsonNode> tampered =
        rest.exchange(
            "/store/orders/" + applicationId,
            HttpMethod.PUT,
            new HttpEntity<>("{\"pax\": 9}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(tampered.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    // 申請は未処理のまま残り、専用の確定操作では引き続き処理できる
    ResponseEntity<JsonNode> confirmed = confirm(STORE_A, applicationId);
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  @DisplayName("希望日を過ぎた申請は失効し、確定も謝絶も拒否されること（行は PENDING のまま残る）")
  void expiredApplicationRejectsConfirmationAndDecline() {
    // 過去日の申請は作成 API が拒否するため、失効した申請は DB へ直接植える（FK に触れる会員参照は持たせない）
    OrderApplication stale =
        OrderApplication.builder()
            .status(OrderApplicationStatus.PENDING)
            .businessDate(today().minusDays(1))
            .pax(2)
            .requesterMemberCode("000000000000")
            .requesterDeclaredName("失効太郎")
            .build();
    stale.setStoreId(STORE_A);
    String applicationId = orderApplicationRepository.save(stale).getId();

    ResponseEntity<JsonNode> confirmed = confirm(STORE_A, applicationId);
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(confirmed.getBody().path("error").asString()).contains("失効");

    ResponseEntity<JsonNode> declined = decline(STORE_A, applicationId, "失効後の謝絶");
    assertThat(declined.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    OrderApplication after = orderApplicationRepository.findById(applicationId).orElseThrow();
    assertThat(after.getStatus())
        .as("失効は導出であり、行の状態は動かないこと")
        .isEqualTo(OrderApplicationStatus.PENDING);
    assertThat(after.getOrderId()).isNull();
  }

  @Test
  @DisplayName("指名なしで確定した受注を、キャストを設定せずに汎用更新で編集できること")
  void confirmedNominationFreeOrderCanBeEditedByTheGenericUpdate() {
    String applicationId = requestReservation(memberAToken, STORE_A, "確定後に人数を直す");
    ResponseEntity<JsonNode> confirmed = confirm(STORE_A, applicationId);
    assertThat(confirmed.getStatusCode()).as("前提: 指名なしのまま確定できること").isEqualTo(HttpStatus.CREATED);
    assertThat(confirmed.getBody().path("cast_id").isMissingNode()).as("前提: 指名なしであること").isTrue();
    String orderId = confirmed.getBody().path("id").asString();
    long receptionistId = confirmed.getBody().path("receptionist_id").asLong();

    ResponseEntity<JsonNode> edited =
        updateOrder(
            orderId,
            "{\"receptionist_id\": "
                + receptionistId
                + ", \"pax\": 6, \"remarks\": \"確定後に人数を直した\"}");
    assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(edited.getBody().path("pax").asInt()).isEqualTo(6);
    assertThat(edited.getBody().path("remarks").asString()).isEqualTo("確定後に人数を直した");
    assertThat(edited.getBody().path("cast_id").isMissingNode()).as("編集のために指名を作り出さずに済むこと").isTrue();

    // 受付担当が未設定のまま確定した受注（確定した実行者が受付候補の条件を満たさない場合）も同じく編集できる。
    // その状態は実行者の適格性に依るため、ここでは確定後の行から受付担当を外して同じ形を作る。
    orderRepository
        .findById(orderId)
        .ifPresent(
            order -> {
              order.assignReceptionist(null);
              orderRepository.save(order);
            });

    ResponseEntity<JsonNode> withoutReceptionist = updateOrder(orderId, "{\"pax\": 7}");
    assertThat(withoutReceptionist.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(withoutReceptionist.getBody().path("pax").asInt()).isEqualTo(7);
    assertThat(withoutReceptionist.getBody().path("receptionist_id").isMissingNode())
        .as("受付担当を作り出さずに済むこと")
        .isTrue();
  }

  private ResponseEntity<JsonNode> updateOrder(String id, String body) {
    return rest.exchange(
        "/store/orders/" + id,
        HttpMethod.PUT,
        new HttpEntity<>(body, storeHeaders(STORE_A)),
        JsonNode.class);
  }

  @Test
  @DisplayName("汎用更新では既にある指名・受付担当を省略で外せないこと")
  void genericUpdateCannotRemoveAnExistingNominationOrReceptionist() {
    String castId = createCastAs(STORE_A, "汎用更新の必須性ガード用キャスト");
    String storeOrderId = createStoreOrder(castId);

    ResponseEntity<JsonNode> withoutReceptionist = updateOrder(storeOrderId, "{\"pax\": 3}");
    assertThat(withoutReceptionist.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(withoutReceptionist.getBody().path("error").asString())
        .as("拒否が経路の都合ではなく必須性の判定から来ていること")
        .contains("受付担当を外すことはできません");

    ResponseEntity<JsonNode> withoutCast =
        updateOrder(storeOrderId, "{\"receptionist_id\": 3, \"pax\": 3}");
    assertThat(withoutCast.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(withoutCast.getBody().path("error").asString()).contains("指名を外すことはできません");

    // 撥ねられた要求は受注を書き換えない
    ResponseEntity<JsonNode> untouched =
        rest.exchange(
            "/store/orders/" + storeOrderId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(untouched.getBody().path("cast_id").asString()).isEqualTo(castId);
    assertThat(untouched.getBody().path("pax").isMissingNode()).isTrue();

    // 正向対照: 両方を送れば同じ受注を編集できる（拒否が PUT そのものの不達ではない証明）
    ResponseEntity<JsonNode> edited =
        updateOrder(
            storeOrderId, "{\"receptionist_id\": 3, \"cast_id\": \"" + castId + "\", \"pax\": 3}");
    assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(edited.getBody().path("pax").asInt()).isEqualTo(3);
  }

  @Test
  @DisplayName("他店舗は会員の申請を確定も謝絶もできず、申請はその後も処理できること")
  void otherStoreCannotConfirmOrDeclineForeignApplication() {
    String applicationId = requestReservation(memberAToken, STORE_A, "他店舗からの操作対象外");

    ResponseEntity<JsonNode> foreignConfirm = confirm(STORE_B, applicationId);
    assertThat(foreignConfirm.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<JsonNode> foreignDecline = decline(STORE_B, applicationId, "他店舗の謝絶");
    assertThat(foreignDecline.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // 正向対照: 申請先の店舗ではそのまま確定できる（負向がルーティング起因でない証明）
    ResponseEntity<JsonNode> ownConfirm = confirm(STORE_A, applicationId);
    assertThat(ownConfirm.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(ownConfirm.getBody().path("status").asString()).isEqualTo("CONFIRMED");
  }

  @Test
  @DisplayName("店舗が理由付きで謝絶した申請が会員側で謝絶として見え、理由が記録に残ること")
  void declinedApplicationAppearsDeclinedToMember() {
    String applicationId = requestReservation(memberAToken, STORE_A, "謝絶対象");

    // 謝絶は結果を読まれない操作なので 204（本体なし）
    ResponseEntity<JsonNode> declined = decline(STORE_A, applicationId, "満席のためお受けできません");
    assertThat(declined.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    JsonNode own = firstReservation(memberAToken);
    assertThat(own.path("id").asString()).isEqualTo(applicationId);
    assertThat(own.path("status").asString()).isEqualTo("DECLINED");

    OrderApplication application = orderApplicationRepository.findById(applicationId).orElseThrow();
    assertThat(application.getDeclinedReason()).isEqualTo("満席のためお受けできません");
    assertThat(application.getProcessedBy()).as("謝絶の実行者が残ること").isNotNull();

    // 理由の無い謝絶は撥ねられる（記録の根拠を欠く謝絶を成立させない）
    String another = requestReservation(memberAToken, STORE_A, "理由なし謝絶の対象");
    ResponseEntity<JsonNode> reasonless =
        rest.exchange(
            "/store/order-applications/" + another + "/refusal",
            HttpMethod.POST,
            new HttpEntity<>("{}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(reasonless.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // トークンを持たない要求は認証の失敗ではなく認可の拒否として扱われる（401 は失効・改竄トークンの側）。
  @Test
  @DisplayName("匿名では会員の予約申請経路に到達できないこと")
  void anonymousCannotReachMemberApplicationRoutes() {
    ResponseEntity<JsonNode> anonymous =
        rest.exchange(
            "/platform/me/order-applications",
            HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()),
            JsonNode.class);
    assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("壊れた Bearer トークンでは会員の予約申請経路が 401 になること")
  void brokenBearerIsUnauthorized() {
    ResponseEntity<JsonNode> broken =
        rest.exchange(
            "/platform/me/order-applications",
            HttpMethod.GET,
            new HttpEntity<>(bearer("not-a-real-token")),
            JsonNode.class);
    assertThat(broken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("店舗スタッフは会員の予約申請経路に到達できないこと")
  void storeStaffCannotReachMemberApplicationRoutes() {
    ResponseEntity<JsonNode> staff =
        rest.exchange(
            "/platform/me/order-applications",
            HttpMethod.GET,
            new HttpEntity<>(bearer(token)),
            JsonNode.class);
    assertThat(staff.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  /** 会員本人の一覧の先頭（希望日降順・id 降順のため、最後に申請したものが先頭に来る）。 */
  private JsonNode firstReservation(String memberToken) {
    ResponseEntity<JsonNode> list =
        rest.exchange(
            "/platform/me/order-applications",
            HttpMethod.GET,
            new HttpEntity<>(bearer(memberToken)),
            JsonNode.class);
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode first = list.getBody().path("content").path(0);
    assertThat(first.isObject()).as("前提: 一覧に申請が現れること").isTrue();
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
