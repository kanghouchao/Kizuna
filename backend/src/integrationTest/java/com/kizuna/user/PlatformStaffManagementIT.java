package com.kizuna.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.domain.Permission;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.sql.DataSource;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 管理者管理・ロール管理（RBAC）の HTTP 境界統合テスト。ROLE_MANAGE 権限限定の授権書き込み（付与・変更・停止）とロール CRUD、扱う対象が HQ
 * 側ロール保持者に限られること、付与した店舗集合が本人の次回ログインのデータ範囲に反映されること、 授権外店舗の実データが応答生ボディに一切現れないこと（強断言）を本物の PostgreSQL
 * で固定する。ヘルパは {@link com.kizuna.order.PlatformOrderScopeIT} の {@code ensurePlatformUser}/{@code
 * platformToken} 様式を踏襲し、強断言様式は {@link com.kizuna.menu.MenuCrossStoreIT} に由来する。
 *
 * <p>不減零（ADR 0020 の守衛 G5）のうち、授権側（停止・降格）の拒否はここでは固定できない — 母集団は DB 全体の ROLE_MANAGE 実効保持者で、種子の HQ
 * 管理者を含む他の行を止めないと「最後の 1 人」を作れず、それは後続 IT を連鎖破綻させる。拒否の判定は {@code PlatformStaffServiceTest}
 * が持ち、ここは母集団が残る側（降格が通ること）で照会そのものが実 DB で成立することを固定する。ロール定義側の拒否は 母集団の供給元を 1
 * ロールへ縮められるので、他の保持者を一時停止して撃ち、必ず戻す。
 */
class PlatformStaffManagementIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "pass";

  /** 新規作成の要求が通る合言葉。要求側の最小 8 文字を満たす必要があり、種子の合言葉は使えない。 */
  private static final String NEW_ACCOUNT_PASSWORD = "pass1234";

  /** ALL_STORES の HQ 管理者シード（seed/04-platform-admin.yaml）。 */
  private static final String SEED_EMAIL = "admin@kizuna.test";

  /** 授権判定に使う 2 店舗。名称そのものを漏洩検知のカナリアに用いる。 */
  private static final String STORE_A_DOMAIN = "staff-it-store-a.kizuna.test";

  private static final String STORE_A_NAME = "スタッフ管理IT_店舗A授権マーカー";
  private static final String STORE_B_DOMAIN = "staff-it-store-b.kizuna.test";
  private static final String STORE_B_NAME = "スタッフ管理IT_店舗B機密";

  /** 店舗側ロールのみの利用者。管理者管理の門でも対象絞りでも弾かれる側のカナリア。 */
  private static final String NON_HQ_EMAIL = "staff-it-nonhq@kizuna.test";

  /** HQ 側ロール（PLATFORM 権限を含む）だけを持つ利用者。管理者管理に現れる側の代表。 */
  private static final String HQ_SIDE_EMAIL = "staff-it-hqside@kizuna.test";

  /** 種子に無い HQ 側ロール。ROLE_MANAGE は含めず、不減零の母集団を膨らませない。 */
  private static final String HQ_SIDE_ROLE = "スタッフ管理IT_HQ側";

  /** ROLE_MANAGE を含む IT 専用ロールと、その保持者（不減零の母集団を種子に触らず動かすため）。 */
  private static final String ROLE_MANAGE_ROLE = "スタッフ管理IT_管理権限";

  private static final String RACE_EMAIL = "staff-it-race@kizuna.test";

  /** ロール定義の編集経路で不減零を撃つための自作ロールと、その唯一の保持者。 */
  private static final String ROLE_EDIT_ROLE = "スタッフ管理IT_編集対象管理権限";

  private static final String ROLE_EDIT_HOLDER_EMAIL = "staff-it-roleedit@kizuna.test";

  /** 委譲層だけを持つ利用者（STAFF_MANAGE のみ）。ロール定義の門が ROLE_MANAGE であることの検証に使う。 */
  private static final String STAFF_MANAGE_ONLY_EMAIL = "staff-it-staffmanage-only@kizuna.test";

  /** 種子に無いロール（DB データとして追加）。 */
  private static final String STAFF_MANAGE_ONLY_ROLE = "スタッフ管理IT_委譲層のみ";

  private static final String CAST_CANARY_EMAIL = "staff-it-cast-canary@kizuna.test";

  private static final String CASE1_EMAIL = "staff-it-created@kizuna.test";
  private static final String CASE3_EMAIL = "staff-it-editable@kizuna.test";
  private static final String DUP_EMAIL = "staff-it-dup@kizuna.test";

  @Autowired private StoreRepository storeRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PermissionRepository permissionRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

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
    ensurePlatformUser(
        HQ_SIDE_EMAIL, UserType.STAFF, Set.of(hqSideRoleId()), StoreScopeType.ALL_STORES, Set.of());

    Role staffManageOnly =
        roleRepository
            .findByName(STAFF_MANAGE_ONLY_ROLE)
            .orElseGet(
                () ->
                    roleRepository.save(
                        Role.builder()
                            .name(STAFF_MANAGE_ONLY_ROLE)
                            .permissionIds(permissionIdsOf(PermissionCode.STAFF_MANAGE))
                            .build()));
    ensurePlatformUser(
        STAFF_MANAGE_ONLY_EMAIL,
        UserType.STAFF,
        Set.of(staffManageOnly.getId()),
        StoreScopeType.ALL_STORES,
        Set.of());
  }

  private Set<Long> permissionIdsOf(PermissionCode... codes) {
    Set<String> names = Arrays.stream(codes).map(PermissionCode::name).collect(Collectors.toSet());
    return permissionRepository.findByCodeIn(names).stream()
        .map(Permission::getId)
        .collect(Collectors.toSet());
  }

  /**
   * HQ 側ロールの id。STORE_MANAGE（PLATFORM）でロールを HQ 側にし、STORE_VIEW（SHARED）で {@code /platform/stores/me}
   * まで到達させる。ROLE_MANAGE は含めないので、この束の保持者を止めても不減零には触れない。
   */
  private long hqSideRoleId() {
    return roleRepository
        .findByName(HQ_SIDE_ROLE)
        .orElseGet(
            () ->
                roleRepository.save(
                    Role.builder()
                        .name(HQ_SIDE_ROLE)
                        .permissionIds(
                            permissionIdsOf(PermissionCode.STORE_MANAGE, PermissionCode.STORE_VIEW))
                        .build()))
        .getId();
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
        email, NEW_ACCOUNT_PASSWORD, roleIdsJson, scopeType, storeIds);
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
                createBody(
                    CASE1_EMAIL, rolesJson(HQ_SIDE_ROLE), "SPECIFIC_STORES", "[" + storeAId + "]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().path("id").asLong()).isPositive();

    ResponseEntity<String> stores =
        rest.exchange(
            "/platform/stores/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(CASE1_EMAIL, NEW_ACCOUNT_PASSWORD))),
            String.class);
    assertThat(stores.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(stores.getBody())
        .as("授権店舗Aは現れ、授権外店舗Bの名称は生ボディに一切現れないこと")
        .contains(STORE_A_NAME)
        .doesNotContain(STORE_B_NAME);
  }

  @Test
  @DisplayName("ROLE_MANAGE 権限の無い利用者では GET/POST /platform/staff が 403(AC4)")
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
                createBody(
                    CASE3_EMAIL, rolesJson(HQ_SIDE_ROLE), "SPECIFIC_STORES", "[" + storeAId + "]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    long staffId = created.getBody().path("id").asLong();
    long version = created.getBody().path("version").asLong();

    ResponseEntity<String> before =
        rest.exchange(
            "/platform/stores/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(CASE3_EMAIL, NEW_ACCOUNT_PASSWORD))),
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
                updateBody(
                    rolesJson(HQ_SIDE_ROLE), "SPECIFIC_STORES", "[" + storeBId + "]", version),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> after =
        rest.exchange(
            "/platform/stores/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(CASE3_EMAIL, NEW_ACCOUNT_PASSWORD))),
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
    String body = createBody(DUP_EMAIL, rolesJson(HQ_SIDE_ROLE), "ALL_STORES", "[]");

    ResponseEntity<JsonNode> first =
        rest.postForEntity(
            "/platform/staff", new HttpEntity<>(body, bearerJson(hq)), JsonNode.class);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

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
                createBody(email, rolesJson(HQ_SIDE_ROLE), "ALL_STORES", "[]"), bearerJson(hq)),
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
                createBody(email, rolesJson(HQ_SIDE_ROLE), "ALL_STORES", "[]"), bearerJson(hq)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody().path("details").has("email"))
        .as("伸長超過は email 由来の検証エラーとして返ること（店舗エラーへの誤帰属でないこと）")
        .isTrue();
    assertThat(platformUserRepository.findByEmail(email.toLowerCase(Locale.ROOT))).isEmpty();
  }

  @Test
  @DisplayName("列長を超える表示名は 400 で撥ねること（整合性違反まで届かせない）")
  void overlongDisplayNameIsRejectedAsClientError() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    String email = "staff-it-longname@kizuna.test";
    String body =
        String.format(
            "{\"email\":\"%s\",\"password\":\"%s\",\"display_name\":\"%s\",\"role_ids\":%s,"
                + "\"store_scope_type\":\"ALL_STORES\",\"store_ids\":[]}",
            email, NEW_ACCOUNT_PASSWORD, "あ".repeat(151), rolesJson(HQ_SIDE_ROLE));

    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/staff", new HttpEntity<>(body, bearerJson(hq)), JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody().path("details").has("display_name"))
        .as("列長超過は display_name 由来の検証エラーとして返ること")
        .isTrue();
    assertThat(platformUserRepository.findByEmail(email)).isEmpty();
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
            NEW_ACCOUNT_PASSWORD, rolesJson(HQ_SIDE_ROLE));

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
                    rolesJson(HQ_SIDE_ROLE),
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
                    rolesJson(HQ_SIDE_ROLE),
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
                    rolesJson(HQ_SIDE_ROLE),
                    "SPECIFIC_STORES",
                    "[999999]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody().path("error").asString()).isEqualTo("指定された店舗が存在しません");
  }

  // 検索語は対象絞りと AND で重なる。3 者に共通する接頭辞で引くことで、「検索で拾える範囲にいてもなお
  // CAST も店舗側ロールのみの利用者も現れない」ことまで断言する。size はページ境界に隠れて偽陰性に
  // ならないよう、IT が作る件数より十分大きく取る。
  @Test
  @DisplayName("管理者一覧に CAST も店舗側ロールのみの利用者も現れず、HQ 側ロール保持者は現れること(強断言・AC1)")
  void administratorListShowsOnlyHqSideRoleHolders() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<String> res =
        rest.exchange(
            "/platform/staff?search=staff-it-&size=100",
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody())
        .as("HQ 側ロール保持者は現れ、CAST と店舗側ロールのみの利用者は一覧の生ボディに一切現れないこと")
        .contains(HQ_SIDE_EMAIL)
        .doesNotContain(CAST_CANARY_EMAIL)
        .doesNotContain(NON_HQ_EMAIL);
  }

  @Test
  @DisplayName("管理者一覧は search で表示名・メールを横断して絞り込み、Spring Page 形で返すこと")
  void staffListFiltersBySearchAndReturnsPage() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/staff?search=" + HQ_SIDE_EMAIL,
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().path("total_elements").asLong()).isEqualTo(1);
    assertThat(res.getBody().path("content").get(0).path("email").asString())
        .isEqualTo(HQ_SIDE_EMAIL);

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
                .roleIds(Set.of(hqSideRoleId()))
                .storeScopeType(scopeType)
                .storeIds(storeIds)
                .build())
        .getId();
  }

  // 一覧の現在ページに居ない対象でも最新の版を取り直せる経路（競合後の再試行に要る）。
  // 一覧・作成と同じく CAST/MEMBER と店舗側ロールのみの利用者は不可視のため 404（在否も漏らさない）。
  @Test
  @DisplayName("GET /platform/staff/{id} は HQ 側ロール保持者を返し、CAST も店舗側ロールのみの利用者も 404 になること(AC1)")
  void getStaffByIdReturnsAdministratorAndHidesOthers() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    long staffId = platformUserRepository.findByEmail(HQ_SIDE_EMAIL).orElseThrow().getId();
    long castId = platformUserRepository.findByEmail(CAST_CANARY_EMAIL).orElseThrow().getId();
    long storeSideId = platformUserRepository.findByEmail(NON_HQ_EMAIL).orElseThrow().getId();

    ResponseEntity<JsonNode> found =
        rest.exchange(
            "/platform/staff/" + staffId,
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            JsonNode.class);
    assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(found.getBody().path("email").asString()).isEqualTo(HQ_SIDE_EMAIL);
    assertThat(found.getBody().path("version").isNumber()).isTrue();

    ResponseEntity<String> cast =
        rest.exchange(
            "/platform/staff/" + castId,
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            String.class);
    assertThat(cast.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    ResponseEntity<String> storeSide =
        rest.exchange(
            "/platform/staff/" + storeSideId,
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            String.class);
    assertThat(storeSide.getStatusCode())
        .as("店舗側ロールのみの利用者は詳細でも見つからないこと")
        .isEqualTo(HttpStatus.NOT_FOUND);

    ResponseEntity<String> forbidden =
        rest.exchange(
            "/platform/staff/" + staffId,
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(NON_HQ_EMAIL, PASSWORD))),
            String.class);
    assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  // "staff-it-h_side" は _ をワイルドカードとして扱うと staff-it-hqside に一致してしまう。
  // 検索語は字面として照合されるべきなので 0 件が正。対象は一覧に現れる側（HQ 側ロール保持者）で取る —
  // 対象絞りで元から消える行を使うと、エスケープが壊れても 0 件のままで赤くならない。
  @Test
  @DisplayName("検索語中の LIKE メタ文字は字面として扱われ、ワイルドカードにならないこと")
  void staffSearchTreatsLikeMetacharactersAsLiterals() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    assertThat(staffIds(hq, "?search=staff-it-hqside&size=100"))
        .as("前提: 字面どおりの検索語では対象が 1 件見つかること")
        .isNotEmpty();

    ResponseEntity<JsonNode> underscore =
        rest.exchange(
            "/platform/staff?search=staff-it-h_side",
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            JsonNode.class);
    assertThat(underscore.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(underscore.getBody().path("total_elements").asLong()).isZero();

    ResponseEntity<JsonNode> percent =
        rest.exchange(
            "/platform/staff?search=staff-it-%25hqside",
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
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    String token = platformToken(email, NEW_ACCOUNT_PASSWORD);

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
                createBody(email, rolesJson(HQ_SIDE_ROLE), "ALL_STORES", "[]"), bearerJson(hq)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    long staffId = created.getBody().path("id").asLong();
    long version = created.getBody().path("version").asLong();

    // 停止（enabled=false）。授権内容は同値のまま。
    ResponseEntity<JsonNode> stopped =
        rest.exchange(
            "/platform/staff/" + staffId,
            HttpMethod.PUT,
            new HttpEntity<>(
                "{\"role_ids\":"
                    + rolesJson(HQ_SIDE_ROLE)
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
                String.format(
                    "{\"email\": \"%s\", \"password\": \"%s\"}", email, NEW_ACCOUNT_PASSWORD),
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
                createBody(email, rolesJson(HQ_SIDE_ROLE), "SPECIFIC_STORES", "[" + storeAId + "]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    long staffId = created.getBody().path("id").asLong();
    long initialVersion = created.getBody().path("version").asLong();

    // 1 発目: 返却された version での更新は成功し、応答は増加した version を返す。
    ResponseEntity<JsonNode> first =
        rest.exchange(
            "/platform/staff/" + staffId,
            HttpMethod.PUT,
            new HttpEntity<>(
                updateBody(
                    rolesJson(HQ_SIDE_ROLE),
                    "SPECIFIC_STORES",
                    "[" + storeBId + "]",
                    initialVersion),
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
                    + rolesJson(HQ_SIDE_ROLE)
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
                createBody(email, rolesJson(HQ_SIDE_ROLE), "ALL_STORES", "[]"), bearerJson(hq)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    long staffId = created.getBody().path("id").asLong();
    long preStopVersion = created.getBody().path("version").asLong();

    // 現行 version で停止する（成功）。
    ResponseEntity<JsonNode> stopped =
        rest.exchange(
            "/platform/staff/" + staffId,
            HttpMethod.PUT,
            new HttpEntity<>(
                "{\"role_ids\":"
                    + rolesJson(HQ_SIDE_ROLE)
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
                    + rolesJson(HQ_SIDE_ROLE)
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
                String.format(
                    "{\"email\": \"%s\", \"password\": \"%s\"}", email, NEW_ACCOUNT_PASSWORD),
                jsonHeaders()),
            JsonNode.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("ロール一覧は ROLE_MANAGE 保持者に既定 3 ロールを返し、非保持者には 403")
  void roleListingRequiresRoleManage() {
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
  @DisplayName("STAFF_MANAGE のみ保持の利用者はロール定義（roles・permissions）へ 403(AC1)")
  void staffManageAloneCannotReachRoleDefinition() {
    String delegated = platformToken(STAFF_MANAGE_ONLY_EMAIL, PASSWORD);

    ResponseEntity<String> roles =
        rest.exchange(
            "/platform/roles", HttpMethod.GET, new HttpEntity<>(bearer(delegated)), String.class);
    assertThat(roles.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<String> permissions =
        rest.exchange(
            "/platform/permissions",
            HttpMethod.GET,
            new HttpEntity<>(bearer(delegated)),
            String.class);
    assertThat(permissions.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("権限目録は ROLE_MANAGE 保持者に 20 件の code+console を返すこと")
  void permissionCatalogIsExposedToRoleManage() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/permissions", HttpMethod.GET, new HttpEntity<>(bearer(hq)), JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody()).hasSize(20);
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
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
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
                    "staff-it-customrole@kizuna.test",
                    "[" + roleId + "," + hqSideRoleId() + "]",
                    "ALL_STORES",
                    "[]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(staff.getStatusCode()).isEqualTo(HttpStatus.CREATED);

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
    assertThat(unused.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    ResponseEntity<String> deleted =
        rest.exchange(
            "/platform/roles/" + unused.getBody().path("id").asLong(),
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(hq)),
            String.class);
    assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  @DisplayName("STAFF_MANAGE のみ保持の利用者は管理者管理（一覧・詳細・作成・更新）へ 403(AC3)")
  void staffManageAloneCannotReachAdministratorManagement() {
    String delegated = platformToken(STAFF_MANAGE_ONLY_EMAIL, PASSWORD);
    long targetId = platformUserRepository.findByEmail(HQ_SIDE_EMAIL).orElseThrow().getId();

    assertThat(
            rest.exchange(
                    "/platform/staff",
                    HttpMethod.GET,
                    new HttpEntity<>(bearer(delegated)),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            rest.exchange(
                    "/platform/staff/" + targetId,
                    HttpMethod.GET,
                    new HttpEntity<>(bearer(delegated)),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            rest.postForEntity(
                    "/platform/staff",
                    new HttpEntity<>(
                        createBody(
                            "staff-it-delegated@kizuna.test",
                            rolesJson(HQ_SIDE_ROLE),
                            "ALL_STORES",
                            "[]"),
                        bearerJson(delegated)),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            rest.exchange(
                    "/platform/staff/" + targetId,
                    HttpMethod.PUT,
                    new HttpEntity<>(
                        updateBody(rolesJson(HQ_SIDE_ROLE), "ALL_STORES", "[]", 0L),
                        bearerJson(delegated)),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("店舗側ロールのみの作成・降格は 400 で拒否されること（管理者管理が扱えるのは HQ 側ロール保持者だけ）")
  void storeSideOnlyGrantIsRejected() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody("staff-it-storeonly@kizuna.test", rolesJson("店長"), "ALL_STORES", "[]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(created.getBody().path("error").asString())
        .isEqualTo("管理者にはプラットフォーム権限を含むロールを 1 つ以上付与してください");
    assertThat(platformUserRepository.findByEmail("staff-it-storeonly@kizuna.test")).isEmpty();

    long targetId = platformUserRepository.findByEmail(HQ_SIDE_EMAIL).orElseThrow().getId();
    long version = platformUserRepository.findById(targetId).orElseThrow().getVersion();
    ResponseEntity<String> demoted =
        rest.exchange(
            "/platform/staff/" + targetId,
            HttpMethod.PUT,
            new HttpEntity<>(
                updateBody(rolesJson("店長"), "ALL_STORES", "[]", version), bearerJson(hq)),
            String.class);
    assertThat(demoted.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(platformUserRepository.findById(targetId).orElseThrow().getRoleIds())
        .as("拒否された降格で授権が変わらないこと")
        .containsExactly(hqSideRoleId());
  }

  // 不減零（G5）は母集団が残る側だけをここで固定する（拒否側は PlatformStaffServiceTest — 冒頭の javadoc 参照）。
  // 実 DB を通すことに意味があるのは、母集団を押さえる問い合わせ（FOR UPDATE 付きスカラー投影）が
  // 本物の PostgreSQL で成立し、版の照合を巻き込まないことが、この経路でしか確かめられないからである。
  @Test
  @DisplayName("ROLE_MANAGE 保持者の降格は、他に有効な保持者が残っていれば 200 で通ること")
  void demotingRoleManageHolderIsAllowedWhileOthersRemain() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);
    String email = "staff-it-demote@kizuna.test";

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(email, rolesJson("HQ管理者"), "ALL_STORES", "[]"), bearerJson(hq)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    long staffId = created.getBody().path("id").asLong();
    long version = created.getBody().path("version").asLong();

    ResponseEntity<JsonNode> demoted =
        rest.exchange(
            "/platform/staff/" + staffId,
            HttpMethod.PUT,
            new HttpEntity<>(
                updateBody(rolesJson(HQ_SIDE_ROLE), "ALL_STORES", "[]", version), bearerJson(hq)),
            JsonNode.class);

    assertThat(demoted.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(platformUserRepository.findById(staffId).orElseThrow().getRoleIds())
        .containsExactly(hqSideRoleId());
  }

  // 直列化は「押さえていること」そのものが不変量で、押さえられていなくても計数は同じ答えを返す — FOR UPDATE が
  // 静かに落ちても他のどのテストも赤くならない。押さえた行が別接続から取れないことで、実際に押さえていると確かめる。
  @Test
  @DisplayName("母集団の照会は数えた行を実際に押さえること（スカラー投影でも FOR UPDATE が効くこと）")
  void holderLookupLocksTheRowsItCounts() {
    Set<Long> roleManageRoleIds =
        roleRepository.findIdsByPermissionCode(PermissionCode.ROLE_MANAGE.name());
    assertThat(roleManageRoleIds).as("前提: ROLE_MANAGE を含むロールが存在すること").isNotEmpty();

    new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              List<Long> holders =
                  platformUserRepository.lockEnabledRoleHolderIds(roleManageRoleIds);
              assertThat(holders).as("前提: 有効な ROLE_MANAGE 実効保持者が居ること").isNotEmpty();

              assertThatThrownBy(() -> lockNoWait(holders.get(0)))
                  .as("押さえた行は別接続の FOR UPDATE NOWAIT では取れないこと")
                  .isInstanceOf(SQLException.class)
                  .extracting(ex -> ((SQLException) ex).getSQLState())
                  .as("待てば取れる状態（55P03 = lock_not_available）であること")
                  .isEqualTo("55P03");

              status.setRollbackOnly();
              return null;
            });
  }

  /** 別接続で行ロックを待たずに取りに行く。既に押さえられていれば SQLState 55P03 で失敗する。 */
  private void lockNoWait(long userId) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (PreparedStatement statement =
          connection.prepareStatement("select id from t_users where id = ? for update nowait")) {
        statement.setLong(1, userId);
        statement.executeQuery();
      } finally {
        connection.rollback();
      }
    }
  }

  // 守衛の共有直列化点も「押さえていること」そのものが不変量で、押さえられていなくても計数は同じ答えを返す。
  // 服務側が目録行を押さえてから母集団を取り直す順は単体テストが固定し、ここは目録行が本当に押さえられることを見る。
  @Test
  @DisplayName("守衛の直列化点は権限目録の行を実際に押さえること")
  void guardMutexLocksThePermissionCatalogRow() {
    new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              assertThat(permissionRepository.lockIdByCode(PermissionCode.ROLE_MANAGE.name()))
                  .as("前提: ROLE_MANAGE の目録行が播種されていること")
                  .isPresent();

              assertThatThrownBy(() -> lockPermissionNoWait(PermissionCode.ROLE_MANAGE.name()))
                  .as("押さえた目録行は別接続の FOR UPDATE NOWAIT では取れないこと")
                  .isInstanceOf(SQLException.class)
                  .extracting(ex -> ((SQLException) ex).getSQLState())
                  .as("待てば取れる状態（55P03 = lock_not_available）であること")
                  .isEqualTo("55P03");

              status.setRollbackOnly();
              return null;
            });
  }

  private void lockPermissionNoWait(String code) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (PreparedStatement statement =
          connection.prepareStatement(
              "select id from t_permissions where code = ? for update nowait")) {
        statement.setString(1, code);
        statement.executeQuery();
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  @DisplayName("ROLE_MANAGE を含む自作ロールからの権限除去は、他に有効な保持者が居れば 200 で通ること(AC2)")
  void removingRoleManageFromACustomRoleIsAllowedWhileOtherHoldersRemain() {
    long roleId = ensureCustomRoleManageRole(ROLE_EDIT_ROLE);
    ensureRoleManageHolder(ROLE_EDIT_HOLDER_EMAIL, roleId);
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<JsonNode> res = replacePermissions(hq, roleId, "[\"STORE_MANAGE\"]");

    assertThat(res.getStatusCode()).as("種子の HQ管理者が母集団に残っている").isEqualTo(HttpStatus.OK);
    assertThat(roleRepository.findIdsByPermissionCode(PermissionCode.ROLE_MANAGE.name()))
        .doesNotContain(roleId);
  }

  /**
   * 母集団が「この 1 つのロールだけ」に縮んだ状態を作って拒否側を撃つ。他の保持者は資料庫から直接停止する（サービス経由だと 失効イベントで他の IT
   * のトークンまで巻き込むため）。行使者自身は編集対象のロールで認証するので、停止の対象から外す。
   */
  @Test
  @DisplayName("最後の母集団を供給するロールから ROLE_MANAGE を外す編集は 400 で拒否されること(AC1)")
  void removingRoleManageFromTheLastSupplyingRoleIsRejected() {
    long roleId = ensureCustomRoleManageRole(ROLE_EDIT_ROLE);
    long holderId = ensureRoleManageHolder(ROLE_EDIT_HOLDER_EMAIL, roleId);
    String actor = platformToken(ROLE_EDIT_HOLDER_EMAIL, PASSWORD);
    List<Long> suspended = suspendOtherRoleManageHolders(holderId);

    try {
      ResponseEntity<JsonNode> res = replacePermissions(actor, roleId, "[\"STORE_MANAGE\"]");

      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(res.getBody().path("error").asString()).contains("管理権限");
      assertThat(roleRepository.findIdsByPermissionCode(PermissionCode.ROLE_MANAGE.name()))
          .as("拒否された編集で権限構成が変わらないこと")
          .contains(roleId);
    } finally {
      resume(suspended);
    }
  }

  private ResponseEntity<JsonNode> replacePermissions(
      String token, long roleId, String permissionsJson) {
    long version = roleRepository.findById(roleId).orElseThrow().getVersion();
    return rest.exchange(
        "/platform/roles/" + roleId,
        HttpMethod.PUT,
        new HttpEntity<>(
            String.format(
                "{\"name\":\"%s\",\"permissions\":%s,\"version\":%d}",
                ROLE_EDIT_ROLE, permissionsJson, version),
            bearerJson(token)),
        JsonNode.class);
  }

  /** ROLE_MANAGE を含む自作ロール。既定ロールは改廃自体が拒否されるため、この穴は自作ロールでしか踏めない。 */
  private long ensureCustomRoleManageRole(String name) {
    Role role =
        roleRepository
            .findByName(name)
            .orElseGet(
                () ->
                    roleRepository.save(
                        Role.builder()
                            .name(name)
                            .permissionIds(permissionIdsOf(PermissionCode.ROLE_MANAGE))
                            .build()));
    role.replacePermissions(permissionIdsOf(PermissionCode.ROLE_MANAGE));
    return roleRepository.saveAndFlush(role).getId();
  }

  /** 指定した id 以外の有効な ROLE_MANAGE 実効保持者を資料庫から直接停止し、戻した相手を返す。 */
  private List<Long> suspendOtherRoleManageHolders(long keepEnabledId) {
    List<Long> others =
        platformUserRepository
            .findEnabledRoleHolderIds(
                roleRepository.findIdsByPermissionCode(PermissionCode.ROLE_MANAGE.name()))
            .stream()
            .filter(id -> id.longValue() != keepEnabledId)
            .toList();
    others.forEach(
        id -> {
          PlatformUser user = platformUserRepository.findById(id).orElseThrow();
          user.stop();
          platformUserRepository.saveAndFlush(user);
        });
    return others;
  }

  private void resume(List<Long> userIds) {
    userIds.forEach(
        id -> {
          PlatformUser user = platformUserRepository.findById(id).orElseThrow();
          user.resume();
          platformUserRepository.saveAndFlush(user);
        });
  }

  /**
   * 待たされた {@code FOR UPDATE} が返す集合は、待つ前のスナップショットのままかどうかの実測。
   *
   * <p>PostgreSQL は READ COMMITTED で、押さえた行が更新されていれば WHERE を取り直す。しかし「ROLE_MANAGE を持つか」は t_user_roles
   * 側の述語で、取り直しは他の表を元のスナップショットで読む。降格が確定していても保持者に見え続けるなら、 押さえた結果をそのまま数えてはいけない（数え直しが要る）。
   */
  @Test
  @DisplayName("待たされた FOR UPDATE の結果は待つ間に確定した降格を見ず、数え直しは見ること")
  void lockedLookupIsStaleSoTheCountMustBeTakenAgain() throws Exception {
    long roleManageRoleId = itRoleManageRoleId();
    long demotedId = ensureRoleManageHolder(RACE_EMAIL, roleManageRoleId);
    Set<Long> roleManageRoleIds =
        roleRepository.findIdsByPermissionCode(PermissionCode.ROLE_MANAGE.name());
    assertThat(roleManageRoleIds).contains(roleManageRoleId);

    CountDownLatch locksHeld = new CountDownLatch(1);
    AtomicReference<List<Long>> locked = new AtomicReference<>();
    AtomicReference<List<Long>> recounted = new AtomicReference<>();

    Thread waiter =
        new Thread(
            () ->
                new TransactionTemplate(transactionManager)
                    .execute(
                        status -> {
                          awaitQuietly(locksHeld);
                          locked.set(
                              platformUserRepository.lockEnabledRoleHolderIds(roleManageRoleIds));
                          recounted.set(
                              platformUserRepository.findEnabledRoleHolderIds(roleManageRoleIds));
                          status.setRollbackOnly();
                          return null;
                        }));
    waiter.start();

    new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              platformUserRepository.lockEnabledRoleHolderIds(roleManageRoleIds);
              locksHeld.countDown();
              awaitLockWait();
              PlatformUser demoted = platformUserRepository.findById(demotedId).orElseThrow();
              demoted.reassignGrants(Set.of(hqSideRoleId()), StoreScopeType.ALL_STORES, Set.of());
              platformUserRepository.saveAndFlush(demoted);
              return null;
            });

    waiter.join(TimeUnit.SECONDS.toMillis(30));
    assertThat(locked.get()).as("前提: 待たされた側もいずれ結果を返すこと").isNotNull();
    assertThat(locked.get()).as("押さえる問い合わせ自身は待つ前のスナップショットのままで、確定した降格を見ないこと").contains(demotedId);
    assertThat(recounted.get()).as("押さえた後の数え直しは確定した降格を見ること（守衛はこちらを数える）").doesNotContain(demotedId);
  }

  /** ROLE_MANAGE を含む IT 専用ロール。種子の HQ管理者に触らずに母集団を足すために使う。 */
  private long itRoleManageRoleId() {
    return roleRepository
        .findByName(ROLE_MANAGE_ROLE)
        .orElseGet(
            () ->
                roleRepository.save(
                    Role.builder()
                        .name(ROLE_MANAGE_ROLE)
                        .permissionIds(permissionIdsOf(PermissionCode.ROLE_MANAGE))
                        .build()))
        .getId();
  }

  /** 指定ロールだけを持つ有効な保持者を用意する（前回実行で降格済みでも組み直す）。 */
  private long ensureRoleManageHolder(String email, long roleId) {
    ensurePlatformUser(email, UserType.STAFF, Set.of(roleId), StoreScopeType.ALL_STORES, Set.of());
    PlatformUser holder = platformUserRepository.findByEmail(email).orElseThrow();
    holder.reassignGrants(Set.of(roleId), StoreScopeType.ALL_STORES, Set.of());
    return platformUserRepository.saveAndFlush(holder).getId();
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      assertThat(latch.await(30, TimeUnit.SECONDS)).as("前提: 相手がロックを握るまで待てること").isTrue();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new AssertionError(ex);
    }
  }

  /** 相手が本当に行ロック待ちへ入るまで待つ（待ちに入る前に確定すると、この実測は競合を踏まない）。 */
  private void awaitLockWait() {
    for (int attempt = 0; attempt < 300; attempt++) {
      if (countLockWaiters() > 0) {
        return;
      }
      try {
        TimeUnit.MILLISECONDS.sleep(100);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AssertionError(ex);
      }
    }
    throw new AssertionError("前提: 相手が行ロック待ちへ入ること");
  }

  private int countLockWaiters() {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "select count(*) from pg_stat_activity"
                    + " where wait_event_type = 'Lock' and datname = current_database()");
        ResultSet resultSet = statement.executeQuery()) {
      resultSet.next();
      return resultSet.getInt(1);
    } catch (SQLException ex) {
      throw new AssertionError(ex);
    }
  }
}
