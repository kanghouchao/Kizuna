package com.kizuna.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.auth.infrastructure.JwtEncoderConfig;
import com.kizuna.auth.infrastructure.PlatformJwtIssuer;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 平台トークン過橋（案 A）の授権検証と実データ非漏洩を本物の PostgreSQL で固定する統合テスト。
 *
 * <p>平台トークンを /platform・/store で直接受理し、店舗文脈は StoreIdInterceptor が X-Store-ID を 授権店舗集合（StoreScope）で
 * fail-closed 検証する。断言は {@link com.kizuna.order.PlatformOrderScopeIT}
 * の強断言様式（応答生ボディに授権外店舗の実データが一切現れないこと）に倣う。シードユーザーは demo シード（田中花子=STORE_MANAGER{1,2} /
 * 山田次郎=STORE_STAFF{1} / HQ=ALL_STORES）。
 */
class PlatformBridgeIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "pass";

  /** {@link com.kizuna.shared.exception.CommonExceptionHandler} の汎用 401 文言と一致する固定値。 */
  private static final String UNAUTHENTICATED_MESSAGE = "認証に失敗しました";

  /** seed/04-platform-admin.yaml の HQ 管理者（ALL_STORES）。 */
  private static final String HQ_EMAIL = "admin@kizuna.test";

  /** demo シードの店長（SPECIFIC_STORES {1,2}）。 */
  private static final String MANAGER_EMAIL = "tanaka.hanako@kizuna.test";

  /** demo シードのスタッフ（SPECIFIC_STORES {1}）。 */
  private static final String STAFF_EMAIL = "yamada.jiro@kizuna.test";

  /** 役割線検証用に直挿する CAST ユーザー（PlatformOrderScopeIT の planted-user 様式）。 */
  private static final String CAST_EMAIL = "bridge-cast@kizuna.test";

  /** ログアウト検証専用ユーザー（他ケースのトークンを巻き込まないため独立させる）。 */
  private static final String LOGOUT_EMAIL = "bridge-logout@kizuna.test";

  private static final String MARKER_S1_REMARKS = "BRIDGE_MARKER_S1";
  private static final String CANARY_S2_REMARKS = "STORE2_LEAK_CANARY";

  /** マーカー受注の営業日（PlatformOrderScopeIT の 2999-01-01 と区別する）。 */
  private static final LocalDate MARKER_DATE = LocalDate.of(2999, 2, 1);

  @Autowired private OrderRepository orderRepository;
  @Autowired private CastRepository castRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RoleRepository roleRepository;

  @BeforeEach
  void prepareBridgeFixture() {
    // store1 に正向マーカー、store2（demo シードの実在店舗 = STORE_B）にカナリアを直挿する。
    ensureMarkerOrder(STORE_A, MARKER_S1_REMARKS);
    ensureMarkerOrder(STORE_B, CANARY_S2_REMARKS);

    ensurePlatformUser(CAST_EMAIL, UserType.CAST, Set.of(), StoreScopeType.ALL_STORES, Set.of());
    ensurePlatformUser(
        LOGOUT_EMAIL,
        UserType.STAFF,
        roleIdsOf("店舗スタッフ"),
        StoreScopeType.SPECIFIC_STORES,
        Set.of(STORE_A));
  }

  /** リポジトリ直挿（テストスレッドは @StoreScoped を経由せず storeFilter が無効なので他店舗にも書ける）。 */
  private void ensureMarkerOrder(long storeId, String remarks) {
    boolean exists =
        orderRepository.findAll().stream()
            .anyMatch(
                o ->
                    o.getStoreId() != null
                        && storeId == o.getStoreId()
                        && remarks.equals(o.getRemarks()));
    if (exists) {
      return;
    }
    Order order =
        Order.builder()
            .remarks(remarks)
            .businessDate(MARKER_DATE)
            .status(OrderStatus.CONFIRMED)
            .build();
    order.setStoreId(storeId);
    orderRepository.save(order);
  }

  private void ensurePlatformUser(
      String email,
      UserType userType,
      Set<Long> roleIds,
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
                        .displayName("過橋IT " + userType.name())
                        .enabled(true)
                        .userType(userType)
                        .roleIds(roleIds)
                        .storeScopeType(scopeType)
                        .storeIds(storeIds)
                        .build()));
  }

  /** 種子の既定束を名称で解決する（束はデータ — id を決め打ちしない）。 */
  private Set<Long> roleIdsOf(String roleName) {
    return Set.of(roleRepository.findByName(roleName).orElseThrow().getId());
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
    String t = res.getBody().path("token").asString();
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

  /** 平台トークン + 店舗文脈ヘッダ（X-Role: store / X-Store-ID）。過橋の正規リクエスト形。 */
  private static HttpHeaders bridgeHeaders(String token, long storeId) {
    HttpHeaders headers = bearer(token);
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(storeId));
    return headers;
  }

  private static HttpHeaders bridgeJsonHeaders(String token, long storeId) {
    HttpHeaders headers = bridgeHeaders(token, storeId);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  /**
   * 漏出の観測面は作業キューの読み口にする。顧客詳細の注文履歴（{@code GET /store/orders}）は 要約の行になり備考を載せないため、カナリアが応答に現れる余地が無くなった。
   */
  private ResponseEntity<String> getStoreOrdersRaw(HttpHeaders headers) {
    return rest.exchange(
        "/store/orders/work-queue?statuses=CONFIRMED&size=2000",
        HttpMethod.GET,
        new HttpEntity<>(headers),
        String.class);
  }

  private long castCountForStore(long storeId) {
    return castRepository.findAll().stream()
        .filter(c -> c.getStoreId() != null && storeId == c.getStoreId())
        .count();
  }

  @Test
  @DisplayName("大小文字混在メールで平台ログインが成功すること（裁定 1: email 小文字正規化）")
  void mixedCaseEmailLoginSucceeds() {
    ResponseEntity<JsonNode> res = platformLogin("TANAKA.Hanako@KIZUNA.test", PASSWORD);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().path("token").asString()).isNotBlank();
  }

  @Test
  @DisplayName("2店舗授権の店長は過橋で双方の店舗の受注を読め、非選択店舗の実データは応答に現れないこと")
  void managerReadsBothAuthorizedStoresViaBridge() {
    String token = platformToken(MANAGER_EMAIL);

    ResponseEntity<String> store1 = getStoreOrdersRaw(bridgeHeaders(token, STORE_A));
    assertThat(store1.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(store1.getBody())
        .as("店舗1の一覧は店舗1マーカーを含み、店舗2カナリアを一切含まないこと")
        .contains(MARKER_S1_REMARKS)
        .doesNotContain(CANARY_S2_REMARKS);

    ResponseEntity<String> store2 = getStoreOrdersRaw(bridgeHeaders(token, STORE_B));
    assertThat(store2.getStatusCode()).as("授権内の正側: 店舗2も読めること").isEqualTo(HttpStatus.OK);
    assertThat(store2.getBody()).contains(CANARY_S2_REMARKS);
  }

  @Test
  @DisplayName("単店舗スタッフは非授権店舗に読み書きとも 403 で、実データ漏洩も永続化もないこと")
  void staffCannotReachUnauthorizedStore() {
    String token = platformToken(STAFF_EMAIL);

    ResponseEntity<String> read = getStoreOrdersRaw(bridgeHeaders(token, STORE_B));
    assertThat(read.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(read.getBody() == null ? "" : read.getBody())
        .as("拒否応答に店舗2の実データが一切現れないこと")
        .doesNotContain(CANARY_S2_REMARKS);

    long before = castCountForStore(STORE_B);
    ResponseEntity<String> write =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"過橋IT不正書込キャスト\"}", bridgeJsonHeaders(token, STORE_B)),
            String.class);
    assertThat(write.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(castCountForStore(STORE_B)).as("拒否された書きが店舗2に永続化されていないこと").isEqualTo(before);
  }

  @Test
  @DisplayName("GET /platform/stores/me は授権店舗のみを返し、非授権店舗の実データが生ボディに現れないこと")
  void storeListOmitsUnauthorizedStore() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    // スタッフ（SPECIFIC{1}）: 店舗1のみ。店舗2の店名は生ボディにも現れない（強断言）。
    ResponseEntity<String> staff =
        rest.exchange(
            "/platform/stores/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(STAFF_EMAIL))),
            String.class);
    assertThat(staff.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(staff.getBody()).doesNotContain("Sample Tenant 2");
    assertThat(idsOf(mapper.readTree(staff.getBody()))).containsExactly(1L);

    // 店長（SPECIFIC{1,2}）: 授権 2 店舗が id 昇順で返る。
    ResponseEntity<String> manager =
        rest.exchange(
            "/platform/stores/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(MANAGER_EMAIL))),
            String.class);
    assertThat(manager.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(idsOf(mapper.readTree(manager.getBody()))).containsExactly(1L, 2L);

    // HQ（ALL_STORES）: 全店（他 IT が作る店舗もあり得るため包含断言）。
    ResponseEntity<String> hq =
        rest.exchange(
            "/platform/stores/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(HQ_EMAIL))),
            String.class);
    assertThat(hq.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(idsOf(mapper.readTree(hq.getBody()))).contains(1L, 2L);

    // CAST: 役割線の外（@PreAuthorize）。
    ResponseEntity<String> cast =
        rest.exchange(
            "/platform/stores/me",
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
  @DisplayName("HQ は /platform/stores へ過橋でき、店長は STORE_MANAGE なしのため 403 になること")
  void hqBridgesToPlatformButManagerDoesNot() {
    ResponseEntity<String> hq =
        rest.exchange(
            "/platform/stores",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(HQ_EMAIL))),
            String.class);
    assertThat(hq.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> manager =
        rest.exchange(
            "/platform/stores",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(MANAGER_EMAIL))),
            String.class);
    assertThat(manager.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("HQ は store_bridge=true で店舗コンソール入口を得つつ、着地は平台コンソールのままであること")
  void hqHoldsStoreBridgeWhileLandingOnPlatformConsole() {
    ResponseEntity<JsonNode> me =
        rest.exchange(
            "/platform/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(HQ_EMAIL))),
            JsonNode.class);

    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    // HQ 束の STAFF_MANAGE は Console.STORE なので店舗文脈を確立できる（ADR 0020）。
    assertThat(me.getBody().path("store_bridge").asBoolean()).isTrue();
    // 着地は PLATFORM 権限が優先されるため従来どおり平台コンソール。
    assertThat(me.getBody().path("console").asString()).isEqualTo("platform");
  }

  @Test
  @DisplayName("HQ の統合メニュー GET /platform/menus/me は中央項目と店舗スタッフ管理だけを返すこと（強断言）")
  void hqSeesPlatformMenusAndStoreStaffManagementOnly() {
    ResponseEntity<String> res =
        rest.exchange(
            "/platform/menus/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(HQ_EMAIL))),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody()).as("中央操作項目が可視であること").contains("店舗一覧", "管理者管理", "ロール管理", "システム設定");
    // HQ 束は STAFF_MANAGE（Console.STORE）を持つため、店舗スタッフ管理の 1 行だけが店舗側から残る。
    // 見出しの HRM も一緒に残るのは、可視な子が 1 つあるからである（ADR 0020）。
    assertThat(res.getBody()).as("店舗スタッフ管理へ辿り着けること").contains("HRM", "スタッフ管理");
    assertThat(res.getBody())
        .as("それ以外の店舗コンソール項目は現れないこと（反対スコープの不在まで強断言）")
        .doesNotContain("予約・案件管理", "キャスト管理", "出勤管理", "顧客一覧", "店舗情報", "業務管理", "CRM");
  }

  @Test
  @DisplayName("店長の統合メニュー GET /platform/menus/me は店舗項目のみを返し、中央項目は一切現れないこと（強断言）")
  void managerSeesOnlyStoreMenusViaUnifiedEndpoint() {
    ResponseEntity<String> res =
        rest.exchange(
            "/platform/menus/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(MANAGER_EMAIL))),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    // 店長束は STORE_MENU_VIEW + 店舗側の各能力を持つが PLATFORM_MENU_VIEW を持たないため、中央グループは fail-closed で剔除される。
    assertThat(res.getBody())
        .as("店舗操作項目が可視であること")
        .contains("予約・案件管理", "キャスト管理", "出勤管理", "スタッフ管理", "顧客一覧", "店舗情報");
    assertThat(res.getBody())
        .as("中央コンソール項目が一切現れないこと（反対スコープの不在まで強断言）")
        .doesNotContain("店舗一覧", "管理者管理", "ロール管理", "システム設定", "ダッシュボード");
  }

  @Test
  @DisplayName("店舗スタッフの統合メニュー GET /platform/menus/me は店舗項目のみを返し、中央項目は一切現れないこと（強断言）")
  void staffSeesOnlyStoreMenusViaUnifiedEndpoint() {
    ResponseEntity<String> res =
        rest.exchange(
            "/platform/menus/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(STAFF_EMAIL))),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    // 店舗スタッフ束は STORE_MENU_VIEW + 店舗側の各能力を持つが PLATFORM_MENU_VIEW を持たないため、中央グループは fail-closed で剔除される。
    assertThat(res.getBody())
        .as("店舗操作項目が可視であること")
        .contains("予約・案件管理", "キャスト管理", "出勤管理", "顧客一覧", "店舗情報");
    // 委譲層（STAFF_MANAGE）は店長へしか配らないので、平スタッフの束には店舗スタッフ管理が現れない。
    assertThat(res.getBody()).as("委譲層の行が平スタッフには現れないこと").doesNotContain("スタッフ管理");
    assertThat(res.getBody())
        .as("中央コンソール項目が一切現れないこと（反対スコープの不在まで強断言）")
        .doesNotContain("店舗一覧", "管理者管理", "ロール管理", "システム設定");
    assertThat(res.getBody()).as("店舗側ダッシュボードは廃止済みで、中央側のそれも不可視であること").doesNotContain("ダッシュボード");
  }

  @Test
  @DisplayName("CAST の統合メニュー GET /platform/menus/me は 200 で空配列になること（PERM_* 皆無で fail-closed）")
  void castSeesEmptyUnifiedMenu() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/menus/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(CAST_EMAIL))),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().isArray()).isTrue();
    assertThat(res.getBody().size()).as("全ノードが permission 非 null のため CAST には 1 件も可視でない").isZero();
  }

  @Test
  @DisplayName("CAST は授権スコープでも店舗ヘッダで /store 端点に過橋できず 403 になること（役割線）")
  void castCannotBridgeToStoreConsole() {
    // CAST は ALL_STORES を持つが店舗ロールではないため、StoreIdInterceptor が店舗文脈確立の前に 403 で弾く。
    // メニュー端点は /platform へ統一されたため、役割線は別の /store 端点（受注）で保全する。
    ResponseEntity<String> res =
        rest.exchange(
            "/store/orders",
            HttpMethod.GET,
            new HttpEntity<>(bridgeHeaders(platformToken(CAST_EMAIL), STORE_A)),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("統合前の旧メニュー端点 GET /central/menus/me・/tenant/menus/me は 404 になること")
  void oldMenuEndpointsAreGone() {
    ResponseEntity<String> legacyMenu =
        rest.exchange(
            "/central/menus/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(HQ_EMAIL))),
            String.class);
    assertThat(legacyMenu.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    ResponseEntity<String> legacyStoreMenu =
        rest.exchange(
            "/tenant/menus/me",
            HttpMethod.GET,
            new HttpEntity<>(bridgeHeaders(platformToken(MANAGER_EMAIL), STORE_A)),
            String.class);
    assertThat(legacyStoreMenu.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("店舗文脈ヘッダなしの平台トークンは /store/orders で 403 になること（fail-closed）")
  void missingStoreHeaderFailsClosed() {
    ResponseEntity<String> res =
        rest.exchange(
            "/store/orders",
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

    // ブラックリスト済みトークンは decoder の TokenBlacklistValidator が拒否し、resource-server の
    // AuthenticationEntryPoint が 401 で応答する。
    ResponseEntity<String> me =
        rest.exchange(
            "/platform/me", HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(errorMessageOf(me.getBody()))
        .as("PlatformAuthenticationEntryPoint の固定文言が CommonExceptionHandler の汎用文言と一致すること")
        .isEqualTo(UNAUTHENTICATED_MESSAGE);
  }

  @Test
  @DisplayName("誤った鍵で署名された Bearer token は 401 + 認証エントリポイントの固定文言を返すこと（decoder 検証失敗の実データ固定）")
  void invalidSignatureBearerTokenReturns401WithEntryPointMessage() {
    String forgedToken = issueTokenWithWrongSignature();

    ResponseEntity<String> res =
        rest.exchange(
            "/platform/me", HttpMethod.GET, new HttpEntity<>(bearer(forgedToken)), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(errorMessageOf(res.getBody())).isEqualTo(UNAUTHENTICATED_MESSAGE);
  }

  /** decoder の署名検証で拒否させるため、実サーバーの JWT secret とは異なる鍵で署名する。 */
  private String issueTokenWithWrongSignature() {
    AppProperties wrongProperties = new AppProperties();
    AppProperties.Jwt jwt = new AppProperties.Jwt();
    jwt.setSecret("wrong-signature-secret-wrong-signature-secret!!");
    jwt.setExpiration(3_600_000L);
    wrongProperties.setJwt(jwt);
    PlatformJwtIssuer wrongIssuer =
        new PlatformJwtIssuer(new JwtEncoderConfig().jwtEncoder(wrongProperties), wrongProperties);
    return wrongIssuer.issue(MANAGER_EMAIL, Map.of("authorities", List.of("PERM_TEST"))).token();
  }

  private static String errorMessageOf(String body) {
    return new ObjectMapper().readTree(body).path("error").asString();
  }
}
