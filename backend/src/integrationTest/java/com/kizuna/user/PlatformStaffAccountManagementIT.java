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
import java.util.List;
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
 * アカウント管理（{@code /platform/staff-accounts}）の HTTP 境界統合テスト。STAFF_ACCOUNT_MANAGE 門、対象が本人種別 STAFF の全
 * アカウントであること（店舗側ロールしか持たない者も含む）、CAST/MEMBER の在否を出さないこと、停止・再開の往復、絞り込みを 本物の PostgreSQL で固定する。様式は
 * {@link PlatformStaffManagementIT} に倣う。
 *
 * <p>一覧の断言は必ずカナリア接頭辞での絞り込みを通す。母集団は DB 全体のスタッフなので、頁の先頭に何が載るかは 並行して走る他の IT の作る行に左右される。
 *
 * <p>不減零（ADR 0020 の守衛 G5）のうち拒否側はここでは固定できない — 母集団は DB 全体の ROLE_MANAGE 実効保持者で、種子の HQ 管理者を止めないと「最後の 1
 * 人」を作れず、それは後続 IT を連鎖破綻させる。拒否の判定は {@code PlatformStaffAccountServiceTest} が持ち、ここは
 * 母集団が残る側（保持者の停止が通ること）で照会そのものが実 DB で成立することを固定する。
 */
class PlatformStaffAccountManagementIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "pass";

  /** ALL_STORES の HQ 管理者シード（既定授与で STAFF_ACCOUNT_MANAGE を持つ）。 */
  private static final String SEED_EMAIL = "admin@kizuna.test";

  private static final String STORE_A_DOMAIN = "account-it-store-a.kizuna.test";
  private static final String STORE_A_NAME = "アカウント管理IT_店舗A";
  private static final String STORE_B_DOMAIN = "account-it-store-b.kizuna.test";
  private static final String STORE_B_NAME = "アカウント管理IT_店舗B";

  /** 店舗側ロールしか持たない利用者。旧来の管理者管理では扱えず、この面で初めて停止できる側の代表。 */
  private static final String STORE_ONLY_EMAIL = "account-it-store-only@kizuna.test";

  private static final String STORE_ONLY_NAME = "アカウント管理IT_店舗側のみ";

  /** 店舗B だけを担当する利用者。店舗絞り込みの負側カナリア。 */
  private static final String STORE_B_ONLY_EMAIL = "account-it-store-b-only@kizuna.test";

  private static final String STORE_B_ONLY_NAME = "アカウント管理IT_店舗B専任";

  /** STAFF_ACCOUNT_MANAGE を持たない利用者（門の負側）。 */
  private static final String NON_HOLDER_EMAIL = "account-it-nonholder@kizuna.test";

  private static final String CAST_EMAIL = "account-it-cast@kizuna.test";
  private static final String CAST_NAME = "アカウント管理IT_キャスト圏外";
  private static final String MEMBER_EMAIL = "account-it-member@kizuna.test";
  private static final String MEMBER_NAME = "アカウント管理IT_会員圏外";

  /** ROLE_MANAGE を含む IT 専用ロールと、その保持者（不減零の照会を実 DB で通すため）。 */
  private static final String ROLE_MANAGE_ROLE = "アカウント管理IT_管理権限";

  private static final String ROLE_MANAGE_HOLDER_EMAIL = "account-it-rolemanage@kizuna.test";

  /** ROLE_MANAGE を含まない HQ 側ロールと、その保持者（授権管理の PUT を撃つ対象）。 */
  private static final String HQ_SIDE_ROLE = "アカウント管理IT_HQ側";

  private static final String PUT_TARGET_EMAIL = "account-it-put-target@kizuna.test";

  @Autowired private StoreRepository storeRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PermissionRepository permissionRepository;

  private long storeAId;
  private long storeBId;

  @BeforeEach
  void prepareAccountFixture() {
    storeAId = ensureStore(STORE_A_DOMAIN, STORE_A_NAME);
    storeBId = ensureStore(STORE_B_DOMAIN, STORE_B_NAME);
    ensureEnabledUser(
        STORE_ONLY_EMAIL,
        STORE_ONLY_NAME,
        UserType.STAFF,
        roleIdsOf("店長"),
        StoreScopeType.SPECIFIC_STORES,
        Set.of(storeAId));
    ensureEnabledUser(
        STORE_B_ONLY_EMAIL,
        STORE_B_ONLY_NAME,
        UserType.STAFF,
        roleIdsOf("店長"),
        StoreScopeType.SPECIFIC_STORES,
        Set.of(storeBId));
    ensureEnabledUser(
        NON_HOLDER_EMAIL,
        "アカウント管理IT_門の外",
        UserType.STAFF,
        roleIdsOf("店長"),
        StoreScopeType.ALL_STORES,
        Set.of());
    ensureEnabledUser(
        CAST_EMAIL, CAST_NAME, UserType.CAST, Set.of(), StoreScopeType.ALL_STORES, Set.of());
    ensureEnabledUser(
        MEMBER_EMAIL,
        MEMBER_NAME,
        UserType.MEMBER,
        Set.of(),
        StoreScopeType.SPECIFIC_STORES,
        Set.of(storeAId));
    ensureEnabledUser(
        ROLE_MANAGE_HOLDER_EMAIL,
        "アカウント管理IT_管理権限保持",
        UserType.STAFF,
        Set.of(customRoleId(ROLE_MANAGE_ROLE, PermissionCode.ROLE_MANAGE)),
        StoreScopeType.ALL_STORES,
        Set.of());
    ensureEnabledUser(
        PUT_TARGET_EMAIL,
        "アカウント管理IT_授権編集対象",
        UserType.STAFF,
        Set.of(customRoleId(HQ_SIDE_ROLE, PermissionCode.STORE_MANAGE)),
        StoreScopeType.ALL_STORES,
        Set.of());
  }

  private long ensureStore(String domain, String name) {
    return storeRepository
        .findByDomain(domain)
        .orElseGet(() -> storeRepository.save(new Store(name, domain, null)))
        .getId();
  }

  /** 専用テストユーザーを取得または作成する。前回実行の残留（停止済み）状態があれば enabled=true へ戻す。 */
  private PlatformUser ensureEnabledUser(
      String email,
      String displayName,
      UserType userType,
      Set<Long> roleIds,
      StoreScopeType scopeType,
      Set<Long> storeIds) {
    PlatformUser user =
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
                            .userType(userType)
                            .roleIds(roleIds)
                            .storeScopeType(scopeType)
                            .storeIds(storeIds)
                            .build()));
    if (!user.getEnabled()) {
      user.resume();
      user = platformUserRepository.saveAndFlush(user);
    }
    return user;
  }

  private long customRoleId(String roleName, PermissionCode... codes) {
    return roleRepository
        .findByName(roleName)
        .orElseGet(
            () ->
                roleRepository.save(
                    Role.builder().name(roleName).permissionIds(permissionIdsOf(codes)).build()))
        .getId();
  }

  private Set<Long> permissionIdsOf(PermissionCode... codes) {
    Set<String> names = Arrays.stream(codes).map(PermissionCode::name).collect(Collectors.toSet());
    return permissionRepository.findByCodeIn(names).stream()
        .map(Permission::getId)
        .collect(Collectors.toSet());
  }

  private Set<Long> roleIdsOf(String roleName) {
    return Set.of(roleIdByName(roleName));
  }

  private long roleIdByName(String roleName) {
    return roleRepository.findByName(roleName).orElseThrow().getId();
  }

  /** URL に載せる検索語（メールアドレスの局所部）。非 ASCII を URI テンプレートへ持ち込まないための取り決め。 */
  private static String searchTermOf(String email) {
    return email.substring(0, email.indexOf('@'));
  }

  private long idOf(String email) {
    return platformUserRepository.findByEmail(email).orElseThrow().getId();
  }

  private boolean enabledOf(String email) {
    return platformUserRepository.findByEmail(email).orElseThrow().getEnabled();
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

  private ResponseEntity<JsonNode> listWith(String token, String query) {
    return rest.exchange(
        "/platform/staff-accounts" + query,
        HttpMethod.GET,
        new HttpEntity<>(bearer(token)),
        JsonNode.class);
  }

  private ResponseEntity<String> action(String token, long id, String action) {
    return rest.exchange(
        "/platform/staff-accounts/" + id + "/" + action,
        HttpMethod.POST,
        new HttpEntity<>(bearer(token)),
        String.class);
  }

  /** 一覧応答から display_name を取り出す（Page 形状の content 配列）。 */
  private static List<String> displayNamesOf(JsonNode body) {
    List<String> names = new ArrayList<>();
    body.path("content").forEach(row -> names.add(row.path("display_name").asString("")));
    return names;
  }

  @Test
  @DisplayName("STAFF_ACCOUNT_MANAGE を持たない利用者は全ての端点で 403 になり、保持者は通ること(門)")
  void accountFaceIsGatedByStaffAccountManage() {
    String outsider = login(NON_HOLDER_EMAIL);
    long targetId = idOf(STORE_ONLY_EMAIL);

    assertThat(listWith(outsider, "").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            rest.exchange(
                    "/platform/staff-accounts/" + targetId,
                    HttpMethod.GET,
                    new HttpEntity<>(bearer(outsider)),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(action(outsider, targetId, "suspension").getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(action(outsider, targetId, "resumption").getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    assertThat(listWith(login(SEED_EMAIL), "").getStatusCode())
        .as("既定授与で STAFF_ACCOUNT_MANAGE を持つ HQ 管理者は通ること")
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("店舗側ロールしか持たないアカウントが一覧に現れ、停止と再開ができること(AC の中核)")
  void storeSideOnlyAccountIsListedAndCanBeSuspendedAndResumed() {
    String hq = login(SEED_EMAIL);
    long targetId = idOf(STORE_ONLY_EMAIL);

    ResponseEntity<JsonNode> listed = listWith(hq, "?search=" + searchTermOf(STORE_ONLY_EMAIL));
    assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(displayNamesOf(listed.getBody())).containsExactly(STORE_ONLY_NAME);

    assertThat(action(hq, targetId, "suspension").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(enabledOf(STORE_ONLY_EMAIL)).as("停止で enabled が倒れること").isFalse();

    assertThat(action(hq, targetId, "resumption").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(enabledOf(STORE_ONLY_EMAIL)).as("再開で enabled が戻ること").isTrue();
  }

  @Test
  @DisplayName("CAST と MEMBER は一覧に現れず、id 直指定の取得・停止も 404 になること(在否を漏らさない)")
  void castAndMemberAreOutOfScope() {
    String hq = login(SEED_EMAIL);

    for (String email : List.of(CAST_EMAIL, MEMBER_EMAIL)) {
      ResponseEntity<JsonNode> listed = listWith(hq, "?search=" + searchTermOf(email));
      assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(displayNamesOf(listed.getBody())).as("%s は一覧に現れない", email).isEmpty();
    }

    for (String email : List.of(CAST_EMAIL, MEMBER_EMAIL)) {
      long id = idOf(email);
      assertThat(
              rest.exchange(
                      "/platform/staff-accounts/" + id,
                      HttpMethod.GET,
                      new HttpEntity<>(bearer(hq)),
                      String.class)
                  .getStatusCode())
          .as("%s の詳細は 404", email)
          .isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(action(hq, id, "suspension").getStatusCode())
          .as("%s の停止は 404", email)
          .isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(enabledOf(email)).as("404 の対象は書き換わらないこと").isTrue();
    }
  }

  @Test
  @DisplayName("実行主体が自分自身を停止しようとすると 400 になること(G4)")
  void selfSuspensionIsRejected() {
    String hq = login(SEED_EMAIL);

    assertThat(action(hq, idOf(SEED_EMAIL), "suspension").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(enabledOf(SEED_EMAIL)).isTrue();
  }

  @Test
  @DisplayName("他に保持者が残るなら ROLE_MANAGE 実効保持者の停止は通ること(不減零の照会が実 DB で成立する対照)")
  void roleManageHolderCanBeSuspendedWhileAnotherRemains() {
    String hq = login(SEED_EMAIL);
    long targetId = idOf(ROLE_MANAGE_HOLDER_EMAIL);

    assertThat(action(hq, targetId, "suspension").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(enabledOf(ROLE_MANAGE_HOLDER_EMAIL)).isFalse();

    // 母集団を元へ戻す（後続 IT の前提を動かさない）。
    assertThat(action(hq, targetId, "resumption").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(enabledOf(ROLE_MANAGE_HOLDER_EMAIL)).isTrue();
  }

  @Test
  @DisplayName("search はメールアドレスも横断し、store_id は担当店舗で絞ること(正負 1 行ずつ)")
  void searchAndStoreIdFiltersNarrowTheList() {
    String hq = login(SEED_EMAIL);

    ResponseEntity<JsonNode> byEmail = listWith(hq, "?search=" + searchTermOf(STORE_B_ONLY_EMAIL));
    assertThat(byEmail.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(displayNamesOf(byEmail.getBody()))
        .as("検索語はメールアドレスにも当たること")
        .containsExactly(STORE_B_ONLY_NAME);

    ResponseEntity<JsonNode> byStore =
        listWith(hq, "?search=account-it-store-&storeId=" + storeAId);
    assertThat(byStore.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(displayNamesOf(byStore.getBody()))
        .as("店舗A担当のみが残り、店舗B専任は落ちること")
        .contains(STORE_ONLY_NAME)
        .doesNotContain(STORE_B_ONLY_NAME);
  }

  @Test
  @DisplayName("授権管理の PUT に enabled を載せると 400 になり、DB の enabled も動かないこと")
  void grantUpdateRejectsTheRemovedEnabledField() {
    String hq = login(SEED_EMAIL);
    PlatformUser target = platformUserRepository.findByEmail(PUT_TARGET_EMAIL).orElseThrow();
    String body =
        String.format(
            "{\"role_ids\":[%d],\"store_scope_type\":\"ALL_STORES\",\"store_ids\":[],"
                + "\"enabled\":false,\"version\":%d}",
            roleIdByName(HQ_SIDE_ROLE), target.getVersion());

    ResponseEntity<String> res =
        rest.exchange(
            "/platform/staff/" + target.getId(),
            HttpMethod.PUT,
            new HttpEntity<>(body, bearerJson(hq)),
            String.class);

    assertThat(res.getStatusCode())
        .as("停止・再開は授権管理の面から外れたので、enabled を含む要求は未知項目として撥ねられること")
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(enabledOf(PUT_TARGET_EMAIL)).isTrue();
  }
}
