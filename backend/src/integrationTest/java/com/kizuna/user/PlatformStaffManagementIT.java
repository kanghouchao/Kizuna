package com.kizuna.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.kizuna.shared.CrossTenantTestSupport;
import com.kizuna.tenant.domain.Tenant;
import com.kizuna.tenant.domain.TenantRepository;
import com.kizuna.user.domain.PlatformRole;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
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
 * スタッフ・権限管理（#325）の HTTP 境界統合テスト。HQ 限定の授権書き込みと、付与した店舗集合が本人の次回ログインのデータ範囲に反映されること、
 * 授権外店舗の実データが応答生ボディに一切現れないこと（強断言）を本物の PostgreSQL で固定する。ヘルパは {@link
 * com.kizuna.order.PlatformOrderScopeIT} の {@code ensurePlatformUser}/{@code platformToken}
 * 様式を踏襲し、強断言様式は {@link com.kizuna.menu.MenuCrossTenantIT} に由来する。
 */
class PlatformStaffManagementIT extends CrossTenantTestSupport {

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

  @Autowired private TenantRepository tenantRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private long storeAId;
  private long storeBId;

  @BeforeEach
  void prepareStaffFixture() {
    storeAId = ensureTenant(STORE_A_DOMAIN, STORE_A_NAME);
    storeBId = ensureTenant(STORE_B_DOMAIN, STORE_B_NAME);
    ensurePlatformUser(
        NON_HQ_EMAIL, PlatformRole.STORE_MANAGER, StoreScopeType.ALL_STORES, Set.of());
    ensurePlatformUser(CAST_CANARY_EMAIL, PlatformRole.CAST, StoreScopeType.ALL_STORES, Set.of());
  }

  private long ensureTenant(String domain, String name) {
    return tenantRepository
        .findByDomain(domain)
        .orElseGet(() -> tenantRepository.save(new Tenant(name, domain, null)))
        .getId();
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
                        .displayName("スタッフ管理IT " + role.name())
                        .enabled(true)
                        .role(role)
                        .storeScopeType(scopeType)
                        .storeIds(storeIds)
                        .build()));
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

  private static String createBody(String email, String role, String scopeType, String storeIds) {
    return String.format(
        "{\"email\":\"%s\",\"password\":\"%s\",\"display_name\":\"IT表示名\",\"role\":\"%s\","
            + "\"store_scope_type\":\"%s\",\"store_ids\":%s}",
        email, PASSWORD, role, scopeType, storeIds);
  }

  private static String updateBody(String role, String scopeType, String storeIds) {
    return String.format(
        "{\"role\":\"%s\",\"store_scope_type\":\"%s\",\"store_ids\":%s}",
        role, scopeType, storeIds);
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
  @DisplayName("HQ 作成の STORE_MANAGER が新規メールでログインでき、授権店舗(A)のみ見え店舗(B)の実データが漏れないこと(AC2/AC3)")
  void hqCreatesStaffAndNewStaffCanLoginWithGrantedScope() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/platform/staff",
            new HttpEntity<>(
                createBody(CASE1_EMAIL, "STORE_MANAGER", "SPECIFIC_STORES", "[" + storeAId + "]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(created.getBody().path("id").asLong()).isPositive();

    ResponseEntity<String> stores =
        rest.exchange(
            "/platform/stores",
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
  @DisplayName("非 HQ ロールでは GET/POST /platform/staff が 403(AC4)")
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
                createBody("staff-it-forbidden@kizuna.test", "STORE_STAFF", "ALL_STORES", "[]"),
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
                createBody(CASE3_EMAIL, "STORE_MANAGER", "SPECIFIC_STORES", "[" + storeAId + "]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
    long staffId = created.getBody().path("id").asLong();

    ResponseEntity<String> before =
        rest.exchange(
            "/platform/stores",
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
                updateBody("STORE_MANAGER", "SPECIFIC_STORES", "[" + storeBId + "]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> after =
        rest.exchange(
            "/platform/stores",
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
    String body = createBody(DUP_EMAIL, "STORE_STAFF", "ALL_STORES", "[]");

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
  @DisplayName("非スタッフロール(CAST/MEMBER)の作成は 400 で拒否")
  void nonStaffRoleRejected() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    for (String role : List.of("CAST", "MEMBER")) {
      ResponseEntity<JsonNode> res =
          rest.postForEntity(
              "/platform/staff",
              new HttpEntity<>(
                  createBody("staff-it-reject-" + role + "@kizuna.test", role, "ALL_STORES", "[]"),
                  bearerJson(hq)),
              JsonNode.class);
      assertThat(res.getStatusCode())
          .as("role=%s は 400 で拒否されること", role)
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
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
                    "STORE_MANAGER",
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
                    "STORE_MANAGER",
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
                    "STORE_MANAGER",
                    "SPECIFIC_STORES",
                    "[999999]"),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("スタッフ一覧に CAST が現れず、スタッフロールは現れること(強断言)")
  void staffListExcludesCastAndMember() {
    String hq = platformToken(SEED_EMAIL, PASSWORD);

    ResponseEntity<String> res =
        rest.exchange(
            "/platform/staff", HttpMethod.GET, new HttpEntity<>(bearer(hq)), String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody())
        .as("スタッフロール(STORE_MANAGER)は現れ、CAST は一覧の生ボディに一切現れないこと")
        .contains(NON_HQ_EMAIL)
        .doesNotContain(CAST_CANARY_EMAIL);
  }
}
