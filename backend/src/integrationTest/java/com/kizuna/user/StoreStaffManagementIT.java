package com.kizuna.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.user.domain.Permission;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.Arrays;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

/**
 * 店舗スタッフ管理の HTTP 境界統合テスト。防提権守衛（ADR 0020 の G1〜G3）が実際の JWT・店舗文脈ヘッダ・本物の PostgreSQL を通しても成立すること、 および HQ
 * 側ロール保持者が店長の一覧に在否ごと現れないこと（強断言）を固定する。
 *
 * <p>述語そのものの撃ち分けは {@code StoreStaffServiceTest} が持つ。ここが担うのは、その判定が行使者の JWT から正しく組み上がること（authorities
 * と授権店舗集合）と、応答の生ボディに漏れが無いことである。
 */
class StoreStaffManagementIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "pass";

  /** 新規作成の要求が通る合言葉。要求側の最小 8 文字を満たす必要があり、種子の合言葉は使えない。 */
  private static final String NEW_ACCOUNT_PASSWORD = "pass1234";

  /** 店長役（委譲層のみ・店舗A担当）。この面の主たる行使者。 */
  private static final String MANAGER_EMAIL = "store-staff-it-manager@kizuna.test";

  private static final String MANAGER_ROLE = "店舗スタッフ管理IT_委譲層";

  /** 委譲権限を含まない店舗側ロール。店長が付与できる唯一の種類。 */
  private static final String CLERK_ROLE = "店舗スタッフ管理IT_平スタッフ";

  /** HQ 側ロール（Console.PLATFORM の権限を含む）。この面には在否すら出てはならない。 */
  private static final String HQ_SIDE_ROLE = "店舗スタッフ管理IT_HQ側";

  /** メニューの標識権限しか持たない自作ロール。単独では店舗コンソールへ着地できない。 */
  private static final String MENU_ONLY_ROLE = "店舗スタッフ管理IT_標識のみ";

  /** SHARED の跨店参照権限しか持たない自作ロール。これも単独では店舗コンソールへ着地できない。 */
  private static final String SHARED_ONLY_ROLE = "店舗スタッフ管理IT_跨店参照のみ";

  /** 店舗A のみ担当の平スタッフ。店長から編集できる側の代表。 */
  private static final String CLERK_EMAIL = "store-staff-it-clerk@kizuna.test";

  /** 店舗A・B を担当する平スタッフ。店長の担当範囲を越えるため編集できない側。 */
  private static final String CROSS_EMAIL = "store-staff-it-cross@kizuna.test";

  /** 同店で委譲権限を実効保持する同僚。見えるが編集・停止はできない（G3）。 */
  private static final String PEER_EMAIL = "store-staff-it-peer@kizuna.test";

  /** HQ 側ロール保持者。表示名とメールをそのまま漏洩検知のカナリアに使う。 */
  private static final String HQ_CANARY_EMAIL = "store-staff-it-hq-canary@kizuna.test";

  private static final String HQ_CANARY_NAME = "店舗スタッフ管理IT_HQ側機密";

  /** 店舗B だけを担当する平スタッフ。店舗A の店長からは一覧にも詳細にも出てはならない。 */
  private static final String OTHER_STORE_EMAIL = "store-staff-it-other-store@kizuna.test";

  private static final String OTHER_STORE_NAME = "店舗スタッフ管理IT_他店機密";

  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PermissionRepository permissionRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void prepareStoreStaffFixture() {
    ensureRole(MANAGER_ROLE, PermissionCode.STAFF_MANAGE, PermissionCode.STORE_MENU_VIEW);
    ensureRole(CLERK_ROLE, PermissionCode.ORDER_MANAGE, PermissionCode.STORE_MENU_VIEW);
    ensureRole(HQ_SIDE_ROLE, PermissionCode.STORE_MANAGE, PermissionCode.STORE_VIEW);
    ensureRole(MENU_ONLY_ROLE, PermissionCode.STORE_MENU_VIEW);
    ensureRole(SHARED_ONLY_ROLE, PermissionCode.STORE_VIEW);

    ensureUser(MANAGER_EMAIL, "店舗スタッフ管理IT店長", roleIdsOf(MANAGER_ROLE), Set.of(STORE_A));
    ensureUser(CLERK_EMAIL, "店舗スタッフ管理IT平スタッフ", roleIdsOf(CLERK_ROLE), Set.of(STORE_A));
    ensureUser(CROSS_EMAIL, "店舗スタッフ管理IT跨店", roleIdsOf(CLERK_ROLE), Set.of(STORE_A, STORE_B));
    ensureUser(PEER_EMAIL, "店舗スタッフ管理IT同僚店長", roleIdsOf(MANAGER_ROLE), Set.of(STORE_A));
    ensureUser(HQ_CANARY_EMAIL, HQ_CANARY_NAME, roleIdsOf(HQ_SIDE_ROLE), Set.of(STORE_A));
    ensureUser(OTHER_STORE_EMAIL, OTHER_STORE_NAME, roleIdsOf(CLERK_ROLE), Set.of(STORE_B));
  }

  @Test
  @DisplayName("店長の一覧は店舗側ロールのみの同店スタッフを返し、HQ 側ロール保持者は生ボディに一切現れないこと（AC4・強断言）")
  void managerListHidesHqSideRoleHolders() {
    ResponseEntity<String> res =
        rest.exchange(
            "/store/staff-members?size=100",
            HttpMethod.GET,
            new HttpEntity<>(headersFor(MANAGER_EMAIL, STORE_A)),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody()).as("同店の店舗側スタッフは見えること").contains(CLERK_EMAIL, CROSS_EMAIL, PEER_EMAIL);
    assertThat(res.getBody())
        .as("HQ 側ロール保持者は表示名もメールも現れないこと（在否の列挙防止）")
        .doesNotContain(HQ_CANARY_EMAIL, HQ_CANARY_NAME);
  }

  @Test
  @DisplayName("店長の一覧で、委譲権限の実効保持者と担当外店舗を含む者は editable=false になること（G3）")
  void managerListMarksOutOfScopeRowsAsReadOnly() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/staff-members?size=100",
            HttpMethod.GET,
            new HttpEntity<>(headersFor(MANAGER_EMAIL, STORE_A)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(editableOf(res.getBody(), CLERK_EMAIL)).as("同店・担当内の平スタッフ").isTrue();
    assertThat(editableOf(res.getBody(), CROSS_EMAIL)).as("担当外店舗を含むスタッフ").isFalse();
    assertThat(editableOf(res.getBody(), PEER_EMAIL)).as("委譲権限を実効保持する同僚").isFalse();
  }

  @Test
  @DisplayName("HQ 側ロール保持者の詳細は店長には 404 になること（在否も漏らさない）")
  void managerCannotSeeHqSideRoleHolderDetail() {
    long canaryId = platformUserRepository.findByEmail(HQ_CANARY_EMAIL).orElseThrow().getId();

    ResponseEntity<String> res =
        rest.exchange(
            "/store/staff-members/" + canaryId,
            HttpMethod.GET,
            new HttpEntity<>(headersFor(MANAGER_EMAIL, STORE_A)),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(res.getBody()).doesNotContain(HQ_CANARY_NAME);
  }

  @Test
  @DisplayName("他店だけを担当するスタッフは一覧にも詳細にも出ないこと（id 直指しでも一覧と同じ可視性）")
  void managerCannotReachStaffOfAnotherStore() {
    long targetId = platformUserRepository.findByEmail(OTHER_STORE_EMAIL).orElseThrow().getId();

    ResponseEntity<String> list =
        rest.exchange(
            "/store/staff-members?size=100",
            HttpMethod.GET,
            new HttpEntity<>(headersFor(MANAGER_EMAIL, STORE_A)),
            String.class);
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(list.getBody()).doesNotContain(OTHER_STORE_EMAIL, OTHER_STORE_NAME);

    ResponseEntity<String> detail =
        rest.exchange(
            "/store/staff-members/" + targetId,
            HttpMethod.GET,
            new HttpEntity<>(headersFor(MANAGER_EMAIL, STORE_A)),
            String.class);
    assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(detail.getBody()).doesNotContain(OTHER_STORE_EMAIL, OTHER_STORE_NAME);
  }

  @Test
  @DisplayName("列長を超える表示名は 400 で撥ねること（整合性違反まで届かせない）")
  void overlongDisplayNameIsRejectedAsClientError() {
    String body =
        String.format(
            "{\"email\":\"%s\",\"password\":\"%s\",\"display_name\":\"%s\",\"role_ids\":%s,"
                + "\"store_scope_type\":\"SPECIFIC_STORES\",\"store_ids\":[%d]}",
            "store-staff-it-longname@kizuna.test",
            NEW_ACCOUNT_PASSWORD,
            "あ".repeat(151),
            rolesJson(CLERK_ROLE),
            STORE_A);

    ResponseEntity<String> res =
        rest.postForEntity(
            "/store/staff-members",
            new HttpEntity<>(body, headersFor(MANAGER_EMAIL, STORE_A)),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("店長が自店の平スタッフを作成でき、その本人が新しいメールでログインできること（AC1）")
  void managerCreatesStaffWhoCanLogIn() {
    String email = "store-staff-it-created@kizuna.test";

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/staff-members",
            new HttpEntity<>(
                createBody(email, rolesJson(CLERK_ROLE), "SPECIFIC_STORES", "[" + STORE_A + "]"),
                headersFor(MANAGER_EMAIL, STORE_A)),
            JsonNode.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().path("id").asLong()).isPositive();
    assertThat(created.getBody().path("editable").asBoolean()).isTrue();
    assertThat(loginWithPassword(email, NEW_ACCOUNT_PASSWORD))
        .as("作成された本人が平台ログインできること")
        .isNotBlank();
  }

  @Test
  @DisplayName("委譲権限を含むロールの付与は店長には 400、HQ には通ること（AC2・G1）")
  void delegationRoleGrantIsRefusedForManagerButAllowedForHq() {
    ResponseEntity<String> refused =
        rest.postForEntity(
            "/store/staff-members",
            new HttpEntity<>(
                createBody(
                    "store-staff-it-escalation@kizuna.test",
                    rolesJson(MANAGER_ROLE),
                    "SPECIFIC_STORES",
                    "[" + STORE_A + "]"),
                headersFor(MANAGER_EMAIL, STORE_A)),
            String.class);
    assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    ResponseEntity<String> allowed =
        rest.postForEntity(
            "/store/staff-members",
            new HttpEntity<>(
                createBody(
                    "store-staff-it-hq-granted@kizuna.test",
                    rolesJson(MANAGER_ROLE),
                    "SPECIFIC_STORES",
                    "[" + STORE_A + "]"),
                headersFor("admin@kizuna.test", STORE_A)),
            String.class);
    assertThat(allowed.getStatusCode())
        .as("ROLE_MANAGE 保持者には守衛が課されないこと")
        .isEqualTo(HttpStatus.CREATED);
  }

  @Test
  @DisplayName("担当外店舗を含む店舗集合の指定は店長には 400 になること（AC3・G2）")
  void storeSetOutsideManagerScopeIsRefused() {
    ResponseEntity<String> res =
        rest.postForEntity(
            "/store/staff-members",
            new HttpEntity<>(
                createBody(
                    "store-staff-it-cross-grant@kizuna.test",
                    rolesJson(CLERK_ROLE),
                    "SPECIFIC_STORES",
                    "[" + STORE_A + "," + STORE_B + "]"),
                headersFor(MANAGER_EMAIL, STORE_A)),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("存在しない storeId での作成は FK 違反を 400 へ変換して拒否すること")
  void unknownStoreIdRejected() {
    // ALL_STORES の行使者は担当範囲の検査を素通りするため、店舗 id の在否は保存時の FK でしか止まらない
    // — 店長からは踏めないが、この面の日常的な行使者である HQ からは決定的に踏める経路である。
    // 制約名の抽出が壊れれば兜底へ溢れて 500 になり、別の制約へ誤帰属すれば文言が変わる。
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/store/staff-members",
            new HttpEntity<>(
                createBody(
                    "store-staff-it-unknown-store@kizuna.test",
                    rolesJson(CLERK_ROLE),
                    "SPECIFIC_STORES",
                    "[999999]"),
                headersFor("admin@kizuna.test", STORE_A)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody().path("error").asString()).isEqualTo("指定された店舗が存在しません");
  }

  @Test
  @DisplayName("HQ 側ロールの付与は行使者を問わず 400 になること（母集団の維持）")
  void hqSideRoleGrantIsRefusedEvenForHq() {
    ResponseEntity<String> res =
        rest.postForEntity(
            "/store/staff-members",
            new HttpEntity<>(
                createBody(
                    "store-staff-it-hq-side-grant@kizuna.test",
                    rolesJson(HQ_SIDE_ROLE),
                    "SPECIFIC_STORES",
                    "[" + STORE_A + "]"),
                headersFor("admin@kizuna.test", STORE_A)),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("店舗コンソールへ入れないロール構成での作成は行使者を問わず 400 になること")
  void roleSetWithoutStoreConsoleReachIsRefused() {
    // 着地先の判定（hasStoreConsole）と同じ述語で撥ねる。素通しすると、作成は 201 で成功するのに本人が
    // ログイン後どこへも着地できないアカウントができる。標識権限だけの形と SHARED だけの形の 2 通りがある。
    ResponseEntity<String> markerOnly =
        rest.postForEntity(
            "/store/staff-members",
            new HttpEntity<>(
                createBody(
                    "store-staff-it-menu-only@kizuna.test",
                    rolesJson(MENU_ONLY_ROLE),
                    "SPECIFIC_STORES",
                    "[" + STORE_A + "]"),
                headersFor(MANAGER_EMAIL, STORE_A)),
            String.class);
    assertThat(markerOnly.getStatusCode()).as("標識権限だけのロール").isEqualTo(HttpStatus.BAD_REQUEST);

    ResponseEntity<String> sharedOnly =
        rest.postForEntity(
            "/store/staff-members",
            new HttpEntity<>(
                createBody(
                    "store-staff-it-shared-only@kizuna.test",
                    rolesJson(SHARED_ONLY_ROLE),
                    "SPECIFIC_STORES",
                    "[" + STORE_A + "]"),
                headersFor("admin@kizuna.test", STORE_A)),
            String.class);
    assertThat(sharedOnly.getStatusCode())
        .as("SHARED だけのロールは ROLE_MANAGE 保持者にも撥ねること")
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("着地できないロールも実動権限を含むロールと併せれば作成でき、本人がログインできること（判定は権限の並集）")
  void roleSetReachingStoreConsoleThroughAnotherRoleIsAccepted() {
    String email = "store-staff-it-menu-plus-clerk@kizuna.test";

    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/staff-members",
            new HttpEntity<>(
                createBody(
                    email,
                    rolesJson(MENU_ONLY_ROLE, CLERK_ROLE),
                    "SPECIFIC_STORES",
                    "[" + STORE_A + "]"),
                headersFor(MANAGER_EMAIL, STORE_A)),
            JsonNode.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(loginWithPassword(email, NEW_ACCOUNT_PASSWORD)).isNotBlank();
  }

  @Test
  @DisplayName("既存スタッフを着地できないロール構成へ変更する編集は 400 になること")
  void updateIntoRoleSetWithoutStoreConsoleReachIsRefused() {
    PlatformUser target = platformUserRepository.findByEmail(CLERK_EMAIL).orElseThrow();

    ResponseEntity<String> res =
        rest.exchange(
            "/store/staff-members/" + target.getId(),
            HttpMethod.PUT,
            new HttpEntity<>(
                updateBody(
                    rolesJson(MENU_ONLY_ROLE),
                    "SPECIFIC_STORES",
                    "[" + STORE_A + "]",
                    target.getVersion()),
                headersFor(MANAGER_EMAIL, STORE_A)),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(platformUserRepository.findByEmail(CLERK_EMAIL).orElseThrow().getRoleIds())
        .as("拒否された編集が部分適用されていないこと")
        .isEqualTo(target.getRoleIds());
  }

  @Test
  @DisplayName("委譲権限を実効保持する同僚の編集は店長には 400 になること（G3）")
  void managerCannotEditDelegationHoldingPeer() {
    PlatformUser peer = platformUserRepository.findByEmail(PEER_EMAIL).orElseThrow();

    ResponseEntity<String> res =
        rest.exchange(
            "/store/staff-members/" + peer.getId(),
            HttpMethod.PUT,
            new HttpEntity<>(
                updateBody(rolesJson(CLERK_ROLE), "SPECIFIC_STORES", "[" + STORE_A + "]", 0L),
                headersFor(MANAGER_EMAIL, STORE_A)),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(platformUserRepository.findByEmail(PEER_EMAIL).orElseThrow().getRoleIds())
        .as("拒否された編集が部分適用されていないこと")
        .isEqualTo(peer.getRoleIds());
  }

  @Test
  @DisplayName("HQ は同一画面から任意店舗のスタッフを編集できること（AC5）")
  void hqCanEditStoreStaffThroughTheSameSurface() {
    PlatformUser target = platformUserRepository.findByEmail(CROSS_EMAIL).orElseThrow();

    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/staff-members/" + target.getId(),
            HttpMethod.PUT,
            new HttpEntity<>(
                updateBody(
                    rolesJson(CLERK_ROLE),
                    "SPECIFIC_STORES",
                    "[" + STORE_A + "," + STORE_B + "]",
                    target.getVersion()),
                headersFor("admin@kizuna.test", STORE_B)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().path("editable").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("可授ロールは店長には委譲権限と HQ 側ロールを除いて返り、HQ には委譲権限を含む店舗側ロールも返ること")
  void grantableRolesFollowTheActor() {
    ResponseEntity<String> forManager =
        rest.exchange(
            "/store/staff-members/grantable-roles",
            HttpMethod.GET,
            new HttpEntity<>(headersFor(MANAGER_EMAIL, STORE_A)),
            String.class);
    assertThat(forManager.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(forManager.getBody())
        .contains(CLERK_ROLE)
        .doesNotContain(MANAGER_ROLE, HQ_SIDE_ROLE);

    ResponseEntity<String> forHq =
        rest.exchange(
            "/store/staff-members/grantable-roles",
            HttpMethod.GET,
            new HttpEntity<>(headersFor("admin@kizuna.test", STORE_A)),
            String.class);
    assertThat(forHq.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(forHq.getBody()).contains(CLERK_ROLE, MANAGER_ROLE).doesNotContain(HQ_SIDE_ROLE);
  }

  @Test
  @DisplayName("STAFF_MANAGE を持たない店舗スタッフには全経路が 403 になること")
  void storeStaffWithoutDelegationIsForbidden() {
    ResponseEntity<String> list =
        rest.exchange(
            "/store/staff-members",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            String.class);
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<String> roles =
        rest.exchange(
            "/store/staff-members/grantable-roles",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            String.class);
    assertThat(roles.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  private Boolean editableOf(JsonNode page, String email) {
    for (JsonNode row : page.path("content")) {
      if (email.equals(row.path("email").asString())) {
        return row.path("editable").asBoolean();
      }
    }
    throw new AssertionError("一覧に " + email + " が居ないため editable を判定できない");
  }

  private HttpHeaders headersFor(String email, long storeId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(storeId));
    headers.setBearerAuth(login(email));
    return headers;
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

  private String rolesJson(String... roleNames) {
    return Arrays.stream(roleNames)
        .map(name -> String.valueOf(roleRepository.findByName(name).orElseThrow().getId()))
        .collect(Collectors.joining(",", "[", "]"));
  }

  private Set<Long> roleIdsOf(String roleName) {
    return Set.of(roleRepository.findByName(roleName).orElseThrow().getId());
  }

  private void ensureRole(String name, PermissionCode... codes) {
    Set<String> permissionNames =
        Arrays.stream(codes).map(PermissionCode::name).collect(Collectors.toSet());
    Set<Long> permissionIds =
        permissionRepository.findByCodeIn(permissionNames).stream()
            .map(Permission::getId)
            .collect(Collectors.toSet());
    roleRepository
        .findByName(name)
        .orElseGet(
            () ->
                roleRepository.save(
                    Role.builder().name(name).permissionIds(permissionIds).build()));
  }

  private void ensureUser(String email, String displayName, Set<Long> roleIds, Set<Long> storeIds) {
    platformUserRepository
        .findByEmail(email)
        .orElseGet(
            () ->
                platformUserRepository.save(
                    PlatformUser.builder()
                        .email(email)
                        .password(passwordEncoder.encode(PASSWORD))
                        .displayName(displayName)
                        .enabled(true)
                        .userType(UserType.STAFF)
                        .roleIds(roleIds)
                        .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                        .storeIds(storeIds)
                        .build()));
  }
}
