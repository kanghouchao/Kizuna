package com.kizuna.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.user.domain.Permission;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.SystemRole;
import com.kizuna.user.domain.UserType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Set;
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
 * 店長設定の HTTP 境界統合テスト。店長が「STORE_MANAGER 保持 かつ 当該店舗を担当範囲に含む」の導出として本物の PostgreSQL 上で正しく絞られること、
 * 任命・解任が授権を実際に書き換えること、不変条件と衝突する解任が撥ねられることを固定する。
 *
 * <p>語義の撃ち分け（母集団の各条件・要求本体の二択）は {@code StoreManagerServiceTest} が持つ。ここが担うのは、導出の述語が実 DB
 * で成立すること、新規作成した店長が実際に店舗コンソールへ着地すること、そして行を押さえていることの実測である。
 */
class StoreManagerAppointmentIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "pass";

  /** ROLE_MANAGE を持つ種子の HQ 管理者。この面の唯一の行使者。 */
  private static final String HQ_EMAIL = "admin@kizuna.test";

  /** 店舗A・B を担当する店長。解任が通る側（担当が 1 店に減っても不変条件を満たす）。 */
  private static final String TWO_STORE_MANAGER_EMAIL = "store-manager-it-two-stores@kizuna.test";

  /** 店舗A だけを担当する店長。最後の 1 店からの解任が撥ねられる側。 */
  private static final String LAST_STORE_MANAGER_EMAIL = "store-manager-it-last-store@kizuna.test";

  /** 全店舗担当の店長。一覧には出るが、除去の形が無いため解任できない。 */
  private static final String ALL_STORES_MANAGER_EMAIL = "store-manager-it-all-stores@kizuna.test";

  /** 店舗A の平スタッフ。任命候補には出るが、店長一覧には出ない。 */
  private static final String CLERK_EMAIL = "store-manager-it-clerk@kizuna.test";

  /** 店舗B だけを担当する店長。店舗A の一覧には出てはならない。 */
  private static final String OTHER_STORE_MANAGER_EMAIL =
      "store-manager-it-other-store@kizuna.test";

  /** HQ 側ロール保持者。任命候補の母集団から外れる（管理者管理の領分）。 */
  private static final String HQ_SIDE_EMAIL = "store-manager-it-hq-side@kizuna.test";

  /** 停止中のスタッフ。任命候補の母集団から外れる。 */
  private static final String STOPPED_EMAIL = "store-manager-it-stopped@kizuna.test";

  private static final String CLERK_ROLE = "店長設定IT_平スタッフ";

  private static final String HQ_SIDE_ROLE = "店長設定IT_HQ側";

  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PermissionRepository permissionRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private DataSource dataSource;
  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void prepareManagerFixture() {
    ensureRole(CLERK_ROLE, PermissionCode.ORDER_MANAGE, PermissionCode.STORE_MENU_VIEW);
    ensureRole(HQ_SIDE_ROLE, PermissionCode.STORE_MANAGE, PermissionCode.STORE_VIEW);

    ensureUser(
        TWO_STORE_MANAGER_EMAIL, "店長設定IT二店", managerRoleIds(), Set.of(STORE_A, STORE_B), true);
    ensureUser(LAST_STORE_MANAGER_EMAIL, "店長設定IT一店", managerRoleIds(), Set.of(STORE_A), true);
    ensureUser(OTHER_STORE_MANAGER_EMAIL, "店長設定IT他店", managerRoleIds(), Set.of(STORE_B), true);
    ensureUser(CLERK_EMAIL, "店長設定IT平スタッフ", roleIdsOf(CLERK_ROLE), Set.of(STORE_A), true);
    ensureUser(HQ_SIDE_EMAIL, "店長設定IT_HQ側保持者", roleIdsOf(HQ_SIDE_ROLE), Set.of(STORE_A), true);
    ensureUser(STOPPED_EMAIL, "店長設定IT停止中", roleIdsOf(CLERK_ROLE), Set.of(STORE_A), false);
    ensureAllStoresUser(ALL_STORES_MANAGER_EMAIL, "店長設定IT全店", managerRoleIds());
  }

  @Test
  @DisplayName("一覧は STORE_MANAGE 保持者でなく ROLE_MANAGE 保持者だけに開くこと（AC4）")
  void listRequiresRoleManage() {
    // 種子の店長は STAFF_MANAGE を持つが ROLE_MANAGE は持たない。
    ResponseEntity<String> res =
        rest.exchange(
            managersPath(STORE_A),
            HttpMethod.GET,
            new HttpEntity<>(bearerJson(login("tanaka.hanako@kizuna.test"))),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("任命・解任・候補も ROLE_MANAGE 非保持者には 403 になること（AC4）")
  void writeAndCandidateSurfacesRequireRoleManage() {
    HttpHeaders staff = bearerJson(login("tanaka.hanako@kizuna.test"));

    assertThat(
            rest.exchange(
                    candidatesPath(STORE_A), HttpMethod.GET, new HttpEntity<>(staff), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            rest.exchange(
                    managersPath(STORE_A),
                    HttpMethod.POST,
                    new HttpEntity<>("{\"user_id\":1}", staff),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            rest.exchange(
                    managersPath(STORE_A) + "/1",
                    HttpMethod.DELETE,
                    new HttpEntity<>(staff),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("一覧は当該店舗を担当する店長だけを返し、他店の店長も平スタッフも現れないこと")
  void listDerivesManagersFromRoleAndStoreScope() {
    ResponseEntity<String> res =
        rest.exchange(
            managersPath(STORE_A), HttpMethod.GET, new HttpEntity<>(hqHeaders()), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody())
        .as("当該店舗を担当する店長は全て出ること（ALL_STORES も担当範囲に含む）")
        .contains(TWO_STORE_MANAGER_EMAIL, LAST_STORE_MANAGER_EMAIL, ALL_STORES_MANAGER_EMAIL);
    assertThat(res.getBody())
        .as("他店だけの店長と、店長ロールを持たない同店スタッフは出ないこと")
        .doesNotContain(OTHER_STORE_MANAGER_EMAIL, CLERK_EMAIL);
  }

  @Test
  @DisplayName("任命候補は母集団外（HQ 側ロール・全店舗担当・停止中・既に本店の店長）を外すこと")
  void candidatesExcludeIneligibleAccounts() {
    ResponseEntity<String> res =
        rest.exchange(
            candidatesPath(STORE_A) + "&size=200",
            HttpMethod.GET,
            new HttpEntity<>(hqHeaders()),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody()).as("同店の平スタッフは候補に出ること").contains(CLERK_EMAIL);
    assertThat(res.getBody())
        .as("他店の店長は候補に残ること（任命でこの店舗が担当へ加わる）")
        .contains(OTHER_STORE_MANAGER_EMAIL);
    assertThat(res.getBody())
        .as("HQ 側ロール保持者・全店舗担当・停止中・既に本店の店長は候補に出ないこと")
        .doesNotContain(
            HQ_SIDE_EMAIL, ALL_STORES_MANAGER_EMAIL, STOPPED_EMAIL, LAST_STORE_MANAGER_EMAIL);
  }

  @Test
  @DisplayName("既存アカウントの任命でロールと担当店舗が和で書き換わること（AC1）")
  void appointingExistingStaffGrantsRoleAndStore() {
    long clerkId = idOf(CLERK_EMAIL);

    ResponseEntity<JsonNode> res =
        rest.exchange(
            managersPath(STORE_B),
            HttpMethod.POST,
            new HttpEntity<>("{\"user_id\":" + clerkId + "}", hqHeaders()),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    PlatformUser appointed = platformUserRepository.findByEmail(CLERK_EMAIL).orElseThrow();
    assertThat(appointed.getRoleIds())
        .containsExactlyInAnyOrderElementsOf(union(roleIdsOf(CLERK_ROLE), managerRoleIds()));
    assertThat(appointed.getStoreIds()).containsExactlyInAnyOrder(STORE_A, STORE_B);
  }

  @Test
  @DisplayName("既にこの店舗の店長を再度任命すると 400 になること")
  void appointingCurrentManagerIsRejected() {
    ResponseEntity<String> res =
        rest.exchange(
            managersPath(STORE_A),
            HttpMethod.POST,
            new HttpEntity<>("{\"user_id\":" + idOf(LAST_STORE_MANAGER_EMAIL) + "}", hqHeaders()),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("新規作成での任命で生まれたアカウントは、ログインすると店舗コンソールへ着地すること（AC1・AC2）")
  void createdManagerLandsOnStoreConsole() {
    String email = "store-manager-it-fresh@kizuna.test";

    ResponseEntity<JsonNode> created =
        rest.exchange(
            managersPath(STORE_B),
            HttpMethod.POST,
            new HttpEntity<>(
                String.format(
                    "{\"email\":\"%s\",\"password\":\"%s\",\"display_name\":\"初代店長\"}",
                    email, PASSWORD),
                hqHeaders()),
            JsonNode.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().path("enabled").asBoolean()).isTrue();

    ResponseEntity<JsonNode> me =
        rest.exchange(
            "/platform/me",
            HttpMethod.GET,
            new HttpEntity<>(bearerJson(login(email))),
            JsonNode.class);

    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me.getBody().path("console").asString()).as("着地先が店舗コンソールであること").isEqualTo("store");
    assertThat(me.getBody().path("store_ids")).as("担当店舗が任命した店舗だけであること").hasSize(1);
    assertThat(me.getBody().path("store_ids").get(0).asLong()).isEqualTo(STORE_B);
  }

  @Test
  @DisplayName("複数店舗を担当する店長の解任は当該店舗だけを落とし、店長ロールは残ること（AC1）")
  void dismissingMultiStoreManagerRemovesOnlyThatStore() {
    ResponseEntity<String> res =
        rest.exchange(
            managersPath(STORE_A) + "/" + idOf(TWO_STORE_MANAGER_EMAIL),
            HttpMethod.DELETE,
            new HttpEntity<>(hqHeaders()),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    PlatformUser dismissed =
        platformUserRepository.findByEmail(TWO_STORE_MANAGER_EMAIL).orElseThrow();
    assertThat(dismissed.getStoreIds()).containsExactly(STORE_B);
    assertThat(dismissed.getRoleIds()).containsExactlyInAnyOrderElementsOf(managerRoleIds());
  }

  @Test
  @DisplayName("最後の 1 店からの解任は 400 で撥ね、誘導先を文言に載せること（AC3）")
  void dismissingFromTheLastStoreIsRejectedWithGuidance() {
    long managerId = idOf(LAST_STORE_MANAGER_EMAIL);

    ResponseEntity<JsonNode> res =
        rest.exchange(
            managersPath(STORE_A) + "/" + managerId,
            HttpMethod.DELETE,
            new HttpEntity<>(hqHeaders()),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody().path("error").asString()).contains("店舗スタッフ管理");
    assertThat(platformUserRepository.findById(managerId).orElseThrow().getStoreIds())
        .as("撥ねた解任が部分適用されていないこと")
        .containsExactly(STORE_A);
  }

  @Test
  @DisplayName("全店舗担当の店長の解任は除去の形が無いため 400 になること")
  void dismissingAllStoresManagerIsRejected() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            managersPath(STORE_A) + "/" + idOf(ALL_STORES_MANAGER_EMAIL),
            HttpMethod.DELETE,
            new HttpEntity<>(hqHeaders()),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody().path("error").asString()).contains("店舗スタッフ管理");
  }

  @Test
  @DisplayName("この店舗の店長でない対象の解任は 404 になること")
  void dismissingNonManagerIsNotFound() {
    ResponseEntity<String> res =
        rest.exchange(
            managersPath(STORE_A) + "/" + idOf(CLERK_EMAIL),
            HttpMethod.DELETE,
            new HttpEntity<>(hqHeaders()),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("実在しない店舗宛は空一覧でなく 404 になること")
  void unknownStoreIsNotFound() {
    assertThat(
            rest.exchange(
                    managersPath(999_999L),
                    HttpMethod.GET,
                    new HttpEntity<>(hqHeaders()),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            rest.exchange(
                    candidatesPath(999_999L),
                    HttpMethod.GET,
                    new HttpEntity<>(hqHeaders()),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  // 任命・解任は版を運ばない read-modify-write なので、押さえていないと並行編集に上書きされる。押さえていなくても
  // 単体の答えは変わらないため、押さえた行が別接続から取れないことでしか「実際に押さえている」を確かめられない。
  @Test
  @DisplayName("任命・解任の対象取得は行を実際に押さえること")
  void targetLookupLocksTheRow() {
    long managerId = idOf(TWO_STORE_MANAGER_EMAIL);

    new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              assertThat(platformUserRepository.findByIdForUpdate(managerId)).isPresent();

              assertThatThrownBy(() -> lockNoWait(managerId))
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

  private static String managersPath(long storeId) {
    return "/platform/stores/" + storeId + "/managers";
  }

  private static String candidatesPath(long storeId) {
    return "/platform/stores/" + storeId + "/manager-candidates?search=店長設定IT";
  }

  private HttpHeaders hqHeaders() {
    return bearerJson(login(HQ_EMAIL));
  }

  private static HttpHeaders bearerJson(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private long idOf(String email) {
    return platformUserRepository.findByEmail(email).orElseThrow().getId();
  }

  private Set<Long> managerRoleIds() {
    return roleIdsOf(SystemRole.STORE_MANAGER.getRoleName());
  }

  private Set<Long> roleIdsOf(String roleName) {
    return Set.of(roleRepository.findByName(roleName).orElseThrow().getId());
  }

  private static Set<Long> union(Set<Long> left, Set<Long> right) {
    return java.util.stream.Stream.concat(left.stream(), right.stream())
        .collect(Collectors.toSet());
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

  /**
   * 宣言した授権をそのまま持つ利用者に揃える。既に居れば書き戻すのは、任命・解任を通すテストが同じ行を書き換えるためで、 「作成だけして既存はそのまま」だと後続のテストが前のテストの結果を見る。
   */
  private void ensureUser(
      String email, String displayName, Set<Long> roleIds, Set<Long> storeIds, boolean enabled) {
    ensureUser(email, displayName, roleIds, StoreScopeType.SPECIFIC_STORES, storeIds, enabled);
  }

  private void ensureAllStoresUser(String email, String displayName, Set<Long> roleIds) {
    ensureUser(email, displayName, roleIds, StoreScopeType.ALL_STORES, Set.of(), true);
  }

  private void ensureUser(
      String email,
      String displayName,
      Set<Long> roleIds,
      StoreScopeType scopeType,
      Set<Long> storeIds,
      boolean enabled) {
    PlatformUser user =
        platformUserRepository
            .findByEmail(email)
            .orElseGet(
                () ->
                    PlatformUser.builder()
                        .email(email)
                        .password(passwordEncoder.encode(PASSWORD))
                        .displayName(displayName)
                        .enabled(enabled)
                        .userType(UserType.STAFF)
                        .roleIds(roleIds)
                        .storeScopeType(scopeType)
                        .storeIds(storeIds)
                        .build());
    user.reassignGrants(roleIds, scopeType, storeIds);
    if (enabled) {
      user.resume();
    } else {
      user.stop();
    }
    platformUserRepository.save(user);
  }
}
