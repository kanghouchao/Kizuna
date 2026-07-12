package com.kizuna.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.shared.CrossTenantTestSupport;
import com.kizuna.user.domain.PlatformRole;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * 平台トークン過橋（#324 統一ログイン・案 A）の授権検証と実データ非漏洩を本物の PostgreSQL で固定する統合テスト。
 *
 * <p>平台トークンを /central・/tenant で直接受理し、店舗文脈は TenantIdInterceptor が X-Tenant-ID を 授権店舗集合（StoreScope）で
 * fail-closed 検証する。断言は {@link com.kizuna.order.PlatformOrderScopeIT}
 * の強断言様式（応答生ボディに授権外店舗の実データが一切現れないこと）に倣う。シードユーザーは v0.5.0（田中花子=STORE_MANAGER{1,2} /
 * 山田次郎=STORE_STAFF{1} / HQ=ALL_STORES）。
 */
class PlatformBridgeIT extends CrossTenantTestSupport {

  private static final String PASSWORD = "pass";

  /** v0.4.0 シードの HQ 管理者（ALL_STORES）。 */
  private static final String HQ_EMAIL = "admin@kizuna.test";

  /** v0.5.0 シードの店長（SPECIFIC_STORES {1,2}）。 */
  private static final String MANAGER_EMAIL = "tanaka.hanako@kizuna.test";

  /** v0.5.0 シードのスタッフ（SPECIFIC_STORES {1}）。 */
  private static final String STAFF_EMAIL = "yamada.jiro@kizuna.test";

  /** 役割線検証用に直挿する CAST ユーザー（PlatformOrderScopeIT の planted-user 様式）。 */
  private static final String CAST_EMAIL = "bridge-cast@kizuna.test";

  /** ログアウト検証専用ユーザー（他ケースのトークンを巻き込まないため独立させる）。 */
  private static final String LOGOUT_EMAIL = "bridge-logout@kizuna.test";

  private static final String MARKER_S1_STORE_NAME = "店舗1過橋受注";
  private static final String MARKER_S1_REMARKS = "BRIDGE_MARKER_S1";
  private static final String CANARY_S2_STORE_NAME = "店舗2機密受注";
  private static final String CANARY_S2_REMARKS = "STORE2_LEAK_CANARY";

  /** マーカー受注の営業日（PlatformOrderScopeIT の 2999-01-01 と区別する）。 */
  private static final LocalDate MARKER_DATE = LocalDate.of(2999, 2, 1);

  @Autowired private OrderRepository orderRepository;
  @Autowired private CastRepository castRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void prepareBridgeFixture() {
    // store1 に正向マーカー、store2（v0.5.0 シードの実在店舗 = TENANT_B）にカナリアを直挿する。
    ensureMarkerOrder(TENANT_A, MARKER_S1_STORE_NAME, MARKER_S1_REMARKS);
    ensureMarkerOrder(TENANT_B, CANARY_S2_STORE_NAME, CANARY_S2_REMARKS);

    ensurePlatformUser(CAST_EMAIL, PlatformRole.CAST, StoreScopeType.ALL_STORES, Set.of());
    ensurePlatformUser(
        LOGOUT_EMAIL, PlatformRole.STORE_STAFF, StoreScopeType.SPECIFIC_STORES, Set.of(TENANT_A));
  }

  /** リポジトリ直挿（テストスレッドは @TenantScoped を経由せず tenantFilter が無効なので他テナントにも書ける）。 */
  private void ensureMarkerOrder(long tenantId, String storeName, String remarks) {
    boolean exists =
        orderRepository.findAll().stream()
            .anyMatch(
                o ->
                    o.getTenantId() != null
                        && tenantId == o.getTenantId()
                        && remarks.equals(o.getRemarks()));
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
      String email, PlatformRole role, StoreScopeType scopeType, Set<Long> storeIds) {
    platformUserRepository
        .findByEmail(email)
        .orElseGet(
            () ->
                platformUserRepository.save(
                    PlatformUser.builder()
                        .email(email)
                        .password(passwordEncoder.encode(PASSWORD))
                        .displayName("過橋IT " + role.name())
                        .enabled(true)
                        .role(role)
                        .storeScopeType(scopeType)
                        .storeIds(storeIds)
                        .build()));
  }

  private ResponseEntity<JsonNode> platformLogin(String email, String password) {
    return rest.postForEntity(
        "/platform/login",
        new HttpEntity<>(
            String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, password),
            jsonHeaders()),
        JsonNode.class);
  }

  private String platformToken(String email) {
    ResponseEntity<JsonNode> res = platformLogin(email, PASSWORD);
    assertThat(res.getStatusCode()).as("前提: %s の平台ログインが成功すること", email).isEqualTo(HttpStatus.OK);
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

  /** 平台トークン + 店舗文脈ヘッダ（X-Role: tenant / X-Tenant-ID）。過橋の正規リクエスト形。 */
  private static HttpHeaders bridgeHeaders(String token, long tenantId) {
    HttpHeaders headers = bearer(token);
    headers.set("X-Role", "tenant");
    headers.set("X-Tenant-ID", String.valueOf(tenantId));
    return headers;
  }

  private static HttpHeaders bridgeJsonHeaders(String token, long tenantId) {
    HttpHeaders headers = bridgeHeaders(token, tenantId);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private ResponseEntity<String> getTenantOrdersRaw(HttpHeaders headers) {
    return rest.exchange(
        "/tenant/orders?size=500", HttpMethod.GET, new HttpEntity<>(headers), String.class);
  }

  private long castCountForTenant(long tenantId) {
    return castRepository.findAll().stream()
        .filter(c -> c.getTenantId() != null && tenantId == c.getTenantId())
        .count();
  }

  @Test
  @DisplayName("大小文字混在メールで平台ログインが成功すること（裁定 1: email 小文字正規化）")
  void mixedCaseEmailLoginSucceeds() {
    ResponseEntity<JsonNode> res = platformLogin("TANAKA.Hanako@KIZUNA.test", PASSWORD);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().path("token").asText()).isNotBlank();
  }

  @Test
  @DisplayName("2店舗授権の店長は過橋で双方の店舗の受注を読め、非選択店舗の実データは応答に現れないこと")
  void managerReadsBothAuthorizedStoresViaBridge() {
    String token = platformToken(MANAGER_EMAIL);

    ResponseEntity<String> store1 = getTenantOrdersRaw(bridgeHeaders(token, TENANT_A));
    assertThat(store1.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(store1.getBody())
        .as("店舗1の一覧は店舗1マーカーを含み、店舗2カナリアを一切含まないこと")
        .contains(MARKER_S1_REMARKS)
        .doesNotContain(CANARY_S2_REMARKS)
        .doesNotContain(CANARY_S2_STORE_NAME);

    ResponseEntity<String> store2 = getTenantOrdersRaw(bridgeHeaders(token, TENANT_B));
    assertThat(store2.getStatusCode()).as("授権内の正側: 店舗2も読めること").isEqualTo(HttpStatus.OK);
    assertThat(store2.getBody()).contains(CANARY_S2_REMARKS);
  }

  @Test
  @DisplayName("単店舗スタッフは非授権店舗に読み書きとも 403 で、実データ漏洩も永続化もないこと")
  void staffCannotReachUnauthorizedStore() {
    String token = platformToken(STAFF_EMAIL);

    ResponseEntity<String> read = getTenantOrdersRaw(bridgeHeaders(token, TENANT_B));
    assertThat(read.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(read.getBody() == null ? "" : read.getBody())
        .as("拒否応答に店舗2の実データが一切現れないこと")
        .doesNotContain(CANARY_S2_REMARKS)
        .doesNotContain(CANARY_S2_STORE_NAME);

    long before = castCountForTenant(TENANT_B);
    ResponseEntity<String> write =
        rest.postForEntity(
            "/tenant/casts",
            new HttpEntity<>("{\"name\": \"過橋IT不正書込キャスト\"}", bridgeJsonHeaders(token, TENANT_B)),
            String.class);
    assertThat(write.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(castCountForTenant(TENANT_B)).as("拒否された書きが店舗2に永続化されていないこと").isEqualTo(before);
  }

  @Test
  @DisplayName("GET /platform/stores は授権店舗のみを返し、非授権店舗の実データが生ボディに現れないこと")
  void storeListOmitsUnauthorizedStore() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    // スタッフ（SPECIFIC{1}）: 店舗1のみ。店舗2の店名は生ボディにも現れない（強断言）。
    ResponseEntity<String> staff =
        rest.exchange(
            "/platform/stores",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(STAFF_EMAIL))),
            String.class);
    assertThat(staff.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(staff.getBody()).doesNotContain("Sample Tenant 2");
    assertThat(idsOf(mapper.readTree(staff.getBody()))).containsExactly(1L);

    // 店長（SPECIFIC{1,2}）: 授権 2 店舗が id 昇順で返る。
    ResponseEntity<String> manager =
        rest.exchange(
            "/platform/stores",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(MANAGER_EMAIL))),
            String.class);
    assertThat(manager.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(idsOf(mapper.readTree(manager.getBody()))).containsExactly(1L, 2L);

    // HQ（ALL_STORES）: 全店（他 IT が作る店舗もあり得るため包含断言）。
    ResponseEntity<String> hq =
        rest.exchange(
            "/platform/stores",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(HQ_EMAIL))),
            String.class);
    assertThat(hq.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(idsOf(mapper.readTree(hq.getBody()))).contains(1L, 2L);

    // CAST: 役割線の外（@PreAuthorize）。
    ResponseEntity<String> cast =
        rest.exchange(
            "/platform/stores",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(CAST_EMAIL))),
            String.class);
    assertThat(cast.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  private static List<Long> idsOf(JsonNode array) {
    List<Long> ids = new ArrayList<>();
    array.forEach(node -> ids.add(node.path("id").asLong()));
    return ids;
  }

  @Test
  @DisplayName("HQ は /central/tenants へ過橋でき、店長は TENANT_MANAGE なしのため 403 になること")
  void hqBridgesToCentralButManagerDoesNot() {
    ResponseEntity<String> hq =
        rest.exchange(
            "/central/tenants",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(HQ_EMAIL))),
            String.class);
    assertThat(hq.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> manager =
        rest.exchange(
            "/central/tenants",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(MANAGER_EMAIL))),
            String.class);
    assertThat(manager.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("過橋で店舗メニュー GET /tenant/menus/me が非空を返すこと（サイドバー成立）")
  void bridgeServesStoreMenus() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/tenant/menus/me",
            HttpMethod.GET,
            new HttpEntity<>(bridgeHeaders(platformToken(MANAGER_EMAIL), TENANT_A)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().isArray()).isTrue();
    assertThat(res.getBody()).isNotEmpty();
  }

  @Test
  @DisplayName("店舗文脈ヘッダなしの平台トークンは /tenant/orders で 403 になること（fail-closed）")
  void missingStoreHeaderFailsClosed() {
    ResponseEntity<String> res =
        rest.exchange(
            "/tenant/orders",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(MANAGER_EMAIL))),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("POST /platform/logout 後は同一トークンの GET /platform/me が拒否されること（ブラックリスト登録）")
  void platformLogoutBlacklistsToken() {
    String token = platformToken(LOGOUT_EMAIL);

    // 正向対照: ログアウト前は同一トークンで me が読める（後段の拒否がログアウト起因である証明）。
    ResponseEntity<String> before =
        rest.exchange(
            "/platform/me", HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
    assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<Void> logout =
        rest.exchange(
            "/platform/logout", HttpMethod.POST, new HttpEntity<>(bearer(token)), Void.class);
    assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // ブラックリスト済みトークンは JwtAuthenticationFilter が未認証として扱い、本システムの既定では
    // 403 で拒否される（issuer 相互拒否と同じ経路 — PlatformAuthIT の 403 期待と同一規約）。
    ResponseEntity<String> me =
        rest.exchange(
            "/platform/me", HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
