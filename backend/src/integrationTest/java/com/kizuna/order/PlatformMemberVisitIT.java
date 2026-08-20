package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.domain.ReceptionRoute;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 会員本人の来店履歴の読み口を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>固定するのは「来店として見えるのは有効な帰属記録を持つ受注だけ」という ADR 0009 の骨格 — 未完了の申請も無帰属の完了も現れず、
 * 関連を解除しても過去の来店は残り、無効化された記録だけが消える。帰属記録は店舗で分割されないので、隔離は問い合わせに載せた 会員 ID
 * の一致だけが担う。その一致が効いていることは、他会員の来店を実データで仕込んで応答に現れないことで見る。
 *
 * <p>店舗A の来店は受注完了の本番経路で作る（読み口が実際の完了と噛み合っていることの確認）。基底クラスのシードユーザーは店舗1 にしか授権されていないため、跨店を見るための店舗B
 * の来店だけはリポジトリ直挿しで用意する — 帰属記録の生産経路そのものは {@link OrderAttributionIT} が固定している。
 *
 * <p>シード設定は「100 円ごとに 1 ポイント付与、利用は 100 ポイント単位」。
 */
class PlatformMemberVisitIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  /** demo シード（seed/05-demo.yaml）の山田次郎（STORE_STAFF・授権店舗 = 店舗1）。店舗A の受付担当として使用。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  private static final int TOTAL_FEE = 12000;
  private static final int GRANTED_FOR_TOTAL_FEE = 120;

  /** 店舗B の来店に直挿しする付与。店舗A の付与と見分けが付く値にする。 */
  private static final int GRANTED_AT_STORE_B = 777;

  /** 同じ受注に積まれた他会員の付与。本人の獲得ポイントに混ざってはならない。 */
  private static final int OTHER_MEMBER_GRANT = 5000;

  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderAttributionRepository orderAttributionRepository;
  @Autowired private PointEntryRepository pointEntryRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private StoreRepository storeRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final long nonce = System.nanoTime();

  /** 会員コード → 店舗A で紐づいた顧客 ID。 */
  private final Map<String, String> linkedCustomers = new HashMap<>();

  private RegisteredMember member;
  private String storeAName;
  private String storeBName;

  @BeforeEach
  void registerMemberAndReadStoreNames() {
    member = registerAndLogin("visit");
    storeAName = storeRepository.findById(STORE_A).map(Store::getName).orElseThrow();
    storeBName = storeRepository.findById(STORE_B).map(Store::getName).orElseThrow();
  }

  @Test
  @DisplayName("来店が店舗を跨いで新しい順に並び、日付・店舗・人数・担当・獲得ポイントを返すこと")
  void listsVisitsAcrossStoresNewestFirst() {
    String castName = "来店担当-" + nonce;
    completedVisitAtStoreA(castName, 3, TOTAL_FEE);
    seedVisitAtStoreB(2, GRANTED_AT_STORE_B);

    JsonNode content = visits(null).path("content");

    assertThat(rowsOf(content))
        .as("直挿しした店舗B の帰属が後に生まれるので先頭に来る")
        .containsExactly(
            List.of(today(), storeBName, "2", "", String.valueOf(GRANTED_AT_STORE_B)),
            List.of(today(), storeAName, "3", castName, String.valueOf(GRANTED_FOR_TOTAL_FEE)));
  }

  @Test
  @DisplayName("0 円完了も来店として見え、獲得ポイントは 0 になること")
  void showsZeroFeeVisitsWithoutAnyGrantedPoints() {
    // 付与 0 は台帳へ行を書かないが帰属記録は生まれる — 帰属は来店可視性の事実でポイントとは独立している。
    completedVisitAtStoreA("0円担当-" + nonce, 1, 0);

    JsonNode content = visits(null).path("content");

    assertThat(content).hasSize(1);
    assertThat(content.path(0).path("granted_points").asInt()).isZero();
  }

  @Test
  @DisplayName("同じ受注に別会員の付与が積まれていても、獲得ポイントは本人の分だけを足すこと")
  void countsOnlyTheViewersOwnGrantsForTheSameOrder() {
    // 誤帰属を無効化しても付与行は台帳に残る（清算は手動調整）。その受注が正しい本人へ申領されると、
    // 1 件の受注に 2 人分の付与が並ぶ。受注 ID だけで足すと前の会員の付与まで本人の来店に現れる。
    String orderId = completedVisitAtStoreA("同一受注担当-" + nonce, 2, TOTAL_FEE);
    RegisteredMember other = registerAndLogin("other");
    pointEntryRepository.save(
        PointEntry.grantForOrder(other.id(), orderId, STORE_A, OTHER_MEMBER_GRANT, null));

    JsonNode content = visits(null).path("content");

    assertThat(content).hasSize(1);
    assertThat(content.path(0).path("granted_points").asInt())
        .as("他会員の付与を足し込まないこと")
        .isEqualTo(GRANTED_FOR_TOTAL_FEE);
  }

  @Test
  @DisplayName("帰属記録の無い受注（未完了の申請・無帰属の完了）が来店に現れないこと")
  void hidesOrdersThatCarryNoAttribution() {
    String pendingRequestId = requestReservation();
    String unattributedOrderId = confirmedOrder(null, "無帰属-" + nonce);
    assertThat(complete(unattributedOrderId, TOTAL_FEE).getStatusCode())
        .as("前提: 顧客の付かない受注も完了できること")
        .isEqualTo(HttpStatus.OK);
    // 正向対照: 帰属の生まれる来店を 1 件だけ置き、「そもそも何も見えていない」状態と区別する
    completedVisitAtStoreA("対照担当-" + nonce, 2, TOTAL_FEE);

    JsonNode content = visits(null).path("content");

    assertThat(content).as("来店は帰属記録のある 1 件だけ").hasSize(1);
    assertThat(content.path(0).path("cast_name").asString()).isEqualTo("対照担当-" + nonce);

    // 申請の追跡は別の読み口として並置される。来店に出ないのは「消えた」からではない。
    ResponseEntity<JsonNode> orders = get("/platform/me/order-applications");
    assertThat(orders.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(idsOf(orders.getBody().path("content")))
        .as("未完了の申請は申請一覧には残ること")
        .contains(pendingRequestId);
  }

  @Test
  @DisplayName("無効化された帰属記録が来店から消えること")
  void hidesInvalidatedAttributions() {
    String orderId = completedVisitAtStoreA("無効化担当-" + nonce, 2, TOTAL_FEE);
    assertThat(visits(null).path("content")).as("前提: 無効化する前は来店として見えること").hasSize(1);

    // 無効化の操作そのものは別の実装票。ここでは記録の状態だけを本物の DB で倒し、読み口が状態を見て
    // いることを確かめる。
    jdbcTemplate.update(
        "update t_order_attributions set status = 'INVALIDATED' where order_id = ?", orderId);

    assertThat(visits(null).path("content")).isEmpty();
  }

  @Test
  @DisplayName("関連を解除しても過去の来店が見え続けること（解除は以後にだけ働く）")
  void keepsPastVisitsAfterTheLinkIsReleased() {
    String customerId = createCustomer("解除後-" + nonce);
    linkMember(customerId);
    String orderId = confirmedOrder(customerId, "解除後-" + nonce);
    assertThat(complete(orderId, TOTAL_FEE).getStatusCode())
        .as("前提: 帰属記録の生まれる完了ができること")
        .isEqualTo(HttpStatus.OK);

    ResponseEntity<Void> unlinked =
        rest.exchange(
            "/store/customers/" + customerId + "/member-link",
            HttpMethod.DELETE,
            new HttpEntity<>(storeHeaders(STORE_A)),
            Void.class);
    assertThat(unlinked.getStatusCode().is2xxSuccessful()).as("前提: 関連を解除できること").isTrue();

    JsonNode content = visits(null).path("content");
    assertThat(content).hasSize(1);
    assertThat(content.path(0).path("store_name").asString()).isEqualTo(storeAName);
  }

  @Test
  @DisplayName("他会員の来店が本人の応答に現れないこと")
  void neverExposesAnotherMembersVisits() {
    String otherCastName = "他人担当-" + nonce;
    completedVisitAtStoreA(otherCastName, 4, TOTAL_FEE);

    // 本人にも来店を 1 件持たせる。空の応答は「隔離できている」ことの証拠にならない。
    RegisteredMember viewer = registerAndLogin("viewer");
    String ownCastName = "本人担当-" + nonce;
    completedVisitAtStoreA(viewer, ownCastName, 1, TOTAL_FEE);

    ResponseEntity<String> raw =
        rest.exchange(
            "/platform/me/visits",
            HttpMethod.GET,
            new HttpEntity<>(bearer(viewer.token())),
            String.class);

    assertThat(raw.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(raw.getBody()).as("本人の来店は読めること").contains(ownCastName);
    assertThat(raw.getBody()).as("他会員の来店は実データごと現れないこと").doesNotContain(otherCastName);
  }

  @Test
  @DisplayName("続きはカーソルで辿れ、境界の来店を飛ばしも重ねもしないこと")
  void pagesThroughVisitsWithACursor() {
    List<String> castNames = List.of("頁1担当-" + nonce, "頁2担当-" + nonce, "頁3担当-" + nonce);
    castNames.forEach(castName -> completedVisitAtStoreA(castName, 2, TOTAL_FEE));

    JsonNode first = visits("?size=2");
    assertThat(castNamesOf(first.path("content")))
        .containsExactly(castNames.get(2), castNames.get(1));
    String nextCursor = first.path("next_cursor").asString();
    assertThat(nextCursor).as("続きがあるなら位置が添うこと").isNotBlank();

    JsonNode second = visits("?size=2&cursor=" + nextCursor);
    assertThat(castNamesOf(second.path("content"))).containsExactly(castNames.get(0));
    assertThat(second.path("next_cursor").isMissingNode()).as("続きが無ければ位置は返さないこと").isTrue();
  }

  // ==================== 応答の読み出し ====================

  private JsonNode visits(String query) {
    ResponseEntity<JsonNode> response = get("/platform/me/visits" + (query == null ? "" : query));
    assertThat(response.getStatusCode()).as("前提: 来店履歴が読めること").isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  private static List<List<String>> rowsOf(JsonNode content) {
    List<List<String>> rows = new ArrayList<>();
    content.forEach(
        row ->
            rows.add(
                List.of(
                    row.path("visited_on").asString(""),
                    row.path("store_name").asString(""),
                    row.path("pax").asString(""),
                    row.path("cast_name").asString(""),
                    row.path("granted_points").asString(""))));
    return rows;
  }

  private static List<String> castNamesOf(JsonNode content) {
    List<String> names = new ArrayList<>();
    content.forEach(row -> names.add(row.path("cast_name").asString("")));
    return names;
  }

  private static List<String> idsOf(JsonNode content) {
    List<String> ids = new ArrayList<>();
    content.forEach(row -> ids.add(row.path("id").asString("")));
    return ids;
  }

  private static String today() {
    return LocalDate.now().toString();
  }

  private ResponseEntity<JsonNode> get(String path) {
    return get(member, path);
  }

  private ResponseEntity<JsonNode> get(RegisteredMember as, String path) {
    return rest.exchange(
        path, HttpMethod.GET, new HttpEntity<>(bearer(as.token())), JsonNode.class);
  }

  // ==================== 来店の用意 ====================

  /** 店舗A に本番経路（顧客紐づけ → 受注確定 → 完了）で来店を 1 件作り、その受注 ID を返す。 */
  private String completedVisitAtStoreA(String castName, int pax, int totalFee) {
    return completedVisitAtStoreA(member, castName, pax, totalFee);
  }

  private String completedVisitAtStoreA(
      RegisteredMember as, String castName, int pax, int totalFee) {
    String orderId = confirmedOrder(linkedCustomerAtStoreA(as), castName, pax);
    assertThat(complete(orderId, totalFee).getStatusCode())
        .as("前提: 受注を完了できること")
        .isEqualTo(HttpStatus.OK);
    return orderId;
  }

  /**
   * 店舗A で当該会員に紐づいた顧客。会員 1 人につき 1 件だけ作って使い回す。
   *
   * <p>1 店舗の台帳で 1 人の会員に結び付く顧客は高々 1 件なので、来店を増やすたびに顧客を作って紐づけると 2 件目の紐づけが 409 になる。 来店（受注）の方を増やす。
   */
  private String linkedCustomerAtStoreA(RegisteredMember as) {
    return linkedCustomers.computeIfAbsent(
        as.memberCode(),
        memberCode -> {
          String customerId = createCustomer("来店客-" + memberCode + "-" + nonce);
          linkMember(customerId, memberCode);
          return customerId;
        });
  }

  /**
   * 店舗B の来店を直挿しで作る。基底クラスのシードユーザーは店舗1 にしか授権されていないため、店舗B の受注は API では起こせない。
   *
   * <p>担当キャストは付けない — 指名も割り当ても無い来店で担当名だけが欠けることも同時に見る。
   */
  private void seedVisitAtStoreB(int pax, int grantedPoints) {
    Order order =
        Order.builder()
            .businessDate(LocalDate.now())
            .pax(pax)
            .status(OrderStatus.COMPLETED)
            .receptionRoute(ReceptionRoute.PHONE)
            .totalFee(grantedPoints * 100)
            .autoGrantPoints(grantedPoints)
            .build();
    order.setStoreId(STORE_B);
    String orderId = orderRepository.save(order).getId();
    orderAttributionRepository.save(
        OrderAttribution.onCompletion(
            orderId, member.id(), member.memberCode(), OffsetDateTime.now()));
    pointEntryRepository.save(
        PointEntry.grantForOrder(member.id(), orderId, STORE_B, grantedPoints, null));
  }

  private String confirmedOrder(String customerId, String label) {
    return confirmedOrder(customerId, label, 2);
  }

  private String confirmedOrder(String customerId, String label, int pax) {
    String castId = createCast(label);
    String orderId = createOrder(castId, customerId, label, pax);
    return orderId;
  }

  private String createOrder(String castId, String customerId, String remarks, int pax) {
    String body =
        "{\"receptionist_id\": "
            + SEED_RECEPTIONIST_ID
            + ", \"business_date\": \""
            + LocalDate.now()
            + "\", \"cast_id\": \""
            + castId
            + "\", \"pax\": "
            + pax
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

  private ResponseEntity<JsonNode> complete(String orderId, int totalFee) {
    return rest.exchange(
        "/store/orders/" + orderId + "/completion",
        HttpMethod.POST,
        new HttpEntity<>("{\"total_fee\": " + totalFee + "}", storeHeaders(STORE_A)),
        JsonNode.class);
  }

  /**
   * 本人として店舗A へ予約を申請する（確定しないので帰属記録は生まれない）。
   *
   * <p>営業日は申請側の検証と同じ時間帯で数える。素の {@code LocalDate.now()}（＝コンテナの UTC）だと、日本時間の 00:00〜09:00
   * に走らせたときだけ「昨日」を送ることになり、過去日の申請として撥ねられる。
   */
  private String requestReservation() {
    ResponseEntity<JsonNode> requested =
        rest.postForEntity(
            "/platform/me/order-applications",
            new HttpEntity<>(
                "{\"store_id\": "
                    + STORE_A
                    + ", \"business_date\": \""
                    + LocalDate.now(ZoneId.of("Asia/Tokyo"))
                    + "\", \"pax\": 2, \"declared_name\": \"名乗り太郎\"}",
                bearer(member.token())),
            JsonNode.class);
    assertThat(requested.getStatusCode()).as("前提: 会員が予約を申請できること").isEqualTo(HttpStatus.CREATED);
    return requested.getBody().path("id").asString();
  }

  private String createCast(String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: キャスト作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  private String createCustomer(String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful()).as("前提: 顧客作成が成功すること").isTrue();
    return created.getBody().path("id").asString();
  }

  // ==================== 会員・関連 ====================

  private void linkMember(String customerId) {
    linkMember(customerId, member.memberCode());
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

  /** 登録した会員の本人確認材料。 */
  private record RegisteredMember(long id, String memberCode, String token) {}

  private RegisteredMember registerAndLogin(String prefix) {
    String email = prefix + "-visit-it-" + nonce + "-" + System.nanoTime() + "@kizuna.test";
    ResponseEntity<JsonNode> registration =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \""
                    + email
                    + "\", \"password\": \""
                    + PASSWORD
                    + "\", \"display_name\": \"来店履歴検証会員\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(registration.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);
    String memberCode = registration.getBody().path("member_code").asString();
    long registeredId =
        memberRepository.findByMemberCode(memberCode).map(Member::getId).orElseThrow();

    ResponseEntity<JsonNode> login =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}",
                jsonHeaders()),
            JsonNode.class);
    assertThat(login.getStatusCode()).as("前提: 会員としてログインできること").isEqualTo(HttpStatus.OK);
    return new RegisteredMember(registeredId, memberCode, login.getBody().path("token").asString());
  }

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private static HttpHeaders bearer(String bearerToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(bearerToken);
    return headers;
  }
}
