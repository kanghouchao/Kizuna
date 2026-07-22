package com.kizuna.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.user.domain.Capability;
import com.kizuna.user.domain.CapabilityBundle;
import com.kizuna.user.domain.CapabilityBundleRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.HashMap;
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

/**
 * actors-and-access.md「必ず確認するアクセス場面（8 場面）」の受け入れ IT（#382 / #398）。
 *
 * <p>場面の分担: 場面 1（他店舗の閲覧・更新不可）は {@link com.kizuna.menu.MenuCrossStoreIT} / {@link
 * com.kizuna.order.OrderCrossStoreIT} 等、場面 2（複数店舗の切替・集約）は {@link com.kizuna.auth.PlatformBridgeIT}
 * / {@link com.kizuna.order.PlatformOrderScopeIT}、場面 3（精算範囲次元の表現）は {@link
 * PlatformStaffManagementIT#settlementScopeDimensionIsExpressible} が既に固定しているため本クラスでは重複させない。 場面
 * 7（サービス ID）は #395、場面 8（緊急管理）は #382 要件 4 の後続票の検証点。
 *
 * <p>本クラスは場面 4・5（CAST/MEMBER 本人種別の隔離）と場面 6（公開/内部の分離 +「束はデータ」の証明）を扱う。 強断言様式（リポジトリ直挿カナリア + 生ボディ
 * doesNotContain）は {@link com.kizuna.menu.MenuCrossStoreIT} に由来する。
 */
class AuthorizationScenesIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "pass";

  private static final String CAST_EMAIL = "scenes-it-cast@kizuna.test";
  private static final String MEMBER_EMAIL = "scenes-it-member@kizuna.test";
  private static final String PROFILE_ONLY_EMAIL = "scenes-it-profile-only@kizuna.test";

  /** STORE_VIEW 無しの店舗コンソール資格（ALL_STORES）を検証するユーザー（#428）。 */
  private static final String STORE_CONSOLE_ALL_EMAIL = "scenes-it-storeconsole-all@kizuna.test";

  /** STORE_VIEW も店舗コンソール資格も持たない STAFF（403 を検証 — #428）。 */
  private static final String PLATFORM_ONLY_EMAIL = "scenes-it-platform-only@kizuna.test";

  /** 種子に無い束（DB データとして追加 — 発版不要の証明）。 */
  private static final String PROFILE_ONLY_BUNDLE = "公開プロフィール担当IT";

  /** STORE_VIEW を含まない店舗コンソール束（ORDER_MANAGE のみ — storeBridge=true / #428）。 */
  private static final String STORE_CONSOLE_ONLY_BUNDLE = "受注担当IT";

  /** PLATFORM 能力のみで STORE_VIEW も店舗コンソール能力も持たない束（#428）。 */
  private static final String PLATFORM_ONLY_BUNDLE = "プラットフォームメニュー標識のみIT";

  /** 内部キャスト情報のカナリア。公開プロフィール応答へ混入しないことを強断言する。 */
  private static final String CAST_CANARY_NAME = "場面IT_内部キャスト機密カナリア";

  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private CapabilityBundleRepository capabilityBundleRepository;
  @Autowired private CastRepository castRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void prepareSceneFixture() {
    ensureUser(
        CAST_EMAIL, UserType.CAST, Set.of(), StoreScopeType.SPECIFIC_STORES, Set.of(STORE_A));
    ensureUser(
        MEMBER_EMAIL, UserType.MEMBER, Set.of(), StoreScopeType.SPECIFIC_STORES, Set.of(STORE_A));

    // 場面 6: 種子に無い束を DB データとして現場作成し、STORE_PROFILE_MANAGE のみを持つスタッフへ授与する。
    CapabilityBundle profileOnly =
        capabilityBundleRepository
            .findByName(PROFILE_ONLY_BUNDLE)
            .orElseGet(
                () ->
                    capabilityBundleRepository.save(
                        CapabilityBundle.builder()
                            .name(PROFILE_ONLY_BUNDLE)
                            .capabilities(Set.of(Capability.STORE_PROFILE_MANAGE))
                            .build()));
    ensureUser(
        PROFILE_ONLY_EMAIL,
        UserType.STAFF,
        Set.of(profileOnly.getId()),
        StoreScopeType.SPECIFIC_STORES,
        Set.of(STORE_A));

    // #428: STORE_VIEW を含まない店舗コンソール束（ORDER_MANAGE のみ）を ALL_STORES スタッフへ授与する。
    CapabilityBundle storeConsoleOnly =
        capabilityBundleRepository
            .findByName(STORE_CONSOLE_ONLY_BUNDLE)
            .orElseGet(
                () ->
                    capabilityBundleRepository.save(
                        CapabilityBundle.builder()
                            .name(STORE_CONSOLE_ONLY_BUNDLE)
                            .capabilities(Set.of(Capability.ORDER_MANAGE))
                            .build()));
    ensureUser(
        STORE_CONSOLE_ALL_EMAIL,
        UserType.STAFF,
        Set.of(storeConsoleOnly.getId()),
        StoreScopeType.ALL_STORES,
        Set.of());

    // #428: STORE_VIEW も店舗コンソール能力も持たない PLATFORM 標識のみの束（403 を検証）。
    CapabilityBundle platformOnly =
        capabilityBundleRepository
            .findByName(PLATFORM_ONLY_BUNDLE)
            .orElseGet(
                () ->
                    capabilityBundleRepository.save(
                        CapabilityBundle.builder()
                            .name(PLATFORM_ONLY_BUNDLE)
                            .capabilities(Set.of(Capability.PLATFORM_MENU_VIEW))
                            .build()));
    ensureUser(
        PLATFORM_ONLY_EMAIL,
        UserType.STAFF,
        Set.of(platformOnly.getId()),
        StoreScopeType.ALL_STORES,
        Set.of());

    // 内部キャスト情報のカナリア（リポジトリ直挿 — テストスレッドは storeFilter を経由しない）。
    boolean canaryExists =
        castRepository.findAll().stream().anyMatch(c -> CAST_CANARY_NAME.equals(c.getName()));
    if (!canaryExists) {
      Cast cast = Cast.builder().name(CAST_CANARY_NAME).status("在籍").build();
      cast.setStoreId(STORE_A);
      castRepository.save(cast);
    }
  }

  private void ensureUser(
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
                        .displayName("場面IT " + userType.name())
                        .enabled(true)
                        .userType(userType)
                        .bundleIds(bundleIds)
                        .storeScopeType(scopeType)
                        .storeIds(storeIds)
                        .build()));
  }

  private String platformToken(String email) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, PASSWORD),
                headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 平台ログインが成功すること").isEqualTo(HttpStatus.OK);
    return res.getBody().path("token").asString();
  }

  private static HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  private static HttpHeaders storeHeaders(String token, long storeId) {
    HttpHeaders headers = bearer(token);
    headers.add("X-Role", "store");
    headers.add("X-Store-ID", String.valueOf(storeId));
    return headers;
  }

  @Test
  @DisplayName("場面4: CAST は staff 管理・跨店参照・店舗文脈のいずれにも到達できないこと（本人種別は能力モデル外）")
  void scene4_castIsIsolatedFromStaffCapabilities() {
    String token = platformToken(CAST_EMAIL);

    ResponseEntity<String> staff =
        rest.exchange(
            "/platform/staff", HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
    assertThat(staff.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<String> stores =
        rest.exchange(
            "/platform/stores/me", HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
    assertThat(stores.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // 授権店舗の店舗ヘッダを名乗っても storeBridge を持たないため 403（StoreIdInterceptor fail-closed）。
    ResponseEntity<String> orders =
        rest.exchange(
            "/store/orders",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(token, STORE_A)),
            String.class);
    assertThat(orders.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("場面5: MEMBER も同様に隔離されること")
  void scene5_memberIsIsolatedFromStaffCapabilities() {
    String token = platformToken(MEMBER_EMAIL);

    ResponseEntity<String> staff =
        rest.exchange(
            "/platform/staff", HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
    assertThat(staff.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<String> orders =
        rest.exchange(
            "/store/orders",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(token, STORE_A)),
            String.class);
    assertThat(orders.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("場面6: 種子に無い公開担当束(DB データ追加のみ)で店舗档案へ到達でき、内部キャスト情報へは到達も混入もしないこと")
  void scene6_nonSeedBundleGrantsProfileOnlyAccess() {
    String token = platformToken(PROFILE_ONLY_EMAIL);

    // STORE_PROFILE_MANAGE を含む束で店舗档案(公開側設定)へ到達できる — 束はデータであり発版を要しない。
    ResponseEntity<String> profile =
        rest.exchange(
            "/store/config",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(token, STORE_A)),
            String.class);
    assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(profile.getBody())
        .as("公開档案応答に内部キャスト情報（カナリア名）が一切現れないこと — 8 場面 6 の強断言")
        .doesNotContain(CAST_CANARY_NAME);

    // CAST_MANAGE を持たないため内部キャスト一覧へは 403。
    ResponseEntity<String> casts =
        rest.exchange(
            "/store/casts",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(token, STORE_A)),
            String.class);
    assertThat(casts.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // プラットフォーム端点にも到達できない（PLATFORM 能力なし）。
    ResponseEntity<String> platform =
        rest.exchange(
            "/platform/stores", HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
    assertThat(platform.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName(
      "#428: STORE_VIEW 無しの店舗コンソール資格保持者(SPECIFIC_STORES)は /platform/stores/me で自授権店舗のみを実名で取得できること")
  void scene428_storeBridgeSpecificStoresCanListOwnAuthorizedStores() {
    // PROFILE_ONLY は STORE_PROFILE_MANAGE(Console.STORE) のみで STORE_VIEW を持たない → storeBridge=true。
    ResponseEntity<JsonNode> stores =
        rest.exchange(
            "/platform/stores/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(PROFILE_ONLY_EMAIL))),
            JsonNode.class);

    assertThat(stores.getStatusCode())
        .as("STORE_VIEW 無しでも storeBridge 経由で 200")
        .isEqualTo(HttpStatus.OK);
    JsonNode body = stores.getBody();
    // 応答は本人の授権店舗集合 {STORE_A} のみで、実名（Sample Tenant）を返す。
    assertThat(body.size()).isEqualTo(1);
    assertThat(body.path(0).path("id").asLong()).isEqualTo(STORE_A);
    assertThat(body.path(0).path("name").asString()).isEqualTo("Sample Tenant");
  }

  @Test
  @DisplayName("#428: STORE_VIEW 無しの店舗コンソール資格保持者(ALL_STORES)は /platform/stores/me で全店舗を実名で取得できること")
  void scene428_storeBridgeAllStoresCanListAllStores() {
    ResponseEntity<JsonNode> stores =
        rest.exchange(
            "/platform/stores/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(STORE_CONSOLE_ALL_EMAIL))),
            JsonNode.class);

    assertThat(stores.getStatusCode()).isEqualTo(HttpStatus.OK);
    // ALL_STORES は全店舗を返す（他 IT の店舗作成で総数は変動しうるため demo 2 店舗の実名 contains で判定）。
    Map<Long, String> byId = new HashMap<>();
    stores
        .getBody()
        .forEach(node -> byId.put(node.path("id").asLong(), node.path("name").asString()));
    assertThat(byId)
        .containsEntry(STORE_A, "Sample Tenant")
        .containsEntry(STORE_B, "Sample Tenant 2");
  }

  @Test
  @DisplayName("#428: STORE_VIEW も店舗コンソール資格も持たない STAFF は /platform/stores/me で 403 になること")
  void scene428_staffWithoutStoreViewNorStoreConsoleIsForbidden() {
    // PLATFORM_MENU_VIEW のみ（authority は発行されるが PERM_STORE_VIEW でも storeBridge でもない）→ fail-closed。
    ResponseEntity<String> stores =
        rest.exchange(
            "/platform/stores/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(PLATFORM_ONLY_EMAIL))),
            String.class);

    assertThat(stores.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
