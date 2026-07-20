package com.kizuna.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.domain.CapabilityBundleRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
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
 * スタッフ・権限管理（#325 / #398 能力束モデル）の HTTP 境界統合テスト。STAFF_MANAGE
 * 能力限定の授権書き込み（付与・変更・停止・履歴）と、付与した店舗集合が本人の次回ログインのデータ範囲に反映されること、 授権外店舗の実データが応答生ボディに一切現れないこと（強断言）を本物の
 * PostgreSQL で固定する。ヘルパは {@link com.kizuna.order.PlatformOrderScopeIT} の {@code
 * ensurePlatformUser}/{@code platformToken} 様式を踏襲し、強断言様式は {@link com.kizuna.menu.MenuCrossStoreIT}
 * に由来する。
 */
class PlatformStaffManagementIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "pass";

  /** ALL_STORES/HQ_ADMIN のシードユーザー（v0.4.0 central/02-platform-users-seed.yaml）。 */
  private static final String SEED_EMAIL = "admin@kizuna.test";

  /** 授権判定に使う 2 店舗。名称そのものを漏洩検知のカナリアに用いる。 */
  private static final String STORE_A_DOMAIN = "staff-it-store-a.kizuna.test";

  private static final String STORE_A_NAME = "スタッフ管理IT_店舗A授権マーカー";
  private static final String STORE_B_DOMAIN = "staff-it-store-b.kizuna.test";
  private static final String STORE_B_NAME = "スタッフ管理IT_店舗B機密";

  private static final String NON_HQ_EMAIL = "staff-it-nonhq@kizuna.test";
  private static final String CAST_CANARY_EMAIL = "staff-it-cast-canary@kizuna.test";

  private static final String CASE1_EMAIL = "staff-it-created@kizuna.test";
  private static final String CASE3_EMAIL = "staff-it-editable@kizuna.test";
  private static final String DUP_EMAIL = "staff-it-dup@kizuna.test";

  @Autowired private StoreRepository storeRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private CapabilityBundleRepository capabilityBundleRepository;

  private long storeAId;
  private long storeBId;

  @BeforeEach
  void prepareStaffFixture() {
    storeAId = ensureStore(STORE_A_DOMAIN, STORE_A_NAME);
    storeBId = ensureStore(STORE_B_DOMAIN, STORE_B_NAME);
    ensurePlatformUser(
        NON_HQ_EMAIL, UserType.STAFF, bundleIdsOf("店長"), StoreScopeType.ALL_STORES, Set.of());
    ensurePlatformUser(
        CAST_CANARY_EMAIL, UserType.CAST, Set.of(), StoreScopeType.ALL_STORES, Set.of());
  }

  private long ensureStore(String domain, String name) {
    return storeRepository
        .findByDomain(domain)
        .orElseGet(() -> storeRepository.save(new Store(name, domain, null)))
        .getId();
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
                        .displayName("スタッフ管理IT " + userType.name())
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

  private static String createBody(
      String email, String bundleIdsJson, String scopeType, String storeIds) {
    return String.format(
        "{\"email\":\"%s\",\"password\":\"%s\",\"display_name\":\"IT表示名\",\"bundle_ids\":%s,"
            + "\"store_scope_type\":\"%s\",\"store_ids\":%s}",
        email, PASSWORD, bundleIdsJson, scopeType, storeIds);
  }

  private static String updateBody(
      String bundleIdsJson, String scopeType, String storeIds, long version) {
    return String.format(
        "{\"bundle_ids\":%s,\"store_scope_type\":\"%s\",\"store_ids\":%s,\"version\":%d}",
        bundleIdsJson, scopeType, storeIds, version);
  }

  /** 束名を JSON の id 配列へ解決する（例: ["店長"] → "[3]"）。 */
  private String bundlesJson(String... bundleNames) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < bundleNames.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(capabilityBundleRepository.findByName(bundleNames[i]).orElseThrow().getId());
    }
    return sb.append(']').toString();
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

  @Test
  @DisplayName("HQ 作成の店長束スタッフが新規メールでログインでき、授権店舗(A)のみ見え店舗(B)の実データが漏れないこと(AC2/AC3)")
  void hqCreatesStaffAndNewStaffCanLoginWithGrantedScope() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(CASE1_EMAIL, bundlesJson("店長"), "SPECIFIC_STORES", "[" + storeAId + "]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(created.getBody().path("id").asLong()).isPositive();

    ResponseEntity<String> stores =
        rest.exchange(
            "/platform/stores/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(CASE1_EMAIL, PASSWORD))),
            String.class);
    assertThat(stores.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(stores.getBody())
        .as("授権店舗Aは現れ、授権外店舗Bの名称は生ボディに一切現れないこと")
        .contains(STORE_A_NAME)
        .doesNotContain(STORE_B_NAME);
  }

  @Test
  @DisplayName("STAFF_MANAGE 能力の無い利用者では GET/POST /platform/staff が 403(AC4)")
  void nonHqCannotManageStaff() {
    String mgr = platformToken(NON_HQ_EMAIL, PASSWORD);

    ResponseEntity<String> get =
        rest.exchange(
            "/platform/staff", HttpMethod.GET, new HttpEntity<>(bearer(mgr)), String.class);
    assertThat(get.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<String> post =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(
                    "staff-it-forbidden@kizuna.test", bundlesJson("店舗スタッフ"), "ALL_STORES", "[]"),
                bearerJson(mgr)),
            String.class);
    assertThat(post.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("HQ が店舗集合を PUT で B のみへ変更すると、対象スタッフの次回ログインで B のみ見え A は漏れないこと(AC3)")
  void hqUpdatesStaffScopeAndItReflectsOnNextLogin() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(CASE3_EMAIL, bundlesJson("店長"), "SPECIFIC_STORES", "[" + storeAId + "]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
    long staffId = created.getBody().path("id").asLong();
    long version = created.getBody().path("version").asLong();

    ResponseEntity<String> before =
        rest.exchange(
            "/platform/stores/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(CASE3_EMAIL, PASSWORD))),
            String.class);
    assertThat(before.getBody())
        .as("変更前は A のみ")
        .contains(STORE_A_NAME)
        .doesNotContain(STORE_B_NAME);

    ResponseEntity<JsonNode> updated =
        rest.exchange(
            "/platform/staff/" + staffId,
            HttpMethod.PUT,
            new HttpEntity<>(
                updateBody(bundlesJson("店長"), "SPECIFIC_STORES", "[" + storeBId + "]", version),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> after =
        rest.exchange(
            "/platform/stores/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(CASE3_EMAIL, PASSWORD))),
            String.class);
    assertThat(after.getBody())
        .as("再ログイン後は B のみ、A の実データは生ボディに一切現れないこと")
        .contains(STORE_B_NAME)
        .doesNotContain(STORE_A_NAME);
  }

  @Test
  @DisplayName("同一メールの二重作成は 2 回目が 400")
  void duplicateEmailRejected() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    String body = createBody(DUP_EMAIL, bundlesJson("店舗スタッフ"), "ALL_STORES", "[]");

    ResponseEntity<JsonNode> first =
        rest.postForEntity(
            "/platform/staff", new HttpEntity<>(body, bearerJson(hq)), JsonNode.class);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<JsonNode> second =
        rest.postForEntity(
            "/platform/staff", new HttpEntity<>(body, bearerJson(hq)), JsonNode.class);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("存在しない能力束 id での作成は 400 で拒否")
  void unknownBundleRejected() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody("staff-it-unknown-bundle@kizuna.test", "[999999]", "ALL_STORES", "[]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("店舗集合の不変条件違反(SPECIFIC_STORES+空 / ALL_STORES+非空)は 400")
  void invalidStoreScopeRejected() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<JsonNode> emptySpecific =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(
                    "staff-it-empty-specific@kizuna.test",
                    bundlesJson("店長"),
                    "SPECIFIC_STORES",
                    "[]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(emptySpecific.getStatusCode())
        .as("SPECIFIC_STORES で店舗集合が空は 400")
        .isEqualTo(HttpStatus.BAD_REQUEST);

    ResponseEntity<JsonNode> nonEmptyAll =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(
                    "staff-it-nonempty-all@kizuna.test",
                    bundlesJson("店長"),
                    "ALL_STORES",
                    "[" + storeAId + "]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(nonEmptyAll.getStatusCode())
        .as("ALL_STORES で個別店舗指定は 400")
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("存在しない storeId での作成は FK 違反を 400 へ変換して拒否")
  void unknownStoreIdRejected() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(
                    "staff-it-unknown-store@kizuna.test",
                    bundlesJson("店長"),
                    "SPECIFIC_STORES",
                    "[999999]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("スタッフ一覧に CAST が現れず、STAFF は現れること(強断言)")
  void staffListExcludesCastAndMember() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<String> res =
        rest.exchange(
            "/platform/staff", HttpMethod.GET, new HttpEntity<>(bearer(hq)), String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody())
        .as("STAFF は現れ、CAST は一覧の生ボディに一切現れないこと")
        .contains(NON_HQ_EMAIL)
        .doesNotContain(CAST_CANARY_EMAIL);
  }

  @Test
  @DisplayName("兼務(HQ管理者+店長の複数束)のスタッフは中央端点と店舗端点の両方へ到達できること")
  void multiBundleStaffReachesBothConsoles() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    String email = "staff-it-multi@kizuna.test";

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(
                    email, bundlesJson("HQ管理者", "店長"), "SPECIFIC_STORES", "[" + storeAId + "]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);

    String token = platformToken(email, PASSWORD);

    ResponseEntity<String> platform =
        rest.exchange(
            "/platform/stores", HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
    assertThat(platform.getStatusCode()).as("HQ管理者束でプラットフォーム端点へ到達できること").isEqualTo(HttpStatus.OK);

    HttpHeaders storeHeaders = bearer(token);
    storeHeaders.add("X-Role", "store");
    storeHeaders.add("X-Store-ID", String.valueOf(storeAId));
    ResponseEntity<String> store =
        rest.exchange(
            "/store/orders", HttpMethod.GET, new HttpEntity<>(storeHeaders), String.class);
    assertThat(store.getStatusCode()).as("店長束で店舗端点へ到達できること(storeBridge)").isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("停止(enabled=false)後はログイン不可だが一覧に残り、付与履歴に実行主体つきで STOP が記録されること(#382 停止後の記録保全)")
  void stoppedStaffCannotLoginButRecordsRemain() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    String email = "staff-it-stopped@kizuna.test";

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(email, bundlesJson("店舗スタッフ"), "ALL_STORES", "[]"), bearerJson(hq)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
    long staffId = created.getBody().path("id").asLong();
    long version = created.getBody().path("version").asLong();

    // 停止（enabled=false）。授権内容は同値のまま。
    ResponseEntity<JsonNode> stopped =
        rest.exchange(
            "/platform/staff/" + staffId,
            HttpMethod.PUT,
            new HttpEntity<>(
                "{\"bundle_ids\":"
                    + bundlesJson("店舗スタッフ")
                    + ",\"store_scope_type\":\"ALL_STORES\",\"store_ids\":[],\"enabled\":false,"
                    + "\"version\":"
                    + version
                    + "}",
                bearerJson(hq)),
            JsonNode.class);
    assertThat(stopped.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(stopped.getBody().path("enabled").asBoolean()).isFalse();

    // 停止後はログイン不可（DisabledException → 401）。
    ResponseEntity<JsonNode> login =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, PASSWORD),
                jsonHeaders()),
            JsonNode.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // 行は残る: 一覧に停止済みスタッフが現れる（過去の実行主体の記録保持）。
    ResponseEntity<String> list =
        rest.exchange(
            "/platform/staff", HttpMethod.GET, new HttpEntity<>(bearer(hq)), String.class);
    assertThat(list.getBody()).as("停止後も一覧に残ること").contains(email);

    // 付与履歴: GRANT → CHANGE → STOP が実行主体(SEED_EMAIL)つきで残る。
    ResponseEntity<String> history =
        rest.exchange(
            "/platform/staff/" + staffId + "/grant-history",
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            String.class);
    assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(history.getBody())
        .as("履歴に GRANT/STOP と実行主体が残ること")
        .contains("GRANT")
        .contains("STOP")
        .contains(SEED_EMAIL);
  }

  @Test
  @DisplayName("精算範囲(SPECIFIC)つきで付与でき、回読と付与履歴快照に精算次元が現れること(#382 要件5=次元の表現)")
  void settlementScopeDimensionIsExpressible() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    String email = "staff-it-settlement@kizuna.test";
    String body =
        String.format(
            "{\"email\":\"%s\",\"password\":\"%s\",\"display_name\":\"IT表示名\","
                + "\"bundle_ids\":%s,\"store_scope_type\":\"ALL_STORES\",\"store_ids\":[],"
                + "\"settlement_scope_type\":\"SPECIFIC_STORES\",\"settlement_store_ids\":[%d]}",
            email, PASSWORD, bundlesJson("店長"), storeAId);

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/platform/staff", new HttpEntity<>(body, bearerJson(hq)), JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
    long staffId = created.getBody().path("id").asLong();
    assertThat(created.getBody().path("settlement_scope_type").asText())
        .isEqualTo("SPECIFIC_STORES");
    assertThat(created.getBody().path("settlement_store_ids").get(0).asLong()).isEqualTo(storeAId);

    // 回読（一覧）にも精算次元が現れる。
    ResponseEntity<String> list =
        rest.exchange(
            "/platform/staff", HttpMethod.GET, new HttpEntity<>(bearer(hq)), String.class);
    assertThat(list.getBody()).contains("settlement_scope_type");

    // 付与履歴の快照にも精算次元が残る。
    ResponseEntity<String> history =
        rest.exchange(
            "/platform/staff/" + staffId + "/grant-history",
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            String.class);
    assertThat(history.getBody())
        .as("履歴快照に精算範囲が残ること")
        .contains("settlement_scope_type")
        .contains("SPECIFIC_STORES");
  }

  /** 付与履歴の行数を返す（陳腐更新の拒否で履歴が増えないことの断言に使う）。 */
  private int grantHistorySize(String token, long staffId) {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/staff/" + staffId + "/grant-history",
            HttpMethod.GET,
            new HttpEntity<>(bearer(token)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    return res.getBody().size();
  }

  /** スタッフ一覧から email 一致の 1 件を返す（見つからなければ失敗）。 */
  private JsonNode findStaffByEmail(String token, String email) {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/staff", HttpMethod.GET, new HttpEntity<>(bearer(token)), JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    for (JsonNode node : res.getBody()) {
      if (email.equals(node.path("email").asText())) {
        return node;
      }
    }
    throw new AssertionError("スタッフ一覧に " + email + " が見つかりません");
  }

  @Test
  @DisplayName("同一 version の二連 PUT は 2 発目が 409 になり、授権・enabled が巻き戻らず付与履歴も増えないこと(#400 AC1)")
  void staleUpdateWithSameVersionIsRejectedWithoutRollback() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    String email = "staff-it-stale@kizuna.test";

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(email, bundlesJson("店長"), "SPECIFIC_STORES", "[" + storeAId + "]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
    long staffId = created.getBody().path("id").asLong();
    long initialVersion = created.getBody().path("version").asLong();

    // 1 発目: 返却された version での更新は成功し、応答は増加した version を返す。
    ResponseEntity<JsonNode> first =
        rest.exchange(
            "/platform/staff/" + staffId,
            HttpMethod.PUT,
            new HttpEntity<>(
                updateBody(
                    bundlesJson("店長"), "SPECIFIC_STORES", "[" + storeBId + "]", initialVersion),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(first.getBody().path("version").asLong())
        .as("更新成功の応答は増加した version を返すこと")
        .isGreaterThan(initialVersion);

    int historyCountAfterFirst = grantHistorySize(hq, staffId);

    // 2 発目: 同じ（陳腐化した）version で店舗集合を A へ戻し停止も試みる上書きは 409。
    ResponseEntity<JsonNode> second =
        rest.exchange(
            "/platform/staff/" + staffId,
            HttpMethod.PUT,
            new HttpEntity<>(
                "{\"bundle_ids\":"
                    + bundlesJson("店長")
                    + ",\"store_scope_type\":\"SPECIFIC_STORES\",\"store_ids\":["
                    + storeAId
                    + "],\"enabled\":false,\"version\":"
                    + initialVersion
                    + "}",
                bearerJson(hq)),
            JsonNode.class);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    // 授権・enabled は 1 発目の内容のまま巻き戻らない。
    JsonNode target = findStaffByEmail(hq, email);
    assertThat(target.path("enabled").asBoolean()).as("陳腐更新で停止へ巻き戻らないこと").isTrue();
    assertThat(target.path("store_ids")).hasSize(1);
    assertThat(target.path("store_ids").get(0).asLong())
        .as("店舗集合は 1 発目の B のまま残ること")
        .isEqualTo(storeBId);

    // 付与履歴の行も増えない。
    assertThat(grantHistorySize(hq, staffId))
        .as("拒否された陳腐更新で付与履歴が増えないこと")
        .isEqualTo(historyCountAfterFirst);
  }

  @Test
  @DisplayName("陳腐 version による停止解除は 409 で拒否され、停止済みアカウントが静黙復活しないこと(#400 AC2)")
  void staleResumeIsRejectedAndUserStaysStopped() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    String email = "staff-it-stale-resume@kizuna.test";

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(email, bundlesJson("店舗スタッフ"), "ALL_STORES", "[]"), bearerJson(hq)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
    long staffId = created.getBody().path("id").asLong();
    long preStopVersion = created.getBody().path("version").asLong();

    // 現行 version で停止する（成功）。
    ResponseEntity<JsonNode> stopped =
        rest.exchange(
            "/platform/staff/" + staffId,
            HttpMethod.PUT,
            new HttpEntity<>(
                "{\"bundle_ids\":"
                    + bundlesJson("店舗スタッフ")
                    + ",\"store_scope_type\":\"ALL_STORES\",\"store_ids\":[],\"enabled\":false,"
                    + "\"version\":"
                    + preStopVersion
                    + "}",
                bearerJson(hq)),
            JsonNode.class);
    assertThat(stopped.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(stopped.getBody().path("enabled").asBoolean()).isFalse();

    // 停止前の陳腐 version による再開（enabled=true）の試みは 409。
    ResponseEntity<JsonNode> resumeAttempt =
        rest.exchange(
            "/platform/staff/" + staffId,
            HttpMethod.PUT,
            new HttpEntity<>(
                "{\"bundle_ids\":"
                    + bundlesJson("店舗スタッフ")
                    + ",\"store_scope_type\":\"ALL_STORES\",\"store_ids\":[],\"enabled\":true,"
                    + "\"version\":"
                    + preStopVersion
                    + "}",
                bearerJson(hq)),
            JsonNode.class);
    assertThat(resumeAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    // 停止のまま: 一覧でも enabled=false、ログインも不可。
    assertThat(findStaffByEmail(hq, email).path("enabled").asBoolean())
        .as("陳腐 version の停止解除で停止済みアカウントが復活しないこと")
        .isFalse();
    ResponseEntity<JsonNode> login =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, PASSWORD),
                jsonHeaders()),
            JsonNode.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("能力束一覧は STAFF_MANAGE 保持者に既定 3 束を返し、非保持者には 403")
  void capabilityBundleListingRequiresStaffManage() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<String> bundles =
        rest.exchange(
            "/platform/capability-bundles",
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            String.class);
    assertThat(bundles.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(bundles.getBody()).contains("HQ管理者").contains("店長").contains("店舗スタッフ");

    String nonHq = platformToken(NON_HQ_EMAIL, PASSWORD);
    ResponseEntity<String> forbidden =
        rest.exchange(
            "/platform/capability-bundles",
            HttpMethod.GET,
            new HttpEntity<>(bearer(nonHq)),
            String.class);
    assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
