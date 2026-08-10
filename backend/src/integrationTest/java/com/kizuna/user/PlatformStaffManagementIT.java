package com.kizuna.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
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
 * スタッフ・ロール管理（RBAC）の HTTP 境界統合テスト。STAFF_MANAGE 権限限定の授権書き込み（付与・変更・停止）とロール
 * CRUD、付与した店舗集合が本人の次回ログインのデータ範囲に反映されること、 授権外店舗の実データが応答生ボディに一切現れないこと（強断言）を本物の PostgreSQL で固定する。ヘルパは
 * {@link com.kizuna.order.PlatformOrderScopeIT} の {@code ensurePlatformUser}/{@code platformToken}
 * 様式を踏襲し、強断言様式は {@link com.kizuna.menu.MenuCrossStoreIT} に由来する。
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
  @Autowired private RoleRepository roleRepository;

  private long storeAId;
  private long storeBId;

  @BeforeEach
  void prepareStaffFixture() {
    storeAId = ensureStore(STORE_A_DOMAIN, STORE_A_NAME);
    storeBId = ensureStore(STORE_B_DOMAIN, STORE_B_NAME);
    ensurePlatformUser(
        NON_HQ_EMAIL, UserType.STAFF, roleIdsOf("店長"), StoreScopeType.ALL_STORES, Set.of());
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
                        .displayName("スタッフ管理IT " + userType.name())
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

  private String platformToken(String email, String password) {
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, password),
                jsonHeaders()),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 平台ログインが成功すること").isEqualTo(HttpStatus.OK);
    String t = res.getBody().path("token").asString();
    assertThat(t).isNotBlank();
    return t;
  }

  private static String createBody(
      String email, String roleIdsJson, String scopeType, String storeIds) {
    return String.format(
        "{\"email\":\"%s\",\"password\":\"%s\",\"display_name\":\"IT表示名\",\"role_ids\":%s,"
            + "\"store_scope_type\":\"%s\",\"store_ids\":%s}",
        email, PASSWORD, roleIdsJson, scopeType, storeIds);
  }

  private static String updateBody(
      String roleIdsJson, String scopeType, String storeIds, long version) {
    return String.format(
        "{\"role_ids\":%s,\"store_scope_type\":\"%s\",\"store_ids\":%s,\"version\":%d}",
        roleIdsJson, scopeType, storeIds, version);
  }

  /** 束名を JSON の id 配列へ解決する（例: ["店長"] → "[3]"）。 */
  private String rolesJson(String... roleNames) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < roleNames.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(roleRepository.findByName(roleNames[i]).orElseThrow().getId());
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
                createBody(CASE1_EMAIL, rolesJson("店長"), "SPECIFIC_STORES", "[" + storeAId + "]"),
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
                    "staff-it-forbidden@kizuna.test", rolesJson("店舗スタッフ"), "ALL_STORES", "[]"),
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
                createBody(CASE3_EMAIL, rolesJson("店長"), "SPECIFIC_STORES", "[" + storeAId + "]"),
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
                updateBody(rolesJson("店長"), "SPECIFIC_STORES", "[" + storeBId + "]", version),
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
    String body = createBody(DUP_EMAIL, rolesJson("店舗スタッフ"), "ALL_STORES", "[]");

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
  @DisplayName("列長(255)を超えるメールでの作成は email 由来の 400 で拒否され、副作用がないこと")
  void tooLongEmailCreateIsRejected() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    // t_users.email は VARCHAR(255)。@Email は形式のみで長さを見ないため、@Size が無いと列長違反が
    // 保存時の DataIntegrityViolationException 変換に吸われ「指定された店舗が存在しません」へ誤帰属する。
    // ローカル部は @Email の上限 64 文字、ドメインはラベル 63 文字以内で組み、形式は合法のまま 255 字を超える。
    String label = "a".repeat(63);
    String email = "b".repeat(64) + "@" + label + "." + label + "." + label + ".kizuna.test";

    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(email, rolesJson("店舗スタッフ"), "ALL_STORES", "[]"), bearerJson(hq)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody().path("details").has("email"))
        .as("列長超過は email 由来の検証エラーとして返ること（店舗エラーへの誤帰属でないこと）")
        .isTrue();
    assertThat(platformUserRepository.findByEmail(email)).isEmpty();
  }

  @Test
  @DisplayName("小文字化で列長(255)を超えるメールでの作成は email 由来の 400 で拒否され、副作用がないこと")
  void lowercaseExpandingEmailCreateIsRejected() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    // U+0130 は永続化前の小文字化で 2 文字に伸長する。原文 146 字は @Email を通過するが
    // 小文字化後は 280 字となり VARCHAR(255) を超えるため、@Size は伸長を見込んだ上限で拒否する必要がある。
    String label = "İ".repeat(10);
    String email = "İ".repeat(64) + "@" + (label + ".").repeat(7) + "test";

    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(email, rolesJson("店舗スタッフ"), "ALL_STORES", "[]"), bearerJson(hq)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody().path("details").has("email"))
        .as("伸長超過は email 由来の検証エラーとして返ること（店舗エラーへの誤帰属でないこと）")
        .isTrue();
    assertThat(platformUserRepository.findByEmail(email.toLowerCase(Locale.ROOT))).isEmpty();
  }

  @Test
  @DisplayName("検証エラーの details のキーが、要求で送ったのと同じ綴り（snake_case）で返ること")
  void validationDetailKeysMatchRequestSpelling() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    // display_name は複数語なので、Bean のプロパティ名（displayName）で返れば往復で綴りが食い違う。
    String body =
        String.format(
            "{\"email\":\"staff-it-wire-name@kizuna.test\",\"password\":\"%s\","
                + "\"display_name\":\"\",\"role_ids\":%s,"
                + "\"store_scope_type\":\"ALL_STORES\",\"store_ids\":[]}",
            PASSWORD, rolesJson("店舗スタッフ"));

    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/staff", new HttpEntity<>(body, bearerJson(hq)), JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode details = res.getBody().path("details");
    assertThat(details.has("display_name")).as("送信時と同じキーで返ること").isTrue();
    assertThat(details.has("displayName")).as("Bean のプロパティ名を並記もしないこと").isFalse();
  }

  @Test
  @DisplayName("存在しない能力束 id での作成は 400 で拒否")
  void unknownRoleRejected() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody("staff-it-unknown-role@kizuna.test", "[999999]", "ALL_STORES", "[]"),
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
                    rolesJson("店長"),
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
                    rolesJson("店長"),
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

    // create は店舗 id を事前検証しないため、実 DB の FK 違反が決定的に踏める。制約名の抽出が機能しなくなれば
    // 兜底へ溢れて 500 になり、誤った制約へ帰属すれば文言が変わる — 実ドライバから制約名が取れることの機械証明。
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(
                    "staff-it-unknown-store@kizuna.test",
                    rolesJson("店長"),
                    "SPECIFIC_STORES",
                    "[999999]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody().path("error").asString()).isEqualTo("指定された店舗が存在しません");
  }

  // 検索語は STAFF 限定と AND で重なる。両者の email に共通する接頭辞で引くことで、
  // 「検索で拾える範囲にいてもなお CAST は現れない」ことまで断言する。size はページ境界に
  // 隠れて偽陰性にならないよう、IT が作る件数より十分大きく取る。
  @Test
  @DisplayName("スタッフ一覧に CAST が現れず、STAFF は現れること(強断言)")
  void staffListExcludesCastAndMember() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<String> res =
        rest.exchange(
            "/platform/staff?search=staff-it-&size=100",
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody())
        .as("STAFF は現れ、CAST は一覧の生ボディに一切現れないこと")
        .contains(NON_HQ_EMAIL)
        .doesNotContain(CAST_CANARY_EMAIL);
  }

  @Test
  @DisplayName("スタッフ一覧は search で表示名・メールを横断して絞り込み、Spring Page 形で返すこと")
  void staffListFiltersBySearchAndReturnsPage() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/staff?search=" + NON_HQ_EMAIL,
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().path("total_elements").asLong()).isEqualTo(1);
    assertThat(res.getBody().path("content").get(0).path("email").asString())
        .isEqualTo(NON_HQ_EMAIL);

    // 該当なしは空ページ（404 でも 500 でもない）
    ResponseEntity<JsonNode> none =
        rest.exchange(
            "/platform/staff?search=staff-it-no-such-person",
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            JsonNode.class);
    assertThat(none.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(none.getBody().path("total_elements").asLong()).isZero();
  }

  // 店舗絞り込みは「担当範囲がその店舗を覆う」行だけを残す。ALL_STORES は個別 id を持たないまま全店舗を覆うため、
  // 店舗集合の member of だけでは拾えない側の代表として必ず含まれること、他店舗専任が消えること、検索語と AND で
  // 重なることを 1 件ずつ id で断言する。
  @Test
  @DisplayName("スタッフ一覧は storeId で担当範囲に該当する行だけへ絞り込めること")
  void staffListFiltersByStoreId() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    String marker = "staff-it-storefilter-" + UUID.randomUUID();
    long onlyStoreA =
        createStaffWithDisplayName(
            marker + "-a", "staff-it-sf-a-", StoreScopeType.SPECIFIC_STORES, Set.of(storeAId));
    long onlyStoreB =
        createStaffWithDisplayName(
            marker + "-b", "staff-it-sf-b-", StoreScopeType.SPECIFIC_STORES, Set.of(storeBId));
    // 表示名の接尾辞は互いに部分一致しないこと（"-all" は "-a" を含むため検索語 AND の断言が壊れる）
    long allStores =
        createStaffWithDisplayName(
            marker + "-zen", "staff-it-sf-zen-", StoreScopeType.ALL_STORES, Set.of());

    assertThat(staffIds(hq, "?search=" + marker + "&size=100"))
        .as("前提: 絞り込みなしでは 3 件とも見えること")
        .containsExactlyInAnyOrder(onlyStoreA, onlyStoreB, allStores);
    assertThat(staffIds(hq, "?search=" + marker + "&storeId=" + storeAId + "&size=100"))
        .as("店舗A 専任と全店舗担当だけが残ること")
        .containsExactlyInAnyOrder(onlyStoreA, allStores);
    assertThat(staffIds(hq, "?search=" + marker + "&storeId=" + storeBId + "&size=100"))
        .as("店舗B 側でも同じ規則が成り立つこと")
        .containsExactlyInAnyOrder(onlyStoreB, allStores);
    assertThat(staffIds(hq, "?search=" + marker + "-a&storeId=" + storeAId + "&size=100"))
        .as("検索語と AND で重なること")
        .containsExactly(onlyStoreA);
  }

  /** 一覧を取得し、content の id を列挙する。 */
  private Set<Long> staffIds(String token, String query) {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/staff" + query,
            HttpMethod.GET,
            new HttpEntity<>(bearer(token)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    Set<Long> ids = new HashSet<>();
    res.getBody().path("content").forEach(node -> ids.add(node.path("id").asLong()));
    return ids;
  }

  // 呼出側が ?sort= で既定値（表示名 + id）を上書きしても、id が副キーとして補われることで
  // 表示名が重複する行の間でもページ境界を跨いだ取りこぼし・重複が起きないこと。
  @Test
  @DisplayName("スタッフ一覧は表示名が重複しても sort 上書き時に取りこぼし・重複なくページングできること")
  void staffListPagesWithoutGapsOrDuplicatesWhenSortIsOverridden() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    String duplicateName = "staff-it-duplicate-displayname-" + UUID.randomUUID();
    long dupId1 =
        createStaffWithDisplayName(
            duplicateName, "staff-it-dup1-", StoreScopeType.ALL_STORES, Set.of());
    long dupId2 =
        createStaffWithDisplayName(
            duplicateName, "staff-it-dup2-", StoreScopeType.ALL_STORES, Set.of());

    ResponseEntity<JsonNode> page0 =
        rest.exchange(
            "/platform/staff?search=" + duplicateName + "&sort=displayName&size=1&page=0",
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            JsonNode.class);
    ResponseEntity<JsonNode> page1 =
        rest.exchange(
            "/platform/staff?search=" + duplicateName + "&sort=displayName&size=1&page=1",
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            JsonNode.class);
    assertThat(page0.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(page1.getStatusCode()).isEqualTo(HttpStatus.OK);

    long id0 = page0.getBody().path("content").get(0).path("id").asLong();
    long id1 = page1.getBody().path("content").get(0).path("id").asLong();
    assertThat(Set.of(id0, id1))
        .as("id 副キーが保たれ、1 件ずつの取得で両方の対象が重複なく揃うこと")
        .isEqualTo(Set.of(dupId1, dupId2));
  }

  /** 表示名と担当店舗集合を指定してスタッフを作成し、id を返す（ページング・店舗絞り込みの検証用）。 */
  private long createStaffWithDisplayName(
      String displayName, String emailPrefix, StoreScopeType scopeType, Set<Long> storeIds) {
    return platformUserRepository
        .save(
            PlatformUser.builder()
                .email(emailPrefix + UUID.randomUUID() + "@kizuna.test")
                .password(passwordEncoder.encode(PASSWORD))
                .displayName(displayName)
                .enabled(true)
                .userType(UserType.STAFF)
                .roleIds(roleIdsOf("店長"))
                .storeScopeType(scopeType)
                .storeIds(storeIds)
                .build())
        .getId();
  }

  // 一覧の現在ページに居ない対象でも最新の版を取り直せる経路（競合後の再試行に要る）。
  // 一覧・作成と同じく CAST/MEMBER は不可視のため 404。
  @Test
  @DisplayName("GET /platform/staff/{id} は STAFF を返し、CAST は 404 になること")
  void getStaffByIdReturnsStaffAndHidesCast() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    long staffId = platformUserRepository.findByEmail(NON_HQ_EMAIL).orElseThrow().getId();
    long castId = platformUserRepository.findByEmail(CAST_CANARY_EMAIL).orElseThrow().getId();

    ResponseEntity<JsonNode> found =
        rest.exchange(
            "/platform/staff/" + staffId,
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            JsonNode.class);
    assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(found.getBody().path("email").asString()).isEqualTo(NON_HQ_EMAIL);
    assertThat(found.getBody().path("version").isNumber()).isTrue();

    ResponseEntity<String> cast =
        rest.exchange(
            "/platform/staff/" + castId,
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            String.class);
    assertThat(cast.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    ResponseEntity<String> forbidden =
        rest.exchange(
            "/platform/staff/" + staffId,
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(NON_HQ_EMAIL, PASSWORD))),
            String.class);
    assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  // "staff-it-n_nhq" は _ をワイルドカードとして扱うと staff-it-nonhq に一致してしまう。
  // 検索語は字面として照合されるべきなので 0 件が正。
  @Test
  @DisplayName("検索語中の LIKE メタ文字は字面として扱われ、ワイルドカードにならないこと")
  void staffSearchTreatsLikeMetacharactersAsLiterals() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<JsonNode> underscore =
        rest.exchange(
            "/platform/staff?search=staff-it-n_nhq",
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            JsonNode.class);
    assertThat(underscore.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(underscore.getBody().path("total_elements").asLong()).isZero();

    ResponseEntity<JsonNode> percent =
        rest.exchange(
            "/platform/staff?search=staff-it-%25nonhq",
            HttpMethod.GET, new HttpEntity<>(bearer(hq)), JsonNode.class);
    assertThat(percent.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(percent.getBody().path("total_elements").asLong()).isZero();
  }

  @Test
  @DisplayName("兼務(HQ管理者+店長の複数束)のスタッフは中央端点と店舗端点の両方へ到達できること")
  void multiRoleStaffReachesBothConsoles() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    String email = "staff-it-multi@kizuna.test";

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(
                    email, rolesJson("HQ管理者", "店長"), "SPECIFIC_STORES", "[" + storeAId + "]"),
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
  @DisplayName("停止(enabled=false)後はログイン不可だが一覧には残ること(停止後の記録保全)")
  void stoppedStaffCannotLoginButRecordsRemain() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    String email = "staff-it-stopped@kizuna.test";

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(email, rolesJson("店舗スタッフ"), "ALL_STORES", "[]"), bearerJson(hq)),
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
                "{\"role_ids\":"
                    + rolesJson("店舗スタッフ")
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
            "/platform/staff?search=" + email,
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            String.class);
    assertThat(list.getBody()).as("停止後も一覧に残ること").contains(email);
  }

  /**
   * スタッフ一覧から email 一致の 1 件を返す（見つからなければ失敗）。
   *
   * <p>一覧は Spring Page 形のため、対象がページ境界の向こうに隠れないよう email そのもので絞り込んでから {@code content} を走査する。
   */
  private JsonNode findStaffByEmail(String token, String email) {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/staff?search=" + email,
            HttpMethod.GET,
            new HttpEntity<>(bearer(token)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    for (JsonNode node : res.getBody().path("content")) {
      if (email.equals(node.path("email").asString())) {
        return node;
      }
    }
    throw new AssertionError("スタッフ一覧に " + email + " が見つかりません");
  }

  @Test
  @DisplayName("同一 version の二連 PUT は 2 発目が 409 になり、授権・enabled が巻き戻らないこと(AC1)")
  void staleUpdateWithSameVersionIsRejectedWithoutRollback() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    String email = "staff-it-stale@kizuna.test";

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(email, rolesJson("店長"), "SPECIFIC_STORES", "[" + storeAId + "]"),
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
                    rolesJson("店長"), "SPECIFIC_STORES", "[" + storeBId + "]", initialVersion),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(first.getBody().path("version").asLong())
        .as("更新成功の応答は増加した version を返すこと")
        .isGreaterThan(initialVersion);

    // 2 発目: 同じ（陳腐化した）version で店舗集合を A へ戻し停止も試みる上書きは 409。
    ResponseEntity<JsonNode> second =
        rest.exchange(
            "/platform/staff/" + staffId,
            HttpMethod.PUT,
            new HttpEntity<>(
                "{\"role_ids\":"
                    + rolesJson("店長")
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
  }

  @Test
  @DisplayName("陳腐 version による停止解除は 409 で拒否され、停止済みアカウントが静黙復活しないこと(AC2)")
  void staleResumeIsRejectedAndUserStaysStopped() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    String email = "staff-it-stale-resume@kizuna.test";

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(email, rolesJson("店舗スタッフ"), "ALL_STORES", "[]"), bearerJson(hq)),
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
                "{\"role_ids\":"
                    + rolesJson("店舗スタッフ")
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
                "{\"role_ids\":"
                    + rolesJson("店舗スタッフ")
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
  @DisplayName("ロール一覧は STAFF_MANAGE 保持者に既定 3 ロールを返し、非保持者には 403")
  void roleListingRequiresStaffManage() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<JsonNode> roles =
        rest.exchange(
            "/platform/roles", HttpMethod.GET, new HttpEntity<>(bearer(hq)), JsonNode.class);
    assertThat(roles.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(roles.getBody().toString()).contains("HQ管理者").contains("店長").contains("店舗スタッフ");
    // 一覧は要約のみ：権限は個数で返し、コードの列挙（permissions）は詳細取得に委ねる。
    JsonNode firstRole = roles.getBody().get(0);
    assertThat(firstRole.path("permission_count").asLong()).isPositive();
    assertThat(firstRole.has("permissions")).isFalse();

    String nonHq = platformToken(NON_HQ_EMAIL, PASSWORD);
    ResponseEntity<String> forbidden =
        rest.exchange(
            "/platform/roles", HttpMethod.GET, new HttpEntity<>(bearer(nonHq)), String.class);
    assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("権限目録は STAFF_MANAGE 保持者に 16 件の code+console を返すこと")
  void permissionCatalogIsExposedToStaffManage() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/permissions", HttpMethod.GET, new HttpEntity<>(bearer(hq)), JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody()).hasSize(16);
    assertThat(res.getBody().toString()).contains("ORDER_MANAGE").contains("PLATFORM");
  }

  @Test
  @DisplayName("自作ロールは作成・授与でき、授与中は削除が 409、既定ロールの改廃は 400 になること")
  void customRoleLifecycleAndSystemRoleImmutability() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    // 自作ロールの作成（system=false）。
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/platform/roles",
            new HttpEntity<>(
                "{\"name\":\"スタッフ管理IT_受付担当\",\"permissions\":[\"ORDER_MANAGE\"]}", bearerJson(hq)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
    long roleId = created.getBody().path("id").asLong();
    assertThat(created.getBody().path("system").asBoolean()).isFalse();
    assertThat(created.getBody().path("permissions").get(0).asString()).isEqualTo("ORDER_MANAGE");

    // 詳細は編集フォーム向けに権限コードの列挙と version を返す。
    ResponseEntity<JsonNode> detail =
        rest.exchange(
            "/platform/roles/" + roleId,
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            JsonNode.class);
    assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(detail.getBody().path("permissions").get(0).asString()).isEqualTo("ORDER_MANAGE");
    assertThat(detail.getBody().has("version")).isTrue();

    // 存在しない権限コードは 400。
    ResponseEntity<String> unknownPermission =
        rest.postForEntity(
            "/platform/roles",
            new HttpEntity<>(
                "{\"name\":\"スタッフ管理IT_不正権限\",\"permissions\":[\"NOT_A_PERMISSION\"]}",
                bearerJson(hq)),
            String.class);
    assertThat(unknownPermission.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // 未授与のうちは削除できる版を作るため、まず別ロールで授与→削除拒否を確認する。
    ResponseEntity<JsonNode> staff =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(
                    "staff-it-customrole@kizuna.test", "[" + roleId + "]", "ALL_STORES", "[]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(staff.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> deleteInUse =
        rest.exchange(
            "/platform/roles/" + roleId,
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(hq)),
            String.class);
    assertThat(deleteInUse.getStatusCode()).as("授与中のロール削除は 409").isEqualTo(HttpStatus.CONFLICT);

    // 既定ロール（is_system）の改名・削除は 400。
    long systemRoleId = roleRepository.findByName("店長").orElseThrow().getId();
    ResponseEntity<String> renameSystem =
        rest.exchange(
            "/platform/roles/" + systemRoleId,
            HttpMethod.PUT,
            new HttpEntity<>(
                "{\"name\":\"改名試行\",\"permissions\":[\"ORDER_MANAGE\"],\"version\":0}",
                bearerJson(hq)),
            String.class);
    assertThat(renameSystem.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    ResponseEntity<String> deleteSystem =
        rest.exchange(
            "/platform/roles/" + systemRoleId,
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(hq)),
            String.class);
    assertThat(deleteSystem.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // 未授与の自作ロールは削除できる（204）。
    ResponseEntity<JsonNode> unused =
        rest.postForEntity(
            "/platform/roles",
            new HttpEntity<>(
                "{\"name\":\"スタッフ管理IT_未授与\",\"permissions\":[\"CUSTOMER_MANAGE\"]}",
                bearerJson(hq)),
            JsonNode.class);
    assertThat(unused.getStatusCode()).isEqualTo(HttpStatus.OK);
    ResponseEntity<String> deleted =
        rest.exchange(
            "/platform/roles/" + unused.getBody().path("id").asLong(),
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(hq)),
            String.class);
    assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }
}
