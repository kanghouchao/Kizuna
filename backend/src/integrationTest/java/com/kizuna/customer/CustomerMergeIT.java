package com.kizuna.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerMerge;
import com.kizuna.customer.domain.CustomerMergeRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.customer.domain.LinkStatus;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 顧客統合を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>固定するのは ADR 0010 の骨格 — 存続行へ受注（全状態）と関連（ACTIVE・RELEASED の全区間）が移り、被統合行は削除されずに
 * 統合先参照を持つ墓標として残り、連鎖統合では既存の墓標も新しい統合先へ付け替わる（解決は常に一跳）。統合履歴に誰が・いつ・何を移したかが残り、 取り消す端点は存在しない。
 *
 * <p>統合がポイント台帳・会員の来店履歴に波及しないことは、件数ではなく応答の**内容**を統合の前後で突き合わせて見る。
 * 不一致が無いことは正しさの証明ではないが、内容の同一は構造的な不波及（仕訳も帰属記録も顧客を参照しない）が 実際に成り立っていることの観測になる。
 *
 * <p>統合は店長権限（{@code CUSTOMER_MERGE}）を要するため、基底クラスの店員トークン（山田次郎）ではなく {@link
 * #managerHeaders(long)}（田中花子・店舗{1,2} 授権）で叩く。店員では 403 になることも併せて見る。
 *
 * <p>墓標を電話照合・一覧から外すこと、旧 ID を渡された解決を統合先へ届けることは後続の作業の範囲で、本テストは扱わない。
 * 応答に出ない事実（統合先参照・統合履歴・付替え後の受注の顧客）は、行を直接読んで固定する。
 */
class CustomerMergeIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  /** v0.5.0 の山田次郎シード（STORE_STAFF・授権店舗 = 店舗1）。受注の受付担当として使用。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  private static final String MANAGER_EMAIL = "tanaka.hanako@kizuna.test";

  private static final int TOTAL_FEE = 12000;

  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerMemberLinkRepository customerMemberLinkRepository;
  @Autowired private CustomerMergeRepository customerMergeRepository;
  @Autowired private OrderRepository orderRepository;
  @Autowired private PlatformUserRepository platformUserRepository;

  private final long nonce = System.nanoTime();

  // ==================== 付替え ====================

  @Test
  @DisplayName("被統合行の受注が状態を問わず存続行に着き、被統合行は墓標として残ること")
  void movesOrdersOfEveryStatusAndLeavesATombstone() {
    String surviving = createCustomer("存続-" + nonce);
    String merged = createCustomer("被統合-" + nonce);
    // 未確定（CREATED）は会員申請だけが持つ状態になったため、店舗の作成経路では作れない。
    // 統合が状態を条件にしないことは、店舗が到達できる 3 状態で固定する。
    String confirmed = confirmedOrderFor(merged, "確定");
    String completed = completedOrderFor(merged, "完了");
    String cancelled = cancelledOrderFor(merged, "取消");

    ResponseEntity<JsonNode> response = merge(STORE_A, surviving, merged);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().path("surviving_customer_id").asString()).isEqualTo(surviving);
    assertThat(response.getBody().path("moved_order_count").asInt()).isEqualTo(3);
    // 状態は付替えの条件に入らない。売上も履歴も欠けないこと。
    assertThat(statusOf(confirmed)).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(statusOf(completed)).isEqualTo(OrderStatus.COMPLETED);
    assertThat(statusOf(cancelled)).isEqualTo(OrderStatus.CANCELLED);
    assertThat(List.of(confirmed, completed, cancelled))
        .allSatisfy(orderId -> assertThat(customerOf(orderId)).isEqualTo(surviving));
    // 行は削除されず、統合先を指す墓標として残る
    assertThat(mergedIntoOf(merged)).isEqualTo(surviving);
    assertThat(mergedIntoOf(surviving)).as("存続行は生きたまま").isNull();
  }

  @Test
  @DisplayName("被統合行の関連が ACTIVE・RELEASED の全区間ごと存続行へ移ること")
  void movesEveryLinkIntervalIncludingReleasedOnes() {
    String surviving = createCustomer("関連存続-" + nonce);
    String merged = createCustomer("関連被統合-" + nonce);
    link(merged, registerMember("merge-link-old"));
    unlink(merged);
    link(merged, registerMember("merge-link-new"));

    ResponseEntity<JsonNode> response = merge(STORE_A, surviving, merged);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().path("moved_link_count").asInt()).isEqualTo(2);
    // 解除済みの区間も移らなければ「過去に誰と紐づいていたか」の履歴が統合で切れる
    assertThat(customerMemberLinkRepository.findHistory(merged, Limit.unlimited())).isEmpty();
    assertThat(customerMemberLinkRepository.findHistory(surviving, Limit.unlimited())).hasSize(2);
    assertThat(customerMemberLinkRepository.findByCustomerIdAndStatus(surviving, LinkStatus.ACTIVE))
        .as("有効な区間は存続行の側で有効なまま")
        .isPresent();
  }

  @Test
  @DisplayName("連鎖統合で既存の墓標も新しい統合先へ付け替わり、旧 ID の解決が一跳で届くこと")
  void flattensChainsSoResolutionIsAlwaysOneHop() {
    String a = createCustomer("連鎖A-" + nonce);
    String b = createCustomer("連鎖B-" + nonce);
    String c = createCustomer("連鎖C-" + nonce);

    assertThat(merge(STORE_A, b, a).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(merge(STORE_A, c, b).getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(mergedIntoOf(a)).as("A→B→C の後、A は直接 C を指す").isEqualTo(c);
    assertThat(mergedIntoOf(b)).isEqualTo(c);
  }

  @Test
  @DisplayName("付替えと圧平が行の版を上げること（並行して読まれていた行の書き戻しが静かに勝たない）")
  void advancesTheVersionOfEveryRowItRewrites() {
    String a = createCustomer("版A-" + nonce);
    String b = createCustomer("版B-" + nonce);
    String c = createCustomer("版C-" + nonce);
    String orderId = orderFor(b, "版受注");
    assertThat(merge(STORE_A, b, a).getStatusCode())
        .as("前提: A を B へ統合できること")
        .isEqualTo(HttpStatus.OK);
    long tombstoneVersionBefore = versionOfCustomer(a);
    long orderVersionBefore = versionOfOrder(orderId);

    assertThat(merge(STORE_A, c, b).getStatusCode()).isEqualTo(HttpStatus.OK);

    // 版を上げないと、統合の前に読まれていた行を後から保存する経路が、自分が読んだ時点の値
    // （墓標 A なら統合先 B、受注なら顧客 B）を他の項目と一緒に書き戻せてしまう。楽観ロックの
    // 述語が成立するため競合として現れず、圧平と付替えだけが静かに取り消される。
    // 外形（並行する 2 つの要求のどちらが勝つか）の固定は後続の並行テストの範囲で、
    // ここではその競合が検出可能になる前提だけを決定的に押さえる。
    assertThat(versionOfCustomer(a)).as("圧平した墓標の版").isGreaterThan(tombstoneVersionBefore);
    assertThat(versionOfOrder(orderId)).as("付け替えた受注の版").isGreaterThan(orderVersionBefore);
  }

  // ==================== 統合履歴 ====================

  @Test
  @DisplayName("統合履歴に実行者・実行時刻・存続行・被統合行・移した件数が残り、取り消す端点が存在しないこと")
  void recordsWhoMovedWhatAndOffersNoUndo() {
    String surviving = createCustomer("履歴存続-" + nonce);
    String merged = createCustomer("履歴被統合-" + nonce);
    orderFor(merged, "履歴受注1");
    orderFor(merged, "履歴受注2");
    link(merged, registerMember("merge-history"));

    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    List<CustomerMerge> history = customerMergeRepository.findByMergedCustomerId(merged);
    assertThat(history).hasSize(1);
    CustomerMerge recorded = history.get(0);
    assertThat(recorded.getSurvivingCustomerId()).isEqualTo(surviving);
    assertThat(recorded.getMergedBy()).isEqualTo(managerId());
    assertThat(recorded.getMergedAt()).isNotNull();
    assertThat(recorded.getStoreId()).isEqualTo(STORE_A);
    assertThat(recorded.getMovedOrderCount()).isEqualTo(2);
    assertThat(recorded.getMovedLinkCount()).isEqualTo(1);

    // 統合に undo は無い。誤統合の修復は履歴を根拠とする人手作業である（ADR 0010）。
    // 「取り消せない」は応答符号の字面ではなく、取消を試みた後も墓標と履歴が動かないことで見る
    // （端点が無いときの符号は全域ハンドラの分類次第で変わりうるが、この不変量は変わらない）。
    ResponseEntity<JsonNode> undoAttempt =
        rest.exchange(
            mergePath(surviving),
            HttpMethod.DELETE,
            new HttpEntity<>(managerHeaders(STORE_A)),
            JsonNode.class);

    assertThat(undoAttempt.getStatusCode().is2xxSuccessful()).isFalse();
    assertThat(mergedIntoOf(merged)).as("取消の試みで墓標が生き返らないこと").isEqualTo(surviving);
    assertThat(customerMergeRepository.findByMergedCustomerId(merged)).hasSize(1);
  }

  // ==================== 拒否 ====================

  @Test
  @DisplayName("自分自身への統合は撥ねられること")
  void rejectsMergingACustomerIntoItself() {
    String customerId = createCustomer("自己統合-" + nonce);

    assertThat(merge(STORE_A, customerId, customerId).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("墓標を存続行に指定した統合と、墓標をもう一度統合する要求はいずれも 409 になること")
  void rejectsMergesThatWouldBreakTheOneHopInvariant() {
    String surviving = createCustomer("再統合存続-" + nonce);
    String merged = createCustomer("再統合被統合-" + nonce);
    String another = createCustomer("再統合別-" + nonce);
    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    // 墓標を存続行にすると、そこへ着けた参照の解決が二跳になる
    assertThat(merge(STORE_A, merged, another).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    // 応答喪失後の再送。前提状態が「生きている行 → 墓標」へ遷移済みなので二度目は通らない
    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  @DisplayName("他店舗の顧客を指定した統合は存続行・被統合行のいずれでも 404 になり、存在の有無が漏れないこと")
  void hidesForeignStoreCustomersBehindNotFound() {
    String inStoreA = createCustomer("越境自店-" + nonce);
    String inStoreB = createCustomerAt(STORE_B, "越境他店-" + nonce);

    // 田中花子は両店舗に授権されるためヘッダは通り、越境は storeFilter による 404 として現れる
    assertThat(merge(STORE_A, inStoreA, inStoreB).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(merge(STORE_A, inStoreB, inStoreA).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(mergedIntoOf(inStoreA)).isNull();
    assertThat(mergedIntoOf(inStoreB)).isNull();
  }

  @Test
  @DisplayName("両行が ACTIVE 関連を持つ統合は撥ねられ、先に関連を解除することが応答から判ること")
  void rejectsMergeWhenBothRowsAreClaimedByAMember() {
    String surviving = createCustomer("両認領存続-" + nonce);
    String merged = createCustomer("両認領被統合-" + nonce);
    link(surviving, registerMember("merge-claim-1"));
    link(merged, registerMember("merge-claim-2"));

    ResponseEntity<JsonNode> response = merge(STORE_A, surviving, merged);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().path("error").asString()).contains("先に関連を解除");
    assertThat(mergedIntoOf(merged)).as("撥ねた統合は何も動かさない").isNull();
  }

  @Test
  @DisplayName("統合の実行は店長権限を要し、スタッフ権限では 403 になること")
  void requiresTheManagerOnlyMergePermission() {
    String surviving = createCustomer("権限存続-" + nonce);
    String merged = createCustomer("権限被統合-" + nonce);

    ResponseEntity<JsonNode> asStaff =
        rest.exchange(
            mergePath(surviving),
            HttpMethod.POST,
            new HttpEntity<>(mergeBody(merged), storeHeaders(STORE_A)),
            JsonNode.class);

    assertThat(asStaff.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(mergedIntoOf(merged)).isNull();
  }

  @Test
  @DisplayName("統合に関与した行は存続行・被統合行とも削除できず、案内の読める応答になること")
  void keepsBothMergedRowsUndeletable() {
    String surviving = createCustomer("削除存続-" + nonce);
    String merged = createCustomer("削除被統合-" + nonce);
    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<JsonNode> deleteSurviving = deleteCustomer(surviving);
    ResponseEntity<JsonNode> deleteMerged = deleteCustomer(merged);

    assertThat(deleteSurviving.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(deleteSurviving.getBody().path("error").asString()).contains("統合");
    assertThat(deleteMerged.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(deleteMerged.getBody().path("error").asString()).contains("統合");
    assertThat(customerRepository.findById(surviving)).isPresent();
    assertThat(customerRepository.findById(merged)).isPresent();
  }

  // ==================== 不波及 ====================

  @Test
  @DisplayName("統合の前後で、会員のポイント残高・明細行の内容・来店履歴の行の内容が同一であること")
  void leavesTheMembersLedgerAndVisitHistoryUntouched() {
    RegisteredMember member = registerAndLoginMember("merge-untouched");
    String surviving = createCustomer("不波及存続-" + nonce);
    String merged = createCustomer("不波及被統合-" + nonce);
    link(merged, member.memberCode());
    completedOrderFor(merged, "不波及来店");

    JsonNode balanceBefore = memberGet("/platform/me/points/balance", member).getBody();
    JsonNode entriesBefore = memberGet("/platform/me/points/entries", member).getBody();
    JsonNode visitsBefore = memberGet("/platform/me/visits", member).getBody();
    assertThat(balanceBefore.path("balance").asInt()).as("前提: 来店でポイントが積まれること").isPositive();
    assertThat(entriesBefore.path("content")).as("前提: 明細が 1 行以上あること").isNotEmpty();
    assertThat(visitsBefore.path("content")).as("前提: 来店が 1 行以上あること").isNotEmpty();

    assertThat(merge(STORE_A, surviving, merged).getStatusCode()).isEqualTo(HttpStatus.OK);

    // 仕訳も帰属記録も受注と会員を参照し、顧客を参照しない（ADR 0006 / 0009）。件数ではなく内容が同一であること。
    assertThat(memberGet("/platform/me/points/balance", member).getBody()).isEqualTo(balanceBefore);
    assertThat(memberGet("/platform/me/points/entries", member).getBody()).isEqualTo(entriesBefore);
    assertThat(memberGet("/platform/me/visits", member).getBody()).isEqualTo(visitsBefore);
  }

  // ==================== 統合の呼び出し ====================

  private static String mergePath(String survivingCustomerId) {
    return "/store/customers/" + survivingCustomerId + "/merges";
  }

  private static String mergeBody(String mergedCustomerId) {
    return "{\"merged_customer_id\": \"" + mergedCustomerId + "\"}";
  }

  private ResponseEntity<JsonNode> merge(
      long storeId, String survivingCustomerId, String mergedCustomerId) {
    return rest.exchange(
        mergePath(survivingCustomerId),
        HttpMethod.POST,
        new HttpEntity<>(mergeBody(mergedCustomerId), managerHeaders(storeId)),
        JsonNode.class);
  }

  // ==================== 行の直読（応答に出ない事実） ====================

  /** 統合先参照。生きている行では null なので、Optional の写像で畳まずに行そのものを取り出して読む。 */
  private String mergedIntoOf(String customerId) {
    return customer(customerId).getMergedIntoId();
  }

  private Customer customer(String customerId) {
    return customerRepository.findById(customerId).orElseThrow();
  }

  private String customerOf(String orderId) {
    return orderRepository.findById(orderId).map(Order::getCustomerId).orElseThrow();
  }

  private long versionOfCustomer(String customerId) {
    return customer(customerId).getVersion();
  }

  private long versionOfOrder(String orderId) {
    return orderRepository.findById(orderId).map(Order::getVersion).orElseThrow();
  }

  private OrderStatus statusOf(String orderId) {
    return orderRepository.findById(orderId).map(Order::getStatus).orElseThrow();
  }

  private Long managerId() {
    return platformUserRepository.findByEmail(MANAGER_EMAIL).map(PlatformUser::getId).orElseThrow();
  }

  // ==================== 顧客・受注・関連の用意 ====================

  private String createCustomer(String name) {
    return createCustomerAt(STORE_A, name);
  }

  private String createCustomerAt(long storeId, String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", managerHeaders(storeId)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful())
        .as("前提: store %d での顧客作成が成功すること", storeId)
        .isTrue();
    return created.getBody().path("id").asString();
  }

  private ResponseEntity<JsonNode> deleteCustomer(String customerId) {
    return rest.exchange(
        "/store/customers/" + customerId,
        HttpMethod.DELETE,
        new HttpEntity<>(managerHeaders(STORE_A)),
        JsonNode.class);
  }

  /** 予約中（CREATED）の受注を 1 件作る。 */
  private String orderFor(String customerId, String label) {
    String castId = createCast(label + "-" + nonce);
    String body =
        "{\"receptionist_id\": "
            + SEED_RECEPTIONIST_ID
            + ", \"business_date\": \""
            + LocalDate.now()
            + "\", \"cast_id\": \""
            + castId
            + "\", \"pax\": 2, \"customer_id\": \""
            + customerId
            + "\", \"remarks\": \""
            + label
            + "\"}";
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/orders", new HttpEntity<>(body, managerHeaders(STORE_A)), JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 受注作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  /** 店舗が起こす受注は確定で出生するため、作成だけで確定済みになる。 */
  private String confirmedOrderFor(String customerId, String label) {
    return orderFor(customerId, label);
  }

  private String completedOrderFor(String customerId, String label) {
    String orderId = confirmedOrderFor(customerId, label);
    assertThat(
            rest.exchange(
                    "/store/orders/" + orderId + "/completion",
                    HttpMethod.POST,
                    new HttpEntity<>("{\"total_fee\": " + TOTAL_FEE + "}", managerHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .as("前提: 受注を完了できること")
        .isEqualTo(HttpStatus.OK);
    return orderId;
  }

  private String cancelledOrderFor(String customerId, String label) {
    String orderId = orderFor(customerId, label);
    assertThat(
            rest.exchange(
                    "/store/orders/" + orderId + "/cancellation",
                    HttpMethod.POST,
                    new HttpEntity<>("{\"reason\": \"統合テストの前提づくり\"}", managerHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .as("前提: 受注を取消できること")
        .isEqualTo(HttpStatus.NO_CONTENT);
    return orderId;
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

  private void link(String customerId, String memberCode) {
    ResponseEntity<JsonNode> linked =
        rest.exchange(
            "/store/customers/" + customerId + "/member-link",
            HttpMethod.POST,
            new HttpEntity<>("{\"member_code\": \"" + memberCode + "\"}", managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(linked.getStatusCode()).as("前提: 会員の紐づけが成功すること").isEqualTo(HttpStatus.OK);
  }

  private void unlink(String customerId) {
    assertThat(
            rest.exchange(
                    "/store/customers/" + customerId + "/member-link",
                    HttpMethod.DELETE,
                    new HttpEntity<>(managerHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .as("前提: 会員の紐づけを解除できること")
        .isEqualTo(HttpStatus.OK);
  }

  // ==================== 会員 ====================

  /** 会員本人として読むための材料。 */
  private record RegisteredMember(String memberCode, String token) {}

  private String registerMember(String prefix) {
    return registerMemberAs(uniqueEmail(prefix)).memberCode();
  }

  private RegisteredMember registerAndLoginMember(String prefix) {
    String email = uniqueEmail(prefix);
    String memberCode = registerMemberAs(email).memberCode();
    ResponseEntity<JsonNode> login =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(login.getStatusCode()).as("前提: 会員としてログインできること").isEqualTo(HttpStatus.OK);
    return new RegisteredMember(memberCode, login.getBody().path("token").asString());
  }

  private RegisteredMember registerMemberAs(String email) {
    ResponseEntity<JsonNode> registered =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \""
                    + email
                    + "\", \"password\": \""
                    + PASSWORD
                    + "\", \"display_name\": \"統合検証会員\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(registered.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);
    return new RegisteredMember(registered.getBody().path("member_code").asString(), null);
  }

  private ResponseEntity<JsonNode> memberGet(String path, RegisteredMember member) {
    HttpHeaders headers = jsonHeaders();
    headers.setBearerAuth(member.token());
    ResponseEntity<JsonNode> response =
        rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    assertThat(response.getStatusCode()).as("前提: %s を本人として読めること", path).isEqualTo(HttpStatus.OK);
    return response;
  }

  private String uniqueEmail(String prefix) {
    return prefix + "-merge-it-" + nonce + "-" + System.nanoTime() + "@kizuna.test";
  }

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
}
