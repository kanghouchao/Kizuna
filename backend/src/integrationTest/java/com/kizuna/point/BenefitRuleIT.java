package com.kizuna.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.point.domain.BenefitRuleRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

/**
 * 特典規則（{@code /platform/benefit-rules}）の HTTP 境界統合テスト。BENEFIT_MANAGE 門、作成・編集・停用の往復、版照合、および 集約と同じ規則を
 * DB 側にも置いた CHECK 制約を本物の PostgreSQL で固定する。
 *
 * <p>制約の断言は集約を通さない native INSERT で行う。集約が先に撥ねる経路しか無いと、CHECK の述語が誤っていても永久に緑のままになる。
 */
class BenefitRuleIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "pass";

  /** ALL_STORES の HQ 管理者シード（既定授与で BENEFIT_MANAGE を持つ）。 */
  private static final String SEED_EMAIL = "admin@kizuna.test";

  /** BENEFIT_MANAGE を持たない利用者（門の負側）。店長束は店舗側の権限しか持たない。 */
  private static final String NON_HOLDER_EMAIL = "benefit-it-nonholder@kizuna.test";

  private static final String STORE_DOMAIN = "benefit-it-store.kizuna.test";
  private static final String STORE_NAME = "特典規則IT_店舗";

  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private StoreRepository storeRepository;
  @Autowired private BenefitRuleRepository benefitRuleRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String hqToken;
  private String nonHolderToken;
  private long storeId;

  @BeforeEach
  void prepareBenefitRuleFixture() {
    storeId =
        storeRepository
            .findByDomain(STORE_DOMAIN)
            .orElseGet(() -> storeRepository.save(new Store(STORE_NAME, STORE_DOMAIN, null)))
            .getId();
    ensureEnabledUser(NON_HOLDER_EMAIL, "特典規則IT_門の外");
    hqToken = login(SEED_EMAIL);
    nonHolderToken = loginWithPassword(NON_HOLDER_EMAIL, PASSWORD);
  }

  @Test
  @DisplayName("BENEFIT_MANAGE 保持者は五要素を持つ規則を作成し、一覧と詳細で読み戻せること")
  void createAndReadBackAllFiveElements() {
    long id =
        createRule(
            """
            {"name":"特典規則IT_来店ボーナス","type":"VISIT","store_scope_type":"SPECIFIC_STORES",
             "store_ids":[%d],"effective_from":"2026-09-01","effective_until":"2026-12-31",
             "grant_validity_days":180,"repeat_policy":"ONCE_PER_MEMBER","points":500}
            """
                .formatted(storeId));

    JsonNode detail = get("/platform/benefit-rules/" + id).getBody();
    assertThat(detail.path("type").asString()).isEqualTo("VISIT");
    assertThat(detail.path("store_scope_type").asString()).isEqualTo("SPECIFIC_STORES");
    assertThat(detail.path("store_ids").get(0).asLong()).isEqualTo(storeId);
    assertThat(detail.path("effective_from").asString()).isEqualTo("2026-09-01");
    assertThat(detail.path("effective_until").asString()).isEqualTo("2026-12-31");
    assertThat(detail.path("grant_validity_days").asInt()).isEqualTo(180);
    assertThat(detail.path("repeat_policy").asString()).isEqualTo("ONCE_PER_MEMBER");
    assertThat(detail.path("points").asInt()).isEqualTo(500);
    assertThat(detail.path("enabled").asBoolean()).isTrue();
    // 取消方法は種別から導くので、応答にも列にも現れない
    assertThat(detail.has("cancellation_policy")).isFalse();

    ResponseEntity<String> list = listRaw(hqToken);
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(list.getBody()).contains("特典規則IT_来店ボーナス");
    // 一覧の型は店舗 ID の列挙を持たず、件数へ畳む
    assertThat(list.getBody()).contains("\"store_count\":1");
  }

  @Test
  @DisplayName("常設・無期限の規則は期間を持たずに作成できること")
  void periodsAreOptional() {
    long id =
        createRule(
            """
            {"name":"特典規則IT_常設","type":"VISIT","store_scope_type":"ALL_STORES",
             "repeat_policy":"EVERY_TIME","points":100}
            """);

    JsonNode detail = get("/platform/benefit-rules/" + id).getBody();
    // 非 null 包含なので、値なしは項目ごと落ちる
    assertThat(detail.has("effective_from")).isFalse();
    assertThat(detail.has("effective_until")).isFalse();
    assertThat(detail.has("grant_validity_days")).isFalse();
  }

  @Test
  @DisplayName("紹介規則は紹介者・被紹介者の二値を保持できること")
  void referralRuleCarriesBothPointValues() {
    long id =
        createRule(
            """
            {"name":"特典規則IT_紹介","type":"REFERRAL","store_scope_type":"ALL_STORES",
             "repeat_policy":"EVERY_TIME","referrer_points":1000,"referred_points":500}
            """);

    JsonNode detail = get("/platform/benefit-rules/" + id).getBody();
    assertThat(detail.path("referrer_points").asInt()).isEqualTo(1000);
    assertThat(detail.path("referred_points").asInt()).isEqualTo(500);
    assertThat(detail.has("points")).isFalse();
  }

  @Test
  @DisplayName("目録に無い種別は 400 で撥ねられること")
  void unknownTypeIsRejected() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/benefit-rules",
            HttpMethod.POST,
            new HttpEntity<>(
                """
                {"name":"特典規則IT_謎","type":"BIRTHDAY","store_scope_type":"ALL_STORES",
                 "repeat_policy":"EVERY_TIME","points":100}
                """,
                bearerJson(hqToken)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("停用は一方通行で、停用済みの規則は編集も二度目の停用も受け付けないこと")
  void deactivationIsOneWayAndFreezesTheRule() {
    long id =
        createRule(
            """
            {"name":"特典規則IT_停用対象","type":"VISIT","store_scope_type":"ALL_STORES",
             "repeat_policy":"EVERY_TIME","points":200}
            """);
    assertThat(deactivate(id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    JsonNode afterDeactivation = get("/platform/benefit-rules/" + id).getBody();
    assertThat(afterDeactivation.path("enabled").asBoolean()).isFalse();

    assertThat(deactivate(id).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    // 版は停用が進めているので、退場後の状態そのものを撃つには取り直した版を載せる。版照合が先
    // （陳腐なフォームは 409）で、版が合ってはじめて退場済みの 400 に届く。
    assertThat(
            update(
                    id,
                    """
                    {"name":"特典規則IT_停用対象（改）","store_scope_type":"ALL_STORES",
                     "repeat_policy":"EVERY_TIME","points":300,"version":%d}
                    """
                        .formatted(afterDeactivation.path("version").asLong()))
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("編集は種別を動かさず、陳腐化した版の提出は 409 で撥ねられること")
  void updateKeepsTypeAndRefusesStaleVersion() {
    long id =
        createRule(
            """
            {"name":"特典規則IT_編集対象","type":"VISIT","store_scope_type":"ALL_STORES",
             "repeat_policy":"EVERY_TIME","points":200}
            """);
    long version = get("/platform/benefit-rules/" + id).getBody().path("version").asLong();

    ResponseEntity<JsonNode> updated =
        update(
            id,
            """
            {"name":"特典規則IT_編集済み","store_scope_type":"SPECIFIC_STORES","store_ids":[%d],
             "repeat_policy":"ONCE_PER_MEMBER","points":300,"version":%d}
            """
                .formatted(storeId, version));
    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updated.getBody().path("type").asString()).isEqualTo("VISIT");
    assertThat(updated.getBody().path("name").asString()).isEqualTo("特典規則IT_編集済み");

    // 同じ版をもう一度出すのが「開いたままの編集フォーム」の再現
    assertThat(
            update(
                    id,
                    """
                    {"name":"特典規則IT_上書き","store_scope_type":"ALL_STORES",
                     "repeat_policy":"EVERY_TIME","points":400,"version":%d}
                    """
                        .formatted(version))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(get("/platform/benefit-rules/" + id).getBody().path("name").asString())
        .isEqualTo("特典規則IT_編集済み");
  }

  @Test
  @DisplayName("BENEFIT_MANAGE を持たない利用者には規則管理の全端点が 403 を返すこと")
  void nonHolderIsRefusedEverywhere() {
    long id =
        createRule(
            """
            {"name":"特典規則IT_門の検体","type":"VISIT","store_scope_type":"ALL_STORES",
             "repeat_policy":"EVERY_TIME","points":100}
            """);

    assertThat(listRaw(nonHolderToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            rest.exchange(
                    "/platform/benefit-rules/" + id,
                    HttpMethod.GET,
                    new HttpEntity<>(bearer(nonHolderToken)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            rest.exchange(
                    "/platform/benefit-rules",
                    HttpMethod.POST,
                    new HttpEntity<>(
                        """
                        {"name":"特典規則IT_不正","type":"VISIT","store_scope_type":"ALL_STORES",
                         "repeat_policy":"EVERY_TIME","points":100}
                        """,
                        bearerJson(nonHolderToken)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            rest.exchange(
                    "/platform/benefit-rules/" + id + "/deactivation",
                    HttpMethod.POST,
                    new HttpEntity<>(bearer(nonHolderToken)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("点数の形と全店舗ログインの規則は DB の CHECK でも塞がれること")
  void checkConstraintsRejectRowsTheAggregateWouldRefuse() {
    // 紹介以外に紹介の二値を持たせた行
    assertThatThrownBy(
            () ->
                insertRuleRow("特典規則IT_不正な点数", "VISIT", "ALL_STORES", "EVERY_TIME", 100, 1000, 500))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_t_benefit_rules_points_shape");

    // 一人一回限りの紹介規則
    assertThatThrownBy(
            () ->
                insertRuleRow(
                    "特典規則IT_不正な紹介", "REFERRAL", "ALL_STORES", "ONCE_PER_MEMBER", null, 1000, 500))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_t_benefit_rules_referral_every_time");

    // 店舗集合で絞ったログイン規則
    assertThatThrownBy(
            () ->
                insertRuleRow(
                    "特典規則IT_不正なログイン", "LOGIN", "SPECIFIC_STORES", "EVERY_TIME", 100, null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_t_benefit_rules_login_all_stores");
  }

  @Test
  @DisplayName("実在しない店舗を指した作成は 400 になること（外部キー違反の 500 に化けない）")
  void missingStoreReferenceIsRejectedWithBadRequest() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/benefit-rules",
            HttpMethod.POST,
            new HttpEntity<>(
                """
                {"name":"特典規則IT_消えた店舗","type":"VISIT","store_scope_type":"SPECIFIC_STORES",
                 "store_ids":[999999999],"repeat_policy":"EVERY_TIME","points":100}
                """,
                bearerJson(hqToken)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody().path("error").asString()).contains("店舗が見つかりません");
  }

  @Test
  @DisplayName("整数の項目へ届いた小数は切り捨てずに 400 になること")
  void fractionalPointsAreRejected() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/benefit-rules",
            HttpMethod.POST,
            new HttpEntity<>(
                """
                {"name":"特典規則IT_小数","type":"VISIT","store_scope_type":"ALL_STORES",
                 "repeat_policy":"EVERY_TIME","points":1.5}
                """,
                bearerJson(hqToken)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("店舗の削除は規則を道連れにせず、集合表の行だけを落とすこと")
  void storeDeletionEmptiesTheSetWithoutRemovingTheRule() {
    long doomedStoreId =
        storeRepository
            .save(new Store("特典規則IT_消える店舗", "benefit-it-doomed.kizuna.test", null))
            .getId();
    long id =
        createRule(
            """
            {"name":"特典規則IT_店舗集合","type":"VISIT","store_scope_type":"SPECIFIC_STORES",
             "store_ids":[%d],"repeat_policy":"EVERY_TIME","points":100}
            """
                .formatted(doomedStoreId));

    storeRepository.deleteById(doomedStoreId);

    // 規則そのものは残る。集合が空になった規則はどの店舗でも発火しない fail-closed 側へ倒れる。
    assertThat(benefitRuleRepository.findById(id)).isPresent();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_benefit_rule_stores WHERE rule_id = ?", Long.class, id))
        .isZero();
  }

  private void insertRuleRow(
      String name,
      String type,
      String storeScopeType,
      String repeatPolicy,
      Integer points,
      Integer referrerPoints,
      Integer referredPoints) {
    jdbcTemplate.update(
        """
        INSERT INTO t_benefit_rules
          (name, type, store_scope_type, repeat_policy, points, referrer_points, referred_points,
           enabled, created_at, updated_at, version)
        VALUES (?, ?, ?, ?, ?, ?, ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
        """,
        name,
        type,
        storeScopeType,
        repeatPolicy,
        points,
        referrerPoints,
        referredPoints);
  }

  private long createRule(String body) {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/benefit-rules",
            HttpMethod.POST,
            new HttpEntity<>(body, bearerJson(hqToken)),
            JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return res.getBody().path("id").asLong();
  }

  private ResponseEntity<JsonNode> get(String path) {
    ResponseEntity<JsonNode> res =
        rest.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(hqToken)), JsonNode.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    return res;
  }

  private ResponseEntity<JsonNode> update(long id, String body) {
    return rest.exchange(
        "/platform/benefit-rules/" + id,
        HttpMethod.PUT,
        new HttpEntity<>(body, bearerJson(hqToken)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> deactivate(long id) {
    return rest.exchange(
        "/platform/benefit-rules/" + id + "/deactivation",
        HttpMethod.POST,
        new HttpEntity<>(bearer(hqToken)),
        JsonNode.class);
  }

  private ResponseEntity<String> listRaw(String token) {
    return rest.exchange(
        "/platform/benefit-rules?size=50",
        HttpMethod.GET,
        new HttpEntity<>(bearer(token)),
        String.class);
  }

  private void ensureEnabledUser(String email, String displayName) {
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
                            .userType(UserType.STAFF)
                            .roleIds(Set.of(roleRepository.findByName("店長").orElseThrow().getId()))
                            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                            .storeIds(Set.of(storeId))
                            .build()));
    if (!user.getEnabled()) {
      user.resume();
      platformUserRepository.saveAndFlush(user);
    }
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
}
