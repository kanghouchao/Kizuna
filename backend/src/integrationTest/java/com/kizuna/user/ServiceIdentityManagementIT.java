package com.kizuna.user;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
 * サービスID管理（{@code /platform/service-identities}）の HTTP 境界統合テスト。SERVICE_ID_MANAGE 門、自作ロール限定の
 * 授与制約（平台既定ロールの拒絶）、停止・再開の往復、STAFF の面との相互不可視を本物の PostgreSQL で固定する。様式は {@link
 * PlatformStaffAccountManagementIT} に倣う。
 *
 * <p>一覧の断言は必ずカナリア接頭辞（search 絞り込み）を通す。母集団は DB 全体のサービスIDなので、頁の先頭に何が 載るかは並行して走る他の IT の作る行に左右される。
 */
class ServiceIdentityManagementIT extends CrossStoreTestSupport {

  /** ALL_STORES の HQ 管理者シード（既定授与で SERVICE_ID_MANAGE を持つ）。 */
  private static final String SEED_EMAIL = "admin@kizuna.test";

  /** 一覧断言のカナリア接頭辞。URL の search に載せるため ASCII に限る（URI テンプレートへ非 ASCII を持ち込まない取り決め）。 */
  private static final String CANARY = "svc-mgmt-it-";

  private static final String STORE_DOMAIN = "svc-mgmt-it-store.kizuna.test";
  private static final String STORE_NAME = "サービスID管理IT_店舗";

  /** SERVICE_ID_MANAGE を持たない利用者（門の負側）。 */
  private static final String NON_HOLDER_EMAIL = "svc-mgmt-it-nonholder@kizuna.test";

  /** 授与に使う IT 専用の自作ロール。 */
  private static final String CUSTOM_ROLE = "サービスID管理IT_バッチ実行";

  private static final String NON_HOLDER_ROLE = "サービスID管理IT_門の外";

  @Autowired private StoreRepository storeRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PermissionRepository permissionRepository;

  private long storeId;
  private long customRoleId;

  @BeforeEach
  void prepareServiceIdentityFixture() {
    storeId =
        storeRepository
            .findByDomain(STORE_DOMAIN)
            .orElseGet(() -> storeRepository.save(new Store(STORE_NAME, STORE_DOMAIN, null)))
            .getId();
    customRoleId = customRoleId(CUSTOM_ROLE, PermissionCode.STORE_VIEW);
    ensureStaff(
        NON_HOLDER_EMAIL,
        CANARY + "outsider-staff",
        Set.of(customRoleId(NON_HOLDER_ROLE, PermissionCode.STORE_MANAGE)));
  }

  private long customRoleId(String roleName, PermissionCode... codes) {
    Set<String> names = Arrays.stream(codes).map(PermissionCode::name).collect(Collectors.toSet());
    Set<Long> permissionIds =
        permissionRepository.findByCodeIn(names).stream()
            .map(Permission::getId)
            .collect(Collectors.toSet());
    return roleRepository
        .findByName(roleName)
        .orElseGet(
            () ->
                roleRepository.save(
                    Role.builder().name(roleName).permissionIds(permissionIds).build()))
        .getId();
  }

  private void ensureStaff(String email, String displayName, Set<Long> roleIds) {
    platformUserRepository
        .findByEmail(email)
        .orElseGet(
            () ->
                platformUserRepository.save(
                    PlatformUser.builder()
                        .email(email)
                        .password(passwordEncoder.encode("pass"))
                        .displayName(displayName)
                        .enabled(true)
                        .userType(UserType.STAFF)
                        .roleIds(roleIds)
                        .storeScopeType(StoreScopeType.ALL_STORES)
                        .storeIds(Set.of())
                        .build()));
  }

  private long systemRoleId() {
    return roleRepository.findByName("HQ管理者").orElseThrow().getId();
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

  private ResponseEntity<JsonNode> create(String token, String displayName, Set<Long> roleIds) {
    Map<String, Object> body =
        Map.of(
            "display_name",
            displayName,
            "role_ids",
            roleIds,
            "store_scope_type",
            "SPECIFIC_STORES",
            "store_ids",
            Set.of(storeId));
    return rest.postForEntity(
        "/platform/service-identities", new HttpEntity<>(body, bearerJson(token)), JsonNode.class);
  }

  private ResponseEntity<JsonNode> listWith(String token, String query) {
    return rest.exchange(
        "/platform/service-identities" + query,
        HttpMethod.GET,
        new HttpEntity<>(bearer(token)),
        JsonNode.class);
  }

  private ResponseEntity<String> action(String token, long id, String action) {
    return rest.exchange(
        "/platform/service-identities/" + id + "/" + action,
        HttpMethod.POST,
        new HttpEntity<>(bearer(token)),
        String.class);
  }

  private static List<String> displayNamesOf(JsonNode body) {
    List<String> names = new ArrayList<>();
    body.path("content").forEach(row -> names.add(row.path("display_name").asString("")));
    return names;
  }

  @Test
  @DisplayName("SERVICE_ID_MANAGE を持たない利用者は全ての端点で 403 になり、既定授与の HQ 管理者は通ること(門)")
  void faceIsGatedByServiceIdManage() {
    String outsider = login(NON_HOLDER_EMAIL);

    assertThat(listWith(outsider, "").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            rest.exchange(
                    "/platform/service-identities/grantable-roles",
                    HttpMethod.GET,
                    new HttpEntity<>(bearer(outsider)),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            rest.exchange(
                    "/platform/service-identities/1",
                    HttpMethod.GET,
                    new HttpEntity<>(bearer(outsider)),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(create(outsider, CANARY + "outsider-create", Set.of(customRoleId)).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            rest.exchange(
                    "/platform/service-identities/1",
                    HttpMethod.PUT,
                    new HttpEntity<>(
                        Map.of(
                            "role_ids",
                            Set.of(customRoleId),
                            "store_scope_type",
                            "ALL_STORES",
                            "version",
                            0L),
                        bearerJson(outsider)),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(action(outsider, 1L, "suspension").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(action(outsider, 1L, "resumption").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // 既定授与（HQ_ADMIN → SERVICE_ID_MANAGE）が播種されていることの正側。
    String hq = login(SEED_EMAIL);
    assertThat(listWith(hq, "").getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("自作ロール×指定店舗集合で作成でき、対象範囲が授権行（ロール×店舗集合）で表現されること")
  void createExpressesScopeAsGrantRow() {
    String hq = login(SEED_EMAIL);
    String name = CANARY + "create";

    ResponseEntity<JsonNode> created = create(hq, name, Set.of(customRoleId));

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode body = created.getBody();
    assertThat(body.path("display_name").asString()).isEqualTo(name);
    assertThat(body.path("enabled").asBoolean()).isTrue();
    assertThat(body.path("roles").get(0).path("id").asLong()).isEqualTo(customRoleId);
    assertThat(body.path("roles").get(0).path("name").asString()).isEqualTo(CUSTOM_ROLE);
    assertThat(body.path("store_scope_type").asString()).isEqualTo("SPECIFIC_STORES");
    assertThat(body.path("store_ids").get(0).asLong()).isEqualTo(storeId);

    // 永続行は資格情報を持たない本人種別 SERVICE。
    PlatformUser saved = platformUserRepository.findById(body.path("id").asLong()).orElseThrow();
    assertThat(saved.getUserType()).isEqualTo(UserType.SERVICE);
    assertThat(saved.getEmail()).isNull();
    assertThat(saved.getPassword()).isNull();

    // 一覧（カナリア絞り込み）にも授権行が現れる。
    ResponseEntity<JsonNode> listed = listWith(hq, "?search=" + name);
    assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(displayNamesOf(listed.getBody())).contains(name);
  }

  @Test
  @DisplayName("平台既定ロールを載せた作成・授権変更は 400 で拒絶されること")
  void systemRoleGrantIsRejected() {
    String hq = login(SEED_EMAIL);

    ResponseEntity<JsonNode> rejectedCreate =
        create(hq, CANARY + "system-role-create", Set.of(systemRoleId()));
    assertThat(rejectedCreate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    ResponseEntity<JsonNode> created = create(hq, CANARY + "grant-target", Set.of(customRoleId));
    long id = created.getBody().path("id").asLong();
    long version = created.getBody().path("version").asLong();
    ResponseEntity<String> rejectedUpdate =
        rest.exchange(
            "/platform/service-identities/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(
                Map.of(
                    "role_ids",
                    Set.of(systemRoleId()),
                    "store_scope_type",
                    "ALL_STORES",
                    "version",
                    version),
                bearerJson(hq)),
            String.class);
    assertThat(rejectedUpdate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // null 要素は @ElementCollection の永続化で黙って捨てられ、非空検証を通ったのに
  // 店舗ゼロの行が残る抜け道になるため、要素単位の検証で入口から拒む。
  @Test
  @DisplayName("store_ids の null 要素を含む作成・授権変更は 400 で拒絶されること")
  void nullStoreIdElementIsRejected() {
    String hq = login(SEED_EMAIL);

    Map<String, Object> createBody = new HashMap<>();
    createBody.put("display_name", CANARY + "null-store");
    createBody.put("role_ids", Set.of(customRoleId));
    createBody.put("store_scope_type", "SPECIFIC_STORES");
    createBody.put("store_ids", Collections.singletonList(null));
    ResponseEntity<String> rejectedCreate =
        rest.postForEntity(
            "/platform/service-identities",
            new HttpEntity<>(createBody, bearerJson(hq)),
            String.class);
    assertThat(rejectedCreate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    long id =
        create(hq, CANARY + "null-store-update", Set.of(customRoleId))
            .getBody()
            .path("id")
            .asLong();
    Map<String, Object> updateBody = new HashMap<>();
    updateBody.put("role_ids", Set.of(customRoleId));
    updateBody.put("store_scope_type", "SPECIFIC_STORES");
    updateBody.put("store_ids", Collections.singletonList(null));
    updateBody.put("version", 0L);
    ResponseEntity<String> rejectedUpdate =
        rest.exchange(
            "/platform/service-identities/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(updateBody, bearerJson(hq)),
            String.class);
    assertThat(rejectedUpdate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("授権変更はロール×店舗集合を差し替え、陳腐化した version の再提出は 409 になること")
  void updateReassignsGrantsAndRejectsStaleVersion() {
    String hq = login(SEED_EMAIL);
    ResponseEntity<JsonNode> created = create(hq, CANARY + "grant-change", Set.of(customRoleId));
    long id = created.getBody().path("id").asLong();
    long version = created.getBody().path("version").asLong();

    ResponseEntity<JsonNode> updated =
        rest.exchange(
            "/platform/service-identities/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(
                Map.of(
                    "role_ids",
                    Set.of(customRoleId),
                    "store_scope_type",
                    "ALL_STORES",
                    "store_ids",
                    Set.of(),
                    "version",
                    version),
                bearerJson(hq)),
            JsonNode.class);
    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updated.getBody().path("store_scope_type").asString()).isEqualTo("ALL_STORES");

    // 版が進んだ後に元の版で再提出する（陳腐化した編集フォーム）。
    ResponseEntity<String> stale =
        rest.exchange(
            "/platform/service-identities/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(
                Map.of(
                    "role_ids",
                    Set.of(customRoleId),
                    "store_scope_type",
                    "SPECIFIC_STORES",
                    "store_ids",
                    Set.of(storeId),
                    "version",
                    version),
                bearerJson(hq)),
            String.class);
    assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  @DisplayName("停止・再開が enabled の往復として動き、再送は冪等に 204 で受理されること")
  void suspensionAndResumptionRoundTrip() {
    String hq = login(SEED_EMAIL);
    long id = create(hq, CANARY + "toggle", Set.of(customRoleId)).getBody().path("id").asLong();

    assertThat(action(hq, id, "suspension").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(platformUserRepository.findById(id).orElseThrow().getEnabled()).isFalse();
    assertThat(action(hq, id, "suspension").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(action(hq, id, "resumption").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(platformUserRepository.findById(id).orElseThrow().getEnabled()).isTrue();
    assertThat(action(hq, id, "resumption").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  @DisplayName("サービスIDはスタッフの面に現れず、スタッフはサービスIDの面に現れないこと(相互不可視)")
  void surfacesAreMutuallyInvisible() {
    String hq = login(SEED_EMAIL);
    String name = CANARY + "invisible";
    long serviceId = create(hq, name, Set.of(customRoleId)).getBody().path("id").asLong();
    long staffId = platformUserRepository.findByEmail(NON_HOLDER_EMAIL).orElseThrow().getId();

    // アカウント管理・管理者管理の一覧はカナリアで検索してもサービスIDを返さない。
    ResponseEntity<JsonNode> accounts =
        rest.exchange(
            "/platform/staff-accounts?search=" + name,
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            JsonNode.class);
    assertThat(accounts.getBody().path("content")).isEmpty();
    ResponseEntity<JsonNode> staff =
        rest.exchange(
            "/platform/staff?search=" + name,
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            JsonNode.class);
    assertThat(staff.getBody().path("content")).isEmpty();

    // 直指ししてもスタッフの面からサービスIDへは届かない（逆も同じ）。
    assertThat(
            rest.exchange(
                    "/platform/staff-accounts/" + serviceId,
                    HttpMethod.GET,
                    new HttpEntity<>(bearer(hq)),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            rest.exchange(
                    "/platform/service-identities/" + staffId,
                    HttpMethod.GET,
                    new HttpEntity<>(bearer(hq)),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);

    // サービスIDの一覧にスタッフは現れない。
    ResponseEntity<JsonNode> identities = listWith(hq, "?search=" + CANARY + "outsider-staff");
    assertThat(identities.getBody().path("content")).isEmpty();
  }

  @Test
  @DisplayName("付与可能ロールの読み口は自作ロールを返し、平台既定ロールを含まないこと")
  void grantableRolesExcludeSystemRoles() {
    String hq = login(SEED_EMAIL);

    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/service-identities/grantable-roles",
            HttpMethod.GET,
            new HttpEntity<>(bearer(hq)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> names = new ArrayList<>();
    res.getBody().forEach(row -> names.add(row.path("name").asString("")));
    assertThat(names).contains(CUSTOM_ROLE);
    assertThat(names).doesNotContain("HQ管理者", "店長", "店舗スタッフ");
  }
}
