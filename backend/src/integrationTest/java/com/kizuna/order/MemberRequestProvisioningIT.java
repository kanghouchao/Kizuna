package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
 * 会員申請の確定時自動整備を本物の PostgreSQL で検証する統合テスト（ADR 0008）。
 *
 * <p>固定するのは「確定した会員申請は必ず当店の台帳行と関連を得る」ための不変量 — 整備がここで漏れると完了時の会員解決（顧客 → 有効な関連 → 会員）が空振りし、
 * 店員の追加操作なしにはポイントが記帳されない。あわせて負向の不変量も固定する: 確定を経ない申請（謝絶・取下げ）は台帳に触らないこと、
 * 並行確定が関連を二重に立てないこと、そして店舗の応答が会員の登録情報 （メール・表示名）へ到達しないこと。
 *
 * <p>シード設定は「100 円ごとに 1 ポイント付与」。
 */
class MemberRequestProvisioningIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  /** 店舗{1,2} を授権する店長シード（田中花子）。店舗B からの確定に使う。 */
  private static final String MULTI_STORE_MANAGER_EMAIL = "tanaka.hanako@kizuna.test";

  @Autowired private CustomerMemberLinkRepository customerMemberLinkRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private PointEntryRepository pointEntryRepository;

  private final long nonce = System.nanoTime();

  /** 申請する会員（本人の認証トークンと会員コード）。 */
  private record Applicant(String token, String memberCode) {}

  @Test
  @DisplayName("会員申請の確定で、名乗った名前の台帳行と根拠 MEMBER_REQUEST の関連が自動で整うこと")
  void confirmationProvisionsTheLedgerRowAndTheLink() {
    Applicant applicant = register("整備");
    String declaredName = "名乗り一郎-" + nonce;
    String orderId = request(applicant, STORE_A, declaredName);

    ResponseEntity<JsonNode> confirmed = confirm(orderId, storeHeaders(STORE_A));

    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
    String customerId = confirmed.getBody().path("customer_id").asString();
    assertThat(customerId).as("確定した受注が顧客に着くこと").isNotBlank();

    JsonNode customer = customer(customerId).getBody();
    assertThat(customer.path("name").asString())
        .as("台帳行の氏名は本人が名乗った名前であること")
        .isEqualTo(declaredName);
    assertThat(customer.path("phone_number").isMissingNode()).as("申請は電話番号を運ばないこと").isTrue();
    assertThat(customer.path("member_linked").asBoolean()).isTrue();
    assertThat(customer.path("linked_member_code").asString()).isEqualTo(applicant.memberCode());

    List<CustomerMemberLink> links = activeLinks(applicant);
    assertThat(links).as("当店の関連が 1 本立つこと").hasSize(1);
    assertThat(links.get(0).getReason()).isEqualTo(LinkReason.MEMBER_REQUEST);
    assertThat(links.get(0).getCustomerId()).isEqualTo(customerId);
    assertThat(links.get(0).getStoreId()).isEqualTo(STORE_A);
  }

  @Test
  @DisplayName("既に有効な関連がある店舗では、確定がその顧客を使い新しい台帳行を作らないこと")
  void confirmationReusesTheEstablishedLink() {
    Applicant applicant = register("再利用");
    String established = createCustomer("会員コード提示の行");
    linkByMemberCode(established, applicant.memberCode());
    String declaredName = "使われない名乗り-" + nonce;

    ResponseEntity<JsonNode> confirmed =
        confirm(request(applicant, STORE_A, declaredName), storeHeaders(STORE_A));

    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(confirmed.getBody().path("customer_id").asString()).isEqualTo(established);
    List<CustomerMemberLink> links = activeLinks(applicant);
    assertThat(links).hasSize(1);
    assertThat(links.get(0).getReason())
        .as("成立済みの関連が別の根拠に置き換わらないこと")
        .isEqualTo(LinkReason.MEMBER_CODE);
    assertThat(searchCustomers(declaredName)).as("名乗った名前の台帳行は作られないこと").isEmpty();
  }

  @Test
  @DisplayName("謝絶・取下げの申請は台帳に行を作らないこと")
  void declinedAndWithdrawnRequestsLeaveTheLedgerUntouched() {
    Applicant applicant = register("謝絶");
    String declinedName = "謝絶される名乗り-" + nonce;
    String declined = request(applicant, STORE_A, declinedName);
    assertThat(
            rest.exchange(
                    "/store/orders/" + declined + "/decline",
                    HttpMethod.POST,
                    new HttpEntity<>(storeHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .as("前提: 謝絶が成功すること")
        .isEqualTo(HttpStatus.OK);

    String withdrawnName = "取下げる名乗り-" + nonce;
    String withdrawn = request(applicant, STORE_A, withdrawnName);
    assertThat(
            rest.exchange(
                    "/platform/me/orders/" + withdrawn + "/cancellation",
                    HttpMethod.POST,
                    new HttpEntity<>(bearer(applicant.token())),
                    JsonNode.class)
                .getStatusCode())
        .as("前提: 本人の取下げが成功すること")
        .isEqualTo(HttpStatus.OK);

    assertThat(activeLinks(applicant)).as("確定を経ない申請は関連を作らないこと").isEmpty();
    assertThat(searchCustomers(declinedName)).isEmpty();
    assertThat(searchCustomers(withdrawnName)).isEmpty();

    // 正向対照: 同じ会員の 3 件目を確定すれば整備される（負向が申請経路の不達ではない証明）
    String confirmedName = "確定する名乗り-" + nonce;
    assertThat(
            confirm(request(applicant, STORE_A, confirmedName), storeHeaders(STORE_A))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(activeLinks(applicant)).hasSize(1);
    assertThat(searchCustomers(confirmedName)).hasSize(1);
  }

  @Test
  @DisplayName("同一会員・同一店舗の並行確定が二重整備にならず、敗者が勝者の関連を再利用すること")
  void parallelConfirmationConvergesOnASingleLink() throws Exception {
    Applicant applicant = register("並行");
    // 双方が「関連なし」を観測しうる形。敗者は部分一意索引に敗れ、取り直して勝者の関連へ落ちる。
    String sharedName = "並行の名乗り-" + nonce;
    String first = request(applicant, STORE_A, sharedName + "-A");
    String second = request(applicant, STORE_A, sharedName + "-B");

    List<ResponseEntity<JsonNode>> responses =
        inParallel(
            () -> confirm(first, storeHeaders(STORE_A)),
            () -> confirm(second, storeHeaders(STORE_A)));

    assertThat(responses)
        .allSatisfy(
            response ->
                assertThat(response.getStatusCode())
                    .as("敗者も失敗せず収束すること（本文: %s）", response.getBody())
                    .isEqualTo(HttpStatus.OK));
    List<CustomerMemberLink> links = activeLinks(applicant);
    assertThat(links).as("有効な関連は店舗ごとに 1 本だけであること").hasSize(1);
    assertThat(searchCustomers(sharedName)).as("台帳行も 1 行だけであること").hasSize(1);
    assertThat(responses)
        .allSatisfy(
            response ->
                assertThat(response.getBody().path("customer_id").asString())
                    .as("双方の受注が同じ顧客に着くこと")
                    .isEqualTo(links.get(0).getCustomerId()));
  }

  @Test
  @DisplayName("申請時に着いた顧客が他の会員へ付け替えられていたら、確定が申請者の側へ着け直すこと")
  void confirmationReResolvesACustomerReassignedAfterTheRequest() {
    Applicant applicant = register("付替え");
    Applicant other = register("付替え先");
    String shared = createCustomer("付け替えられる行");
    linkByMemberCode(shared, applicant.memberCode());

    // 申請の時点では申請者に紐づく顧客が着く
    String orderId = request(applicant, STORE_A, "付替えの名乗り-" + nonce);

    // 店舗が同じ台帳行を別の会員へ付け替える（解除 → 別会員で再成立）
    assertThat(
            rest.exchange(
                    "/store/customers/" + shared + "/member-link",
                    HttpMethod.DELETE,
                    new HttpEntity<>(storeHeaders(STORE_A)),
                    Void.class)
                .getStatusCode())
        .as("前提: 紐づけを解除できること")
        .isEqualTo(HttpStatus.OK);
    linkByMemberCode(shared, other.memberCode());

    ResponseEntity<JsonNode> confirmed = confirm(orderId, storeHeaders(STORE_A));

    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(confirmed.getBody().path("customer_id").asString())
        .as("別会員を指す行に着け続けないこと（完了すればその会員へ記帳されてしまう）")
        .isNotEqualTo(shared);
    List<CustomerMemberLink> links = activeLinks(applicant);
    assertThat(links).as("申請者には当店の関連が整うこと").hasSize(1);
    assertThat(links.get(0).getReason()).isEqualTo(LinkReason.MEMBER_REQUEST);
    assertThat(confirmed.getBody().path("customer_id").asString())
        .isEqualTo(links.get(0).getCustomerId());
    assertThat(activeLinks(other)).as("付け替え先の会員の関連は動かないこと").hasSize(1);
    assertThat(activeLinks(other).get(0).getCustomerId()).isEqualTo(shared);
  }

  @Test
  @DisplayName("自動整備で作られた顧客の受注を完了するとポイントが記帳されること")
  void completingAProvisionedOrderRecordsPoints() {
    Applicant applicant = register("記帳");
    String orderId = request(applicant, STORE_A, "記帳の名乗り-" + nonce);
    assertThat(confirm(orderId, storeHeaders(STORE_A)).getStatusCode())
        .as("前提: 確定できること")
        .isEqualTo(HttpStatus.OK);

    ResponseEntity<JsonNode> completed =
        rest.exchange(
            "/store/orders/" + orderId + "/completion",
            HttpMethod.POST,
            new HttpEntity<>("{\"total_fee\": 12000}", storeHeaders(STORE_A)),
            JsonNode.class);

    assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(completed.getBody().path("auto_grant_points").asInt())
        .as("会員コードの再提示なしに付与されること")
        .isEqualTo(120);
    assertThat(pointEntryRepository.findCredits(memberIdOf(applicant)))
        .as("台帳にも付与が積まれること")
        .hasSize(1);
  }

  @Test
  @DisplayName("店舗側の応答に会員の登録情報（メール・表示名）が現れないこと")
  void storeSideResponsesNeverCarryThePlatformProfile() {
    // 掃く対象は「本人由来のデータを運ぶ面」。名乗った名前が現れることを正向対照に置く —
    // 現れないだけの断言は、応答が空でも通ってしまう。
    String canaryDisplayName = "PLATFORM-DISPLAY-CANARY-" + nonce;
    String canaryEmail = "platform-email-canary-" + nonce + "@kizuna.test";
    Applicant applicant = register(canaryDisplayName, canaryEmail);
    String declaredName = "CANARY-DECLARED-" + nonce;
    String orderId = request(applicant, STORE_A, declaredName);
    String customerId =
        confirm(orderId, storeHeaders(STORE_A)).getBody().path("customer_id").asString();

    String customerBody = rawStoreBody("/store/customers/" + customerId);
    String listBody = rawStoreBody("/store/customers?search=" + declaredName);
    String orderBody = rawStoreBody("/store/orders/" + orderId);
    // 関連の履歴は実行者の表示名のために PlatformUser を join する読み口で、会員の表示名が紛れ込む余地が
    // 構造上ここにだけある（join の相手を会員側へ広げた瞬間に漏れる）。
    String linkHistoryBody =
        rawStoreBody("/store/customers/" + customerId + "/member-link/history");

    assertThat(customerBody).as("正向対照: 台帳行が名乗った名前を運ぶこと").contains(declaredName);
    assertThat(listBody).as("正向対照: 一覧も同じ行を返すこと").contains(declaredName);
    assertThat(orderBody).as("正向対照: 受注が申請者の会員コードを運ぶこと").contains(applicant.memberCode());
    assertThat(linkHistoryBody).as("正向対照: 自動作成された関連が履歴に現れること").contains(applicant.memberCode());

    assertThat(List.of(customerBody, listBody, orderBody, linkHistoryBody))
        .allSatisfy(
            body ->
                assertThat(body)
                    .as("店舗が知る名前は本人が名乗った名前だけであること")
                    .doesNotContain(canaryDisplayName)
                    .doesNotContain(canaryEmail));
  }

  @Test
  @DisplayName("他店舗に有効な関連があっても、確定した店舗が自店の台帳行を整えること")
  void confirmationProvisionsPerStore() {
    Applicant applicant = register("店舗別");
    String storeACustomer = createCustomer("店舗Aの行");
    linkByMemberCode(storeACustomer, applicant.memberCode());

    String orderId = request(applicant, STORE_B, "店舗Bへの名乗り-" + nonce);
    ResponseEntity<JsonNode> confirmed =
        confirm(orderId, headersFor(STORE_B, loginAs(MULTI_STORE_MANAGER_EMAIL)));

    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(confirmed.getBody().path("customer_id").asString())
        .as("他店舗の台帳行を跨いで使わないこと")
        .isNotBlank()
        .isNotEqualTo(storeACustomer);
    assertThat(activeLinks(applicant)).as("関連は店舗ごとに 1 本ずつ立つこと").hasSize(2);
    assertThat(activeLinks(applicant))
        .anySatisfy(
            link -> {
              assertThat(link.getStoreId()).isEqualTo(STORE_B);
              assertThat(link.getReason()).isEqualTo(LinkReason.MEMBER_REQUEST);
            });
  }

  // ==================== 申請と確定 ====================

  private String request(Applicant applicant, long storeId, String declaredName) {
    ResponseEntity<JsonNode> requested =
        rest.postForEntity(
            "/platform/me/orders",
            new HttpEntity<>(
                "{\"store_id\": "
                    + storeId
                    + ", \"business_date\": \""
                    + LocalDate.now(ZoneId.of("Asia/Tokyo"))
                    + "\", \"pax\": 2, \"declared_name\": \""
                    + declaredName
                    + "\"}",
                bearer(applicant.token())),
            JsonNode.class);
    assertThat(requested.getStatusCode()).as("前提: 予約申請が成功すること").isEqualTo(HttpStatus.CREATED);
    return requested.getBody().path("id").asString();
  }

  private ResponseEntity<JsonNode> confirm(String orderId, HttpHeaders headers) {
    return rest.exchange(
        "/store/orders/" + orderId + "/confirmation",
        HttpMethod.POST,
        new HttpEntity<>(headers),
        JsonNode.class);
  }

  /** 2 つの確定を同時に開始して両方の応答を返す。 */
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

  // ==================== 台帳の読み出し ====================

  private ResponseEntity<JsonNode> customer(String customerId) {
    ResponseEntity<JsonNode> found =
        rest.exchange(
            "/store/customers/" + customerId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(found.getStatusCode()).as("前提: 顧客を読めること").isEqualTo(HttpStatus.OK);
    return found;
  }

  /** 名前で当店の台帳を引いた結果の顧客 ID。検索語は名前の部分一致。 */
  private List<String> searchCustomers(String name) {
    ResponseEntity<JsonNode> page =
        rest.exchange(
            "/store/customers?search=" + name,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(page.getStatusCode()).as("前提: 台帳を検索できること").isEqualTo(HttpStatus.OK);
    List<String> ids = new ArrayList<>();
    page.getBody().path("content").forEach(node -> ids.add(node.path("id").asString()));
    return ids;
  }

  private String rawStoreBody(String path) {
    ResponseEntity<String> response =
        rest.exchange(path, HttpMethod.GET, new HttpEntity<>(storeHeaders(STORE_A)), String.class);
    assertThat(response.getStatusCode()).as("前提: %s を読めること", path).isEqualTo(HttpStatus.OK);
    return response.getBody();
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

  private void linkByMemberCode(String customerId, String memberCode) {
    ResponseEntity<JsonNode> linked =
        rest.exchange(
            "/store/customers/" + customerId + "/member-link",
            HttpMethod.POST,
            new HttpEntity<>("{\"member_code\": \"" + memberCode + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(linked.getStatusCode()).as("前提: 会員コードでの紐づけが成功すること").isEqualTo(HttpStatus.OK);
  }

  /** その会員の有効な関連（店舗を跨いで全件）。店舗文脈の外なので storeFilter は働かない。 */
  private List<CustomerMemberLink> activeLinks(Applicant applicant) {
    long memberId = memberIdOf(applicant);
    return customerMemberLinkRepository.findAll().stream()
        .filter(link -> link.getMemberId() != null && link.getMemberId() == memberId)
        .filter(link -> link.getStatus() == LinkStatus.ACTIVE)
        .toList();
  }

  private long memberIdOf(Applicant applicant) {
    return memberRepository
        .findByMemberCode(applicant.memberCode())
        .map(Member::getId)
        .orElseThrow();
  }

  // ==================== 会員の登録とログイン ====================

  private Applicant register(String label) {
    return register(
        label + "-表示名",
        label + "-provision-it-" + nonce + "-" + System.nanoTime() + "@kizuna.test");
  }

  private Applicant register(String displayName, String email) {
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
    String memberCode = registration.getBody().path("member_code").asString();
    assertThat(memberCode).isNotBlank();
    return new Applicant(login(email, PASSWORD), memberCode);
  }

  private String loginAs(String email) {
    return login(email, "pass");
  }

  private String login(String email, String password) {
    ResponseEntity<JsonNode> login =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(login.getStatusCode()).as("前提: %s でログインできること", email).isEqualTo(HttpStatus.OK);
    String issued = login.getBody().path("token").asString();
    assertThat(issued).isNotBlank();
    return issued;
  }

  private static HttpHeaders headersFor(long storeId, String bearerToken) {
    HttpHeaders headers = jsonHeaders();
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(storeId));
    headers.setBearerAuth(bearerToken);
    return headers;
  }

  private static HttpHeaders bearer(String bearerToken) {
    HttpHeaders headers = jsonHeaders();
    headers.setBearerAuth(bearerToken);
    return headers;
  }

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
}
