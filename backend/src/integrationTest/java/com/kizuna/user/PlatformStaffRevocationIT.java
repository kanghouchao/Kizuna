package com.kizuna.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 停止済みスタッフの既発行 JWT を Redis ユーザー単位ブラックリストで即時失効させることを本物の PostgreSQL + Redis で固定する統合テスト。
 *
 * <p>停止・再開の口はアカウント管理（{@code POST /platform/staff-accounts/{id}/suspension} と {@code
 * .../resumption}）で、実行主体は種子の HQ 管理者 admin@kizuna.test（既定授与で STAFF_ACCOUNT_MANAGE を持つ）。対象は店舗側ロールだけの
 * 専用テストユーザーにする — この面が扱うのはロール構成を問わない全 STAFF であり、不減零の母集団にも触れない。
 *
 * <p>スタイルは {@link com.kizuna.auth.PlatformBridgeIT} に倣い、対象ユーザーは repository 直挿の専用テストユーザーのみを使う
 * （種子ユーザー、特に {@code CrossStoreTestSupport} が全面依存する yamada.jiro@kizuna.test を停止すると後続 IT が連鎖破綻するため）。
 *
 * <p>{@code CrossStoreTestSupport} は継承しない。本 IT は店舗文脈（X-Store-ID）を一切使わず、同基底の {@code @BeforeEach}
 * による種子ユーザーログインも不要なため（上記のとおり種子ユーザーには触れない方針）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PlatformStaffRevocationIT {

  private static final String TEST_PASSWORD = "pass";

  /** 種子の HQ 管理者（PERM_STAFF_ACCOUNT_MANAGE 保持、停止操作の実行主体）。 */
  private static final String ADMIN_EMAIL = "admin@kizuna.test";

  private static final String STOP_EMAIL = "revocation-stop@kizuna.test";
  private static final String RESUME_EMAIL = "revocation-resume@kizuna.test";
  private static final String IDEMPOTENT_EMAIL = "revocation-idempotent@kizuna.test";
  private static final String TTL_EMAIL = "revocation-ttl@kizuna.test";
  private static final String NOOP_EMAIL = "revocation-noop@kizuna.test";

  /** 停止・再開の対象に使う店舗側ロール。ROLE_MANAGE を含まないので不減零の母集団を動かさない。 */
  private static final String TARGET_ROLE = "店長";

  /** 授権管理の PUT を撃つ対象に使う HQ 側ロール（管理者管理が扱えるのは HQ 側ロール保持者だけ）。 */
  private static final String HQ_ROLE = "HQ管理者";

  private static final String USER_BLACKLIST_KEY_PREFIX = "blacklist:users:";

  /** {@link com.kizuna.shared.exception.CommonExceptionHandler} の汎用 401 文言と一致する固定値。 */
  private static final String UNAUTHENTICATED_MESSAGE = "認証に失敗しました";

  @Autowired private TestRestTemplate rest;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RedisTemplate<String, Object> redisTemplate;
  @Autowired private AppProperties appProperties;

  /** 各テストで書かれ得るユーザー単位ブラックリストの key を後始末する（テスト間の Redis 状態汚染を防ぐ）。 */
  @AfterEach
  void cleanupRedis() {
    for (String email :
        List.of(ADMIN_EMAIL, STOP_EMAIL, RESUME_EMAIL, IDEMPOTENT_EMAIL, TTL_EMAIL, NOOP_EMAIL)) {
      redisTemplate.delete(USER_BLACKLIST_KEY_PREFIX + email);
    }
  }

  /** 専用テストユーザーを取得または作成する。前回実行の残留（停止済み）状態があれば enabled=true へリセットする。 */
  private PlatformUser ensureEnabledTestUser(String email, String roleName) {
    PlatformUser user =
        platformUserRepository
            .findByEmail(email)
            .orElseGet(
                () ->
                    platformUserRepository.save(
                        PlatformUser.builder()
                            .email(email)
                            .password(passwordEncoder.encode(TEST_PASSWORD))
                            .displayName("失効IT " + email)
                            .enabled(true)
                            .userType(UserType.STAFF)
                            .roleIds(roleIdsOf(roleName))
                            .storeScopeType(StoreScopeType.ALL_STORES)
                            .storeIds(Set.of())
                            .build()));
    if (!user.getEnabled()) {
      user.resume();
      user = platformUserRepository.saveAndFlush(user);
    }
    return user;
  }

  /** 種子の既定束を名称で解決する(束はデータ — id を決め打ちしない)。 */
  private Set<Long> roleIdsOf(String roleName) {
    return Set.of(roleRepository.findByName(roleName).orElseThrow().getId());
  }

  private String rolesJson(String roleName) {
    return "[" + roleRepository.findByName(roleName).orElseThrow().getId() + "]";
  }

  private static String updateBody(
      String roleIdsJson, String scopeType, String storeIds, long version) {
    return String.format(
        "{\"role_ids\":%s,\"store_scope_type\":\"%s\",\"store_ids\":%s,\"version\":%d}",
        roleIdsJson, scopeType, storeIds, version);
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

  private ResponseEntity<String> meWith(String token) {
    return rest.exchange(
        "/platform/me", HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
  }

  private static String errorMessageOf(String body) {
    return new ObjectMapper().readTree(body).path("error").asString();
  }

  private ResponseEntity<String> suspend(String actorToken, long targetId) {
    return rest.exchange(
        "/platform/staff-accounts/" + targetId + "/suspension",
        HttpMethod.POST,
        new HttpEntity<>(bearer(actorToken)),
        String.class);
  }

  private ResponseEntity<String> resume(String actorToken, long targetId) {
    return rest.exchange(
        "/platform/staff-accounts/" + targetId + "/resumption",
        HttpMethod.POST,
        new HttpEntity<>(bearer(actorToken)),
        String.class);
  }

  @Test
  @DisplayName("停止したユーザーの停止前に取得した JWT は GET /platform/me が 401 になること(ユーザー単位ブラックリスト即時反映)")
  void stoppingUserRevokesPreviouslyIssuedToken() {
    PlatformUser target = ensureEnabledTestUser(STOP_EMAIL, TARGET_ROLE);
    String targetToken = platformToken(STOP_EMAIL, TEST_PASSWORD);
    // 正向対照: 停止前は me が読めること(後段の拒否が停止起因である証明)。
    assertThat(meWith(targetToken).getStatusCode()).isEqualTo(HttpStatus.OK);

    String admin = platformToken(ADMIN_EMAIL, TEST_PASSWORD);
    assertThat(suspend(admin, target.getId()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // ブラックリスト済みトークンは decoder の TokenBlacklistValidator が拒否し、resource-server の
    // AuthenticationEntryPoint が 401 で応答する(PlatformBridgeIT のログアウト検証と同じ規約)。
    ResponseEntity<String> stopped = meWith(targetToken);
    assertThat(stopped.getStatusCode())
        .as("停止前に発行された JWT はユーザー単位ブラックリストで即時に拒否されること")
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(errorMessageOf(stopped.getBody())).isEqualTo(UNAUTHENTICATED_MESSAGE);
  }

  @Test
  @DisplayName("再開すると、停止中に拒否されていた同一の旧 JWT が再び 200 になること")
  void resumingUserRevivesPreviouslyIssuedToken() {
    PlatformUser target = ensureEnabledTestUser(RESUME_EMAIL, TARGET_ROLE);
    String targetToken = platformToken(RESUME_EMAIL, TEST_PASSWORD);
    String admin = platformToken(ADMIN_EMAIL, TEST_PASSWORD);

    assertThat(suspend(admin, target.getId()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(meWith(targetToken).getStatusCode())
        .as("前提: 停止直後は拒否されること")
        .isEqualTo(HttpStatus.UNAUTHORIZED);

    assertThat(resume(admin, target.getId()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(meWith(targetToken).getStatusCode())
        .as("再開後は停止前に発行された同一トークンが即時に復活すること")
        .isEqualTo(HttpStatus.OK);
  }

  /**
   * 拒否された停止が失効を書かないことを固定する。
   *
   * <p>旧 PUT 面には「停止と不正な店舗集合を同時送信して stop の後で失敗させる」経路があり、AFTER_COMMIT 相そのものを撃てた。停止専用端点には stop
   * の後で失敗しうる入力が無いため、命題は「守衛に撥ねられた停止は何も書かない」へ弱める。
   */
  @Test
  @DisplayName("自己停止が 400 で拒否されると、ブラックリストは書かれず実行主体のトークンも有効なままであること")
  void rejectedSuspensionDoesNotBlacklistUser() {
    String adminToken = platformToken(ADMIN_EMAIL, TEST_PASSWORD);
    PlatformUser admin = platformUserRepository.findByEmail(ADMIN_EMAIL).orElseThrow();

    assertThat(suspend(adminToken, admin.getId()).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    PlatformUser reloaded = platformUserRepository.findById(admin.getId()).orElseThrow();
    assertThat(reloaded.getEnabled()).as("拒否されたため DB 上は enabled=true のまま").isTrue();
    assertThat(redisTemplate.hasKey(USER_BLACKLIST_KEY_PREFIX + ADMIN_EMAIL))
        .as("停止が成立していないためユーザー単位ブラックリストは書かれないこと")
        .isNotEqualTo(true);
    assertThat(meWith(adminToken).getStatusCode())
        .as("自己停止が拒否されたため管理者自身のトークンは引き続き有効なこと")
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("既に停止済みのユーザーへ停止を再送すると 204 になり、ユーザー単位ブラックリストが再書込されること(冪等)")
  void reSendingStopOnAlreadyStoppedUserRewritesBlacklistKey() {
    PlatformUser target = ensureEnabledTestUser(IDEMPOTENT_EMAIL, TARGET_ROLE);
    String admin = platformToken(ADMIN_EMAIL, TEST_PASSWORD);
    String key = USER_BLACKLIST_KEY_PREFIX + IDEMPOTENT_EMAIL;

    assertThat(suspend(admin, target.getId()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(redisTemplate.hasKey(key)).as("前提: 1 回目の停止でキーが書かれること").isEqualTo(true);

    redisTemplate.delete(key);
    assertThat(redisTemplate.hasKey(key)).as("前提: 手動削除でキーが消えていること").isNotEqualTo(true);

    assertThat(suspend(admin, target.getId()).getStatusCode())
        .as("既に停止済みの対象への再送も 204 で受理されること(結果語義の冪等性)")
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(redisTemplate.hasKey(key)).as("再送によりユーザー単位ブラックリストが再書込されること").isEqualTo(true);
  }

  @Test
  @DisplayName("内容が同一の授権更新でも version が増えること（陳腐な更新がコミットできない前提の実証）")
  void noOpUpdateStillBumpsVersion() {
    PlatformUser target = ensureEnabledTestUser(NOOP_EMAIL, HQ_ROLE);
    String admin = platformToken(ADMIN_EMAIL, TEST_PASSWORD);

    // 1 回目: 束も店舗集合も現状と同一の payload を送る（実質 no-op）。
    ResponseEntity<JsonNode> first = putGrants(admin, target.getId(), target.getVersion());
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    long afterFirst = first.getBody().path("version").asLong();

    // no-op でも version が進む = version 述語つき UPDATE が発行されている。これが成り立つ限り、
    // 停止を知らずに読んだ陳腐なスナップショットからの更新は、停止が先にコミットした時点で
    // 楽観ロック違反となりコミットできない（＝失効とブラックリストの食い違いが構造的に起きない）。
    assertThat(afterFirst).as("内容同一でも version は増える").isEqualTo(target.getVersion() + 1);

    // 進んだ version により、古い version を持つ要求は 409 で弾かれる。
    assertThat(putGrants(admin, target.getId(), target.getVersion()).getStatusCode())
        .as("陳腐な version の要求は 409 で弾かれること")
        .isEqualTo(HttpStatus.CONFLICT);
  }

  private ResponseEntity<JsonNode> putGrants(String actorToken, long targetId, long version) {
    return rest.exchange(
        "/platform/staff/" + targetId,
        HttpMethod.PUT,
        new HttpEntity<>(
            updateBody(rolesJson(HQ_ROLE), "ALL_STORES", "[]", version), bearerJson(actorToken)),
        JsonNode.class);
  }

  @Test
  @DisplayName("停止時に書き込まれるユーザー単位ブラックリストの TTL が JWT 有効期間(app.jwt.expiration)と一致すること")
  void blacklistKeyTtlMatchesJwtExpiration() {
    PlatformUser target = ensureEnabledTestUser(TTL_EMAIL, TARGET_ROLE);
    String admin = platformToken(ADMIN_EMAIL, TEST_PASSWORD);

    assertThat(suspend(admin, target.getId()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    Long ttlMillis =
        redisTemplate.getExpire(USER_BLACKLIST_KEY_PREFIX + TTL_EMAIL, TimeUnit.MILLISECONDS);
    // 下界も固定する: 上界だけだと TTL を 1 秒に誤設定しても緑のままで、AC「JWT 有効期間と一致」を
    // 守れない。実 Redis の TTL は書き込み直後から減るため、テスト実行ぶんの余裕を引いた値を下界に採る。
    long expiration = appProperties.getJwtExpiration();
    assertThat(ttlMillis).isNotNull();
    assertThat(ttlMillis).isLessThanOrEqualTo(expiration);
    assertThat(ttlMillis).isGreaterThan(expiration - 60_000);
  }
}
