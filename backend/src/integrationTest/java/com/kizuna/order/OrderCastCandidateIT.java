package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
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
 * GET /store/orders/cast-candidates を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>固定するのは 2 つ。① 指名候補は受注権限だけで読めること（キャスト管理一覧の流用では受注担当が候補を引けない）、② 候補は当店の在籍中に限られること。
 *
 * <p>断言は「帰属不一致」型の弱断言ではなく、応答生ボディに在籍停止・他店舗の実データ（名前）が一切現れないこと（負向強断言）で行う。先例は {@link
 * OrderReceptionistIT}。
 *
 * <p>絞り込みと並びは派生クエリ名が担うため、その解決はリポジトリの bean 生成時にしか失敗しない — 単体テストでは緑のまま壊れる。本クラスが唯一の証跡になる。
 */
class OrderCastCandidateIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "pass";

  /** ORDER_MANAGE のみを持つ自作ロール（キャスト管理権限は持たない）。 */
  private static final String ORDER_ONLY_ROLE = "指名候補IT受注担当";

  private static final String ORDER_ONLY_EMAIL = "cast-candidate-it-order-only@kizuna.test";

  /** カナリアを他 IT が作るキャストから切り分ける接頭辞（内容の断言はこれで絞ってから行う）。 */
  private static final String CANARY_PREFIX = "指名候補IT";

  /** 読み口がサーバ側で固定している 1 回の件数上限。 */
  private static final int MAX_CANDIDATES = 10;

  private static final String ACTIVE_CAST_NAME = CANARY_PREFIX + "_在籍中カナリア";
  private static final String SUSPENDED_CAST_NAME = CANARY_PREFIX + "_在籍停止カナリア";
  private static final String OTHER_STORE_CAST_NAME = CANARY_PREFIX + "_他店舗カナリア";

  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PermissionRepository permissionRepository;
  @Autowired private CastRepository castRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void prepareFixture() {
    Role orderOnly =
        roleRepository
            .findByName(ORDER_ONLY_ROLE)
            .orElseGet(
                () ->
                    roleRepository.save(
                        Role.builder()
                            .name(ORDER_ONLY_ROLE)
                            .permissionIds(permissionIdsOf(PermissionCode.ORDER_MANAGE))
                            .build()));
    // 全店舗授権にするのは、同一トークンで店舗 A・B を引き比べ、混入しないことと述語が常に空を返して
    // いるのではないことを 1 本の中で示すため（店舗の絞り込みは X-Store-ID と storeFilter が担う）。
    ensureUser(ORDER_ONLY_EMAIL, Set.of(orderOnly.getId()));

    ensureCast(ACTIVE_CAST_NAME, STORE_A, "ACTIVE");
    ensureCast(SUSPENDED_CAST_NAME, STORE_A, "INACTIVE");
    ensureCast(OTHER_STORE_CAST_NAME, STORE_B, "ACTIVE");
  }

  /** リポジトリ直挿（テストスレッドは @StoreScoped を経由せず storeFilter が無効なので他店舗にも書ける）。 */
  private void ensureCast(String name, long storeId, String status) {
    boolean exists = castRepository.findAll().stream().anyMatch(c -> name.equals(c.getName()));
    if (!exists) {
      Cast cast = Cast.builder().name(name).status(status).build();
      cast.setStoreId(storeId);
      castRepository.save(cast);
    }
  }

  private void ensureUser(String email, Set<Long> roleIds) {
    platformUserRepository
        .findByEmail(email)
        .orElseGet(
            () ->
                platformUserRepository.save(
                    PlatformUser.builder()
                        .email(email)
                        .password(passwordEncoder.encode(PASSWORD))
                        .displayName("指名候補IT 受注担当")
                        .enabled(true)
                        .userType(UserType.STAFF)
                        .roleIds(roleIds)
                        .storeScopeType(StoreScopeType.ALL_STORES)
                        .storeIds(Set.of())
                        .build()));
  }

  /** 権限コードから播種済み権限行の id 集合を引く（ロールは権限を id 集合で持つ）。 */
  private Set<Long> permissionIdsOf(PermissionCode code) {
    return permissionRepository.findByCodeIn(Set.of(code.name())).stream()
        .map(Permission::getId)
        .collect(Collectors.toSet());
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
    String issued = res.getBody().path("token").asString();
    assertThat(issued).isNotBlank();
    return issued;
  }

  private static HttpHeaders headersFor(String token, long storeId) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(storeId));
    headers.setBearerAuth(token);
    return headers;
  }

  /**
   * 候補一覧を引く。
   *
   * <p>内容の断言は必ず本 IT のカナリア接頭辞で絞ってから行う。件数上限（サーバ側固定）があるため、他の IT が作った在籍中キャストが増えると
   * 絞り込み無しの窓からカナリアが押し出され、断言が実装と無関係に落ちる。
   */
  private ResponseEntity<String> getCandidates(String token, long storeId, String search) {
    String query = search == null ? "" : "?search=" + search;
    return rest.exchange(
        "/store/orders/cast-candidates" + query,
        HttpMethod.GET,
        new HttpEntity<>(headersFor(token, storeId)),
        String.class);
  }

  @Test
  @DisplayName("受注権限のみのロールで指名候補は読めるが、キャスト管理一覧は読めないこと（権限の非対称が解消していること）")
  void orderManageAloneReadsCandidatesButNotTheCastAdminList() {
    String orderOnlyToken = platformToken(ORDER_ONLY_EMAIL);

    ResponseEntity<String> candidates = getCandidates(orderOnlyToken, STORE_A, CANARY_PREFIX);
    assertThat(candidates.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(candidates.getBody()).as("在籍中のキャストが候補として現れること").contains(ACTIVE_CAST_NAME);

    // 同じトークンでキャスト管理一覧は 403。読み口を受注側に持たせた理由そのもの。
    ResponseEntity<String> adminList =
        rest.exchange(
            "/store/casts",
            HttpMethod.GET,
            new HttpEntity<>(headersFor(orderOnlyToken, STORE_A)),
            String.class);
    assertThat(adminList.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("検索語なしでも 200 で、上限件数を超えない配列が返ること")
  void candidatesWithoutAKeywordAreBoundedInsteadOfEmptyOrUnlimited() {
    // コンボボックスは開いた時点で語なしに一度取りに行くため、空クエリが先頭を返すことが要る。
    // 上限値そのものは NominatableCastLookupTest が固定し、ここでは無界で返さないことだけを見る。
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/orders/cast-candidates",
            HttpMethod.GET,
            new HttpEntity<>(headersFor(platformToken(ORDER_ONLY_EMAIL), STORE_A)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().isArray()).isTrue();
    assertThat(res.getBody().size()).as("絞り込み無しでも先頭が返ること").isPositive();
    assertThat(res.getBody().size()).as("件数上限を超えないこと").isLessThanOrEqualTo(MAX_CANDIDATES);
  }

  @Test
  @DisplayName("在籍停止のキャストの実データが候補一覧に一切現れないこと(負向強断言)")
  void candidatesNeverLeakSuspendedCast() {
    // 在籍中・在籍停止のカナリアは同じ接頭辞を持つ。除いているのは検索語ではなく在籍状態であることの担保。
    ResponseEntity<String> res =
        getCandidates(platformToken(ORDER_ONLY_EMAIL), STORE_A, CANARY_PREFIX);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody()).as("在籍停止のキャストの実データが生ボディに現れないこと").doesNotContain(SUSPENDED_CAST_NAME);
    // 正向対照: 同じ店舗の在籍中は現れる（述語が常に空を返しているのではないことの証明）
    assertThat(res.getBody()).contains(ACTIVE_CAST_NAME);
  }

  @Test
  @DisplayName("他店舗のキャストの実データが候補一覧に一切現れないこと(負向強断言)")
  void candidatesNeverLeakOtherStoreCast() {
    String orderOnlyToken = platformToken(ORDER_ONLY_EMAIL);

    ResponseEntity<String> storeA = getCandidates(orderOnlyToken, STORE_A, CANARY_PREFIX);
    assertThat(storeA.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(storeA.getBody())
        .as("他店舗(store B)のキャストの実データが生ボディに現れないこと")
        .doesNotContain(OTHER_STORE_CAST_NAME);

    // 正向対照: 同じトークンで store B を指せば現れる（授権ではなく店舗文脈で切れていることの証明）
    ResponseEntity<String> storeB = getCandidates(orderOnlyToken, STORE_B, CANARY_PREFIX);
    assertThat(storeB.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(storeB.getBody()).contains(OTHER_STORE_CAST_NAME);
    assertThat(storeB.getBody()).as("逆向きも混入しないこと").doesNotContain(ACTIVE_CAST_NAME);
  }

  @Test
  @DisplayName("検索語で名前を絞り込めること（絞り込みはサーバ側が担う）")
  void candidatesAreNarrowedByTheSearchKeyword() {
    ResponseEntity<String> res = getCandidates(platformToken(ORDER_ONLY_EMAIL), STORE_A, "在籍中カナリア");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody()).contains(ACTIVE_CAST_NAME);
    assertThat(res.getBody()).doesNotContain(SUSPENDED_CAST_NAME);
  }
}
