package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.shared.CrossTenantTestSupport;
import com.kizuna.tenant.domain.Tenant;
import com.kizuna.tenant.domain.TenantRepository;
import com.kizuna.user.domain.CapabilityBundleRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 集合作用域（#323）の実データ非漏洩を本物の PostgreSQL で強断言する統合テスト。
 *
 * <p>断言は「帰属不一致」型の弱断言ではなく、応答生ボディに授権外店舗の実データ（店舗名・カナリア文字列）が 一切現れないこと（AC2）で行う。読みは storeSetFilter が
 * session 層で機構的に濾過し、書きは明示的単店 storeId の 授権検証（{@code PlatformOrderService.create}）が担う。先例は {@link
 * com.kizuna.menu.MenuCrossTenantIT}（リポジトリ直挿 + 実データ断言）と {@link
 * com.kizuna.auth.PlatformAuthIT}（平台トークン取得 + planted-user）。
 */
class PlatformOrderScopeIT extends CrossTenantTestSupport {

  private static final String PASSWORD = "pass";

  /** ALL_STORES/HQ_ADMIN のシードユーザー（v0.4.0 central/02-platform-users-seed.yaml）。 */
  private static final String SEED_EMAIL = "admin@kizuna.test";

  private static final String SPECIFIC_EMAIL = "scope-manager@kizuna.test";
  private static final String CAST_EMAIL = "scope-cast@kizuna.test";

  private static final String TENANT_B_DOMAIN = "platform-scope-it.kizuna.test";

  private static final String MARKER_A_STORE_NAME = "店舗A受注マーカー";
  private static final String MARKER_A_REMARKS = "SCOPE_MARKER_A";
  private static final String CANARY_B_STORE_NAME = "店舗B機密受注";
  private static final String CANARY_B_REMARKS = "SCOPE_LEAK_CANARY_B";

  /** マーカー受注が一覧の先頭ページに現れるよう、他 IT の受注より新しい営業日を使う。 */
  private static final LocalDate MARKER_DATE = LocalDate.of(2999, 1, 1);

  /** v0.5.0 central/01 の山田次郎シード(platform_users id=3, STORE_STAFF, SPECIFIC_STORES{1})。受付担当として使用。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  @Autowired private OrderRepository orderRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private CapabilityBundleRepository capabilityBundleRepository;

  /** 保存後に採番された第二テナントの実 id。 */
  private long tenantBId;

  @BeforeEach
  void prepareScopeFixture() {
    Tenant tenantB =
        tenantRepository
            .findByDomain(TENANT_B_DOMAIN)
            .orElseGet(
                () -> tenantRepository.save(new Tenant("集合作用域IT第二テナント", TENANT_B_DOMAIN, null)));
    tenantBId = tenantB.getId();

    ensureMarkerOrder(TENANT_A, MARKER_A_STORE_NAME, MARKER_A_REMARKS);
    ensureMarkerOrder(tenantBId, CANARY_B_STORE_NAME, CANARY_B_REMARKS);

    ensurePlatformUser(
        SPECIFIC_EMAIL,
        UserType.STAFF,
        bundleIdsOf("店長"),
        StoreScopeType.SPECIFIC_STORES,
        Set.of(TENANT_A));
    ensurePlatformUser(CAST_EMAIL, UserType.CAST, Set.of(), StoreScopeType.ALL_STORES, Set.of());
  }

  /** リポジトリ直挿（テストスレッドは @TenantScoped を経由せず tenantFilter が無効なので他テナントにも書ける）。 */
  private void ensureMarkerOrder(long tenantId, String storeName, String remarks) {
    boolean exists =
        orderRepository.findAll().stream()
            .anyMatch(
                o ->
                    o.getTenantId() != null
                        && tenantId == o.getTenantId()
                        && storeName.equals(o.getStoreName()));
    if (exists) {
      return;
    }
    Order order =
        Order.builder()
            .storeName(storeName)
            .remarks(remarks)
            .businessDate(MARKER_DATE)
            .status(OrderStatus.CREATED)
            .build();
    order.setTenantId(tenantId);
    orderRepository.save(order);
  }

  private void ensurePlatformUser(
      String email,
      UserType userType,
      Set<Long> bundleIds,
      StoreScopeType scopeType,
      Set<Long> storeIds) {
    platformUserRepository
        .findByEmail(email)
        .orElseGet(
            () ->
                platformUserRepository.save(
                    PlatformUser.builder()
                        .email(email)
                        .password(passwordEncoder.encode(PASSWORD))
                        .displayName("集合作用域IT " + userType.name())
                        .enabled(true)
                        .userType(userType)
                        .bundleIds(bundleIds)
                        .storeScopeType(scopeType)
                        .storeIds(storeIds)
                        .build()));
  }

  /** 種子の既定束を名称で解決する（束はデータ — id を決め打ちしない）。 */
  private Set<Long> bundleIdsOf(String bundleName) {
    return Set.of(capabilityBundleRepository.findByName(bundleName).orElseThrow().getId());
  }

  private String platformToken(String email, String password) {
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, password),
                jsonHeaders()),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 平台ログインが成功すること").isEqualTo(HttpStatus.OK);
    String t = res.getBody().path("token").asText();
    assertThat(t).isNotBlank();
    return t;
  }

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private static HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  private static HttpHeaders bearerJson(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private long orderCountForTenant(long tenantId) {
    return orderRepository.findAll().stream()
        .filter(o -> o.getTenantId() != null && tenantId == o.getTenantId())
        .count();
  }

  private String createCastAs(long tenantId, String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/tenant/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", tenantHeaders(tenantId)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful())
        .as("前提: tenant %d でのキャスト作成が成功すること", tenantId)
        .isTrue();
    return created.getBody().path("id").asText();
  }

  @Test
  @DisplayName("SPECIFIC{1} の一覧は授権店舗(store_id=1)のみを返し、正向マーカーを含むこと(AC1)")
  void specificScopeListReturnsOnlyAuthorizedStores() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/orders?size=500",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(SPECIFIC_EMAIL, PASSWORD))),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode content = res.getBody().path("content");
    assertThat(content).isNotEmpty();
    content.forEach(
        node ->
            assertThat(node.path("store_id").asLong()).as("授権外の店舗が一覧に現れないこと").isEqualTo(TENANT_A));

    boolean hasMarker = false;
    for (JsonNode node : content) {
      if (MARKER_A_STORE_NAME.equals(node.path("store_name").asText())) {
        hasMarker = true;
        break;
      }
    }
    assertThat(hasMarker).as("授権店舗の正向マーカーが一覧に含まれること").isTrue();
  }

  @Test
  @DisplayName("集合外店舗の実データ(店舗B機密受注/カナリア)が応答の生ボディに一切現れないこと(AC2)")
  void outOfSetRealDataNeverAppearsInResponse() {
    ResponseEntity<String> res =
        rest.exchange(
            "/platform/orders?size=500",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(SPECIFIC_EMAIL, PASSWORD))),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody())
        .as("授権外店舗の店舗名・カナリア文字列が生ボディに現れないこと")
        .doesNotContain(CANARY_B_REMARKS)
        .doesNotContain(CANARY_B_STORE_NAME);
  }

  @Test
  @DisplayName("ALL_STORES(seed HQ) の一覧は両店のマーカーが現れ、store_id 集合に {1, B} を含むこと(機構の正側)")
  void allStoresScopeSeesEveryStore() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/orders?size=500",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(SEED_EMAIL, PASSWORD))),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode content = res.getBody().path("content");
    List<String> storeNames = new ArrayList<>();
    Set<Long> storeIds = new HashSet<>();
    content.forEach(
        node -> {
          storeNames.add(node.path("store_name").asText());
          storeIds.add(node.path("store_id").asLong());
        });

    assertThat(storeNames).contains(MARKER_A_STORE_NAME, CANARY_B_STORE_NAME);
    assertThat(storeIds).contains(TENANT_A, tenantBId);
  }

  @Test
  @DisplayName("store_id 未指定の書きは 400 で拒否されること(AC3 前半)")
  void writeWithoutStoreIdIsRejected() {
    String body =
        String.format(
            "{\"receptionist_id\": %d, \"business_date\": \"%s\", \"cast_id\": \"dummy-cast\"}",
            SEED_RECEPTIONIST_ID, LocalDate.now());

    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/orders",
            new HttpEntity<>(body, bearerJson(platformToken(SPECIFIC_EMAIL, PASSWORD))),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("授権集合外 store_id の書きは 403 かつ tenant B に永続化されないこと(AC3 後半)")
  void writeToOutOfSetStoreIsRejected() {
    long before = orderCountForTenant(tenantBId);

    String body =
        String.format(
            "{\"store_id\": %d, \"receptionist_id\": %d, \"business_date\": \"%s\","
                + " \"cast_id\": \"dummy-cast\"}",
            tenantBId, SEED_RECEPTIONIST_ID, LocalDate.now());

    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/orders",
            new HttpEntity<>(body, bearerJson(platformToken(SPECIFIC_EMAIL, PASSWORD))),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(orderCountForTenant(tenantBId))
        .as("拒否された書きが tenant B に永続化されていないこと")
        .isEqualTo(before);
  }

  @Test
  @DisplayName("授権店舗への書きはその店舗に受注を作成すること(正向対照: 負向 403 がバリデーション起因でない証明)")
  void writeToAuthorizedStoreCreatesOrderInThatStore() {
    String castId = createCastAs(TENANT_A, "集合作用域IT用キャスト");

    String body =
        String.format(
            "{\"store_id\": %d, \"receptionist_id\": %d, \"business_date\": \"%s\","
                + " \"cast_id\": \"%s\"}",
            TENANT_A, SEED_RECEPTIONIST_ID, LocalDate.now(), castId);

    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/orders",
            new HttpEntity<>(body, bearerJson(platformToken(SPECIFIC_EMAIL, PASSWORD))),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String newId = res.getBody().path("id").asText();
    assertThat(newId).isNotBlank();

    Order created = orderRepository.findById(newId).orElseThrow();
    assertThat(created.getTenantId()).as("授権店舗(tenant 1)に永続化されていること").isEqualTo(TENANT_A);
  }

  @Test
  @DisplayName("CAST ロールで /platform/orders は 403(@PreAuthorize の役割線)")
  void castRoleIsRejectedOnPlatformOrders() {
    ResponseEntity<String> res =
        rest.exchange(
            "/platform/orders",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(CAST_EMAIL, PASSWORD))),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
