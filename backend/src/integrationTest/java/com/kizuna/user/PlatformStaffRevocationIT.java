package com.kizuna.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.config.AppProperties;
import com.kizuna.user.domain.CapabilityBundleRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
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

/**
 * 停止済みスタッフの既発行 JWT を Redis ユーザー単位ブラックリストで即時失効させることを本物の PostgreSQL + Redis で固定する統合テスト。
 *
 * <p>スタイルは {@link com.kizuna.auth.PlatformBridgeIT} に倣い、対象ユーザーは repository 直挿の専用テストユーザーのみを使う
 * （種子ユーザー、特に {@code CrossStoreTestSupport} が全面依存する yamada.jiro@kizuna.test を停止すると後続 IT が連鎖破綻するため）。
 * 実行主体は種子の HQ 管理者 admin@kizuna.test（PERM_STAFF_MANAGE 保持）を使う。
 *
 * <p>{@code CrossStoreTestSupport} は継承しない。本 IT は店舗文脈（X-Store-ID）を一切使わず、同基底の {@code @BeforeEach}
 * による種子ユーザーログインも不要なため（上記のとおり種子ユーザーには触れない方針）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PlatformStaffRevocationIT {

  private static final String TEST_PASSWORD = "pass";

  /** 種子の HQ 管理者（PERM_STAFF_MANAGE 保持、停止操作の実行主体）。 */
  private static final String ADMIN_EMAIL = "admin@kizuna.test";

  private static final String STOP_EMAIL = "revocation-stop@kizuna.test";
  private static final String RESUME_EMAIL = "revocation-resume@kizuna.test";
  private static final String ROLLBACK_EMAIL = "revocation-rollback@kizuna.test";
  private static final String IDEMPOTENT_EMAIL = "revocation-idempotent@kizuna.test";
  private static final String TTL_EMAIL = "revocation-ttl@kizuna.test";
  private static final String NOOP_EMAIL = "revocation-noop@kizuna.test";

  private static final String USER_BLACKLIST_KEY_PREFIX = "blacklist:users:";

  @Autowired private TestRestTemplate rest;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private CapabilityBundleRepository capabilityBundleRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RedisTemplate<String, Object> redisTemplate;
  @Autowired private AppProperties appProperties;

  /** 各テストで書かれ得るユーザー単位ブラックリストの key を後始末する（テスト間の Redis 状態汚染を防ぐ）。 */
  @AfterEach
  void cleanupRedis() {
    for (String email :
        List.of(
            ADMIN_EMAIL,
            STOP_EMAIL,
            RESUME_EMAIL,
            ROLLBACK_EMAIL,
            IDEMPOTENT_EMAIL,
            TTL_EMAIL,
            NOOP_EMAIL)) {
      redisTemplate.delete(USER_BLACKLIST_KEY_PREFIX + email);
    }
  }

  /** 専用テストユーザーを取得または作成する。前回実行の残留（停止済み）状態があれば enabled=true へリセットする。 */
  private PlatformUser ensureEnabledTestUser(String email, String bundleName) {
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
                            .bundleIds(bundleIdsOf(bundleName))
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
  private Set<Long> bundleIdsOf(String bundleName) {
    return Set.of(capabilityBundleRepository.findByName(bundleName).orElseThrow().getId());
  }

  private String bundlesJson(String bundleName) {
    return "[" + capabilityBundleRepository.findByName(bundleName).orElseThrow().getId() + "]";
  }

  private static String updateBody(
      String bundleIdsJson, String scopeType, String storeIds, boolean enabled, long version) {
    return String.format(
        "{\"bundle_ids\":%s,\"store_scope_type\":\"%s\",\"store_ids\":%s,\"enabled\":%b,"
            + "\"version\":%d}",
        bundleIdsJson, scopeType, storeIds, enabled, version);
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

  private ResponseEntity<JsonNode> putEnabled(
      String actorToken, long targetId, boolean enabled, long version) {
    return rest.exchange(
        "/platform/staff/" + targetId,
        HttpMethod.PUT,
        new HttpEntity<>(
            updateBody(bundlesJson("店舗スタッフ"), "ALL_STORES", "[]", enabled, version),
            bearerJson(actorToken)),
        JsonNode.class);
  }

  @Test
  @DisplayName("停止したユーザーの停止前に取得した JWT は GET /platform/me が 401 になること(ユーザー単位ブラックリスト即時反映)")
  void stoppingUserRevokesPreviouslyIssuedToken() {
    PlatformUser target = ensureEnabledTestUser(STOP_EMAIL, "店舗スタッフ");
    String targetToken = platformToken(STOP_EMAIL, TEST_PASSWORD);
    // 正向対照: 停止前は me が読めること(後段の拒否が停止起因である証明)。
    assertThat(meWith(targetToken).getStatusCode()).isEqualTo(HttpStatus.OK);

    String admin = platformToken(ADMIN_EMAIL, TEST_PASSWORD);
    ResponseEntity<JsonNode> stop = putEnabled(admin, target.getId(), false, target.getVersion());
    assertThat(stop.getStatusCode()).isEqualTo(HttpStatus.OK);

    // ブラックリスト済みトークンは decoder の TokenBlacklistValidator が拒否し、resource-server の
    // AuthenticationEntryPoint が 401 で応答する(PlatformBridgeIT のログアウト検証と同じ規約)。
    assertThat(meWith(targetToken).getStatusCode())
        .as("停止前に発行された JWT はユーザー単位ブラックリストで即時に拒否されること")
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("再開すると、停止中に拒否されていた同一の旧 JWT が再び 200 になること")
  void resumingUserRevivesPreviouslyIssuedToken() {
    PlatformUser target = ensureEnabledTestUser(RESUME_EMAIL, "店舗スタッフ");
    String targetToken = platformToken(RESUME_EMAIL, TEST_PASSWORD);
    String admin = platformToken(ADMIN_EMAIL, TEST_PASSWORD);

    ResponseEntity<JsonNode> stop = putEnabled(admin, target.getId(), false, target.getVersion());
    assertThat(stop.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(meWith(targetToken).getStatusCode())
        .as("前提: 停止直後は拒否されること")
        .isEqualTo(HttpStatus.UNAUTHORIZED);

    long stoppedVersion = stop.getBody().path("version").asLong();
    ResponseEntity<JsonNode> resume = putEnabled(admin, target.getId(), true, stoppedVersion);
    assertThat(resume.getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(meWith(targetToken).getStatusCode())
        .as("再開後は停止前に発行された同一トークンが即時に復活すること")
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName(
      "enabled=false と存在しない store_id を同時送信した更新が 400 でロールバックされ、" + "失効も反映されないこと(AFTER_COMMIT 相の証明)")
  void rollbackOnInvalidStoreDoesNotBlacklistUser() {
    PlatformUser target = ensureEnabledTestUser(ROLLBACK_EMAIL, "店長");
    String targetToken = platformToken(ROLLBACK_EMAIL, TEST_PASSWORD);
    String admin = platformToken(ADMIN_EMAIL, TEST_PASSWORD);

    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/staff/" + target.getId(),
            HttpMethod.PUT,
            new HttpEntity<>(
                updateBody(
                    bundlesJson("店長"), "SPECIFIC_STORES", "[999999]", false, target.getVersion()),
                bearerJson(admin)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    PlatformUser reloaded = platformUserRepository.findById(target.getId()).orElseThrow();
    assertThat(reloaded.getEnabled()).as("ロールバックにより DB 上は enabled=true のまま").isTrue();
    assertThat(redisTemplate.hasKey(USER_BLACKLIST_KEY_PREFIX + ROLLBACK_EMAIL))
        .as("commit が失敗したためユーザー単位ブラックリストは書かれないこと(AFTER_COMMIT)")
        .isNotEqualTo(true);
    assertThat(meWith(targetToken).getStatusCode())
        .as("ロールバックされたため旧トークンは引き続き有効なこと")
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("管理者が自分自身を enabled=false にしようとすると 400 で拒否され、自分のトークンは有効なままであること")
  void selfStopIsRejectedAndAdminTokenStaysValid() {
    String adminToken = platformToken(ADMIN_EMAIL, TEST_PASSWORD);
    PlatformUser admin = platformUserRepository.findByEmail(ADMIN_EMAIL).orElseThrow();

    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/staff/" + admin.getId(),
            HttpMethod.PUT,
            new HttpEntity<>(
                updateBody(bundlesJson("HQ管理者"), "ALL_STORES", "[]", false, admin.getVersion()),
                bearerJson(adminToken)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(meWith(adminToken).getStatusCode())
        .as("自己停止が拒否されたため管理者自身のトークンは引き続き有効なこと")
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("既に停止済みのユーザーへ enabled=false を再送すると 200 になり、ユーザー単位ブラックリストが再書込されること(冪等)")
  void reSendingStopOnAlreadyStoppedUserRewritesBlacklistKey() {
    PlatformUser target = ensureEnabledTestUser(IDEMPOTENT_EMAIL, "店舗スタッフ");
    String admin = platformToken(ADMIN_EMAIL, TEST_PASSWORD);
    String key = USER_BLACKLIST_KEY_PREFIX + IDEMPOTENT_EMAIL;

    ResponseEntity<JsonNode> firstStop =
        putEnabled(admin, target.getId(), false, target.getVersion());
    assertThat(firstStop.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(redisTemplate.hasKey(key)).as("前提: 1 回目の停止でキーが書かれること").isEqualTo(true);

    redisTemplate.delete(key);
    assertThat(redisTemplate.hasKey(key)).as("前提: 手動削除でキーが消えていること").isNotEqualTo(true);

    long stoppedVersion = firstStop.getBody().path("version").asLong();
    ResponseEntity<JsonNode> secondStop = putEnabled(admin, target.getId(), false, stoppedVersion);

    assertThat(secondStop.getStatusCode())
        .as("既に停止済みの対象への再送も 200 で受理されること(結果語義の冪等性)")
        .isEqualTo(HttpStatus.OK);
    assertThat(redisTemplate.hasKey(key)).as("再送によりユーザー単位ブラックリストが再書込されること").isEqualTo(true);
  }

  @Test
  @DisplayName("内容が同一の更新でも version が増えること（陳腐な更新がコミットできない前提の実証）")
  void noOpUpdateStillBumpsVersion() {
    PlatformUser target = ensureEnabledTestUser(NOOP_EMAIL, "店舗スタッフ");
    String admin = platformToken(ADMIN_EMAIL, TEST_PASSWORD);

    // 1 回目: enabled も束も店舗集合も現状と同一の payload を送る（実質 no-op）。
    ResponseEntity<JsonNode> first = putEnabled(admin, target.getId(), true, target.getVersion());
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    long afterFirst = first.getBody().path("version").asLong();

    // no-op でも version が進む = version 述語つき UPDATE が発行されている。これが成り立つ限り、
    // 停止を知らずに読んだ陳腐なスナップショットからの更新は、停止が先にコミットした時点で
    // 楽観ロック違反となりコミットできない（＝失効とブラックリストの食い違いが構造的に起きない）。
    assertThat(afterFirst).as("内容同一でも version は増える").isEqualTo(target.getVersion() + 1);

    // 進んだ version により、古い version を持つ要求は 409 で弾かれる。
    ResponseEntity<JsonNode> stale = putEnabled(admin, target.getId(), true, target.getVersion());
    assertThat(stale.getStatusCode())
        .as("陳腐な version の要求は 409 で弾かれること")
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  @DisplayName("停止時に書き込まれるユーザー単位ブラックリストの TTL が JWT 有効期間(app.jwt.expiration)と一致すること")
  void blacklistKeyTtlMatchesJwtExpiration() {
    PlatformUser target = ensureEnabledTestUser(TTL_EMAIL, "店舗スタッフ");
    String admin = platformToken(ADMIN_EMAIL, TEST_PASSWORD);

    ResponseEntity<JsonNode> stop = putEnabled(admin, target.getId(), false, target.getVersion());
    assertThat(stop.getStatusCode()).isEqualTo(HttpStatus.OK);

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
