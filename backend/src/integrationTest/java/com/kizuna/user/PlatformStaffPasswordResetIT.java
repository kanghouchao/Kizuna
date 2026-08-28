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

/**
 * HQ 管理者によるパスワード再設定（{@code POST /platform/staff-accounts/{id}/password-reset}）を本物の PostgreSQL +
 * Redis で固定する統合テスト。実行主体は種子の HQ 管理者 admin@kizuna.test。失効は資格情報の版の照合（ADR 0022）で
 * 時刻の解釈を伴わないため、断言前に秒境界を跨ぐ sleep を置く必要は無い。
 *
 * <p>対象は repository 直挿の専用テストユーザーのみを使う（種子ユーザーの資格情報を書き換えると後続 IT が連鎖破綻するため）。{@link
 * PlatformStaffRevocationIT} と同じ理由で {@code CrossStoreTestSupport} は継承しない。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PlatformStaffPasswordResetIT {

  private static final String TEST_PASSWORD = "pass";

  /** 種子の HQ 管理者（PERM_STAFF_ACCOUNT_MANAGE 保持、再設定操作の実行主体）。 */
  private static final String ADMIN_EMAIL = "admin@kizuna.test";

  private static final String RESET_EMAIL = "pwreset-target@kizuna.test";
  private static final String REVOKE_EMAIL = "pwreset-revoke@kizuna.test";
  private static final String RESUME_EMAIL = "pwreset-resume@kizuna.test";
  private static final String SECRET_EMAIL = "pwreset-secret@kizuna.test";
  private static final String TTL_EMAIL = "pwreset-ttl@kizuna.test";

  /** 再設定の対象に使う店舗側ロール。HQ 側の権限を含まないので守衛 G6 に掛からない。 */
  private static final String TARGET_ROLE = "店長";

  private static final String CREDENTIAL_VERSION_KEY_PREFIX = "credential-version:";

  @Autowired private TestRestTemplate rest;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RedisTemplate<String, Object> redisTemplate;
  @Autowired private AppProperties appProperties;

  /** 各テストで書かれ得る版キャッシュの key を後始末する（テスト間の Redis 状態汚染を防ぐ。次のアクセスは DB から埋め戻される）。 */
  @AfterEach
  void cleanupRedis() {
    for (String email :
        List.of(ADMIN_EMAIL, RESET_EMAIL, REVOKE_EMAIL, RESUME_EMAIL, SECRET_EMAIL, TTL_EMAIL)) {
      redisTemplate.delete(CREDENTIAL_VERSION_KEY_PREFIX + email);
    }
  }

  /**
   * 専用テストユーザーを取得または作成する。前回実行が残した停止状態とパスワードを毎回初期値へ戻す — 本 IT は対象のパスワードを書き換えるため、戻さないと 2
   * 回目以降の実行が前提のログインで落ちる。
   */
  private PlatformUser ensureResettableTestUser(String email) {
    PlatformUser user =
        platformUserRepository
            .findByEmail(email)
            .orElseGet(
                () ->
                    platformUserRepository.save(
                        PlatformUser.builder()
                            .email(email)
                            .password(passwordEncoder.encode(TEST_PASSWORD))
                            .displayName("再設定IT " + email)
                            .enabled(true)
                            .userType(UserType.STAFF)
                            .roleIds(roleIdsOf(TARGET_ROLE))
                            .storeScopeType(StoreScopeType.ALL_STORES)
                            .storeIds(Set.of())
                            .build()));
    if (!user.getEnabled()) {
      user.resume();
    }
    user.changePassword(passwordEncoder.encode(TEST_PASSWORD));
    return platformUserRepository.saveAndFlush(user);
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
    String token = res.getBody().path("token").asString();
    assertThat(token).isNotBlank();
    return token;
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

  private ResponseEntity<String> meWith(String token) {
    return rest.exchange(
        "/platform/me", HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
  }

  private ResponseEntity<JsonNode> resetPassword(String actorToken, long targetId) {
    return rest.exchange(
        "/platform/staff-accounts/" + targetId + "/password-reset",
        HttpMethod.POST,
        new HttpEntity<>(bearer(actorToken)),
        JsonNode.class);
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

  private static String temporaryPasswordOf(ResponseEntity<JsonNode> response) {
    String temporary = response.getBody().path("temporary_password").asString();
    assertThat(temporary).as("仮パスワードの生値が snake_case のキーで返ること").isNotBlank();
    return temporary;
  }

  @Test
  @DisplayName("店舗側ロールだけの対象は再設定に成功し、返された仮パスワードでログインして /platform/me が読めること")
  void resetIssuesATemporaryPasswordThatCanLogIn() {
    PlatformUser target = ensureResettableTestUser(RESET_EMAIL);
    String admin = platformToken(ADMIN_EMAIL, TEST_PASSWORD);

    ResponseEntity<JsonNode> response = resetPassword(admin, target.getId());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String temporary = temporaryPasswordOf(response);

    // 再設定と同じ秒に発行される token も受理される（判定が時刻でなく版の相等比較であることの実証）。
    String newToken = platformToken(RESET_EMAIL, temporary);
    assertThat(meWith(newToken).getStatusCode())
        .as("仮パスワードで得た新しいトークンは通ること")
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("再設定前に発行された JWT は再設定直後に 401 になること（同一秒でも sleep なしで即時）")
  void resetRevokesPreviouslyIssuedTokens() {
    PlatformUser target = ensureResettableTestUser(REVOKE_EMAIL);
    String oldToken = platformToken(REVOKE_EMAIL, TEST_PASSWORD);
    assertThat(meWith(oldToken).getStatusCode()).as("前提: 再設定前は me が読めること").isEqualTo(HttpStatus.OK);

    String admin = platformToken(ADMIN_EMAIL, TEST_PASSWORD);
    assertThat(resetPassword(admin, target.getId()).getStatusCode()).isEqualTo(HttpStatus.OK);

    // 版照合は時刻を解釈しないため、同一秒に発行された旧トークンも sleep なしで即時に不一致になる。
    assertThat(meWith(oldToken).getStatusCode())
        .as("再設定前に発行された JWT は即時に拒否されること")
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  /** 再開が失効機構に何もしないことを再設定と組み合わせて固定する。停止で増えた版も再設定で増えた版も、再開では戻らない。 */
  @Test
  @DisplayName("再設定後に停止・再開しても、再設定前のトークンは 401 のままであること（再開は版を戻さない）")
  void resumingAfterResetDoesNotReviveThePreResetToken() {
    PlatformUser target = ensureResettableTestUser(RESUME_EMAIL);
    String oldToken = platformToken(RESUME_EMAIL, TEST_PASSWORD);
    assertThat(meWith(oldToken).getStatusCode()).as("前提: 再設定前は me が読めること").isEqualTo(HttpStatus.OK);

    String admin = platformToken(ADMIN_EMAIL, TEST_PASSWORD);
    String temporary = temporaryPasswordOf(resetPassword(admin, target.getId()));

    assertThat(suspend(admin, target.getId()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(resume(admin, target.getId()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(meWith(oldToken).getStatusCode())
        .as("再開は版を戻さない — 再設定前のトークンは失効したままであること")
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    // 対照: 再開後の対象自身は仮パスワードで正常にログインできる（失効が過剰に効いていない）。
    assertThat(meWith(platformToken(RESUME_EMAIL, temporary)).getStatusCode())
        .as("再開後は仮パスワードでの新規ログインが通ること")
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("HQ 側ロール保持者への再設定は 400 になり、店舗側ロールの対象は 200 になること（境界 G6 の両側）")
  void hqRoleHolderIsRejectedWhileStoreSideTargetSucceeds() {
    String admin = platformToken(ADMIN_EMAIL, TEST_PASSWORD);
    PlatformUser hqAdmin = platformUserRepository.findByEmail(ADMIN_EMAIL).orElseThrow();

    assertThat(resetPassword(admin, hqAdmin.getId()).getStatusCode())
        .as("HQ 側ロール保持者は再設定できないこと")
        .isEqualTo(HttpStatus.BAD_REQUEST);

    PlatformUser storeSide = ensureResettableTestUser(RESET_EMAIL);
    assertThat(resetPassword(admin, storeSide.getId()).getStatusCode())
        .as("同じ操作が店舗側ロールの対象には通ること（守衛が再設定そのものを塞いでいない対照）")
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("実行主体が自分自身へ再設定すると 400 になり、自分のトークンも有効なままであること")
  void selfResetIsRejected() {
    String adminToken = platformToken(ADMIN_EMAIL, TEST_PASSWORD);
    PlatformUser admin = platformUserRepository.findByEmail(ADMIN_EMAIL).orElseThrow();

    // 自己再設定は G6 に委ねず名指しで拒む（JWT の権限は写しで、降格後の残存セッションが G6 を素通りするため）。
    assertThat(resetPassword(adminToken, admin.getId()).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    assertThat(platformUserRepository.findCredentialVersionByEmail(ADMIN_EMAIL).orElseThrow())
        .as("拒否されたため資格情報の版は増えないこと")
        .isEqualTo(admin.getCredentialVersion());
    assertThat(meWith(adminToken).getStatusCode())
        .as("自己再設定が拒否されたため管理者自身のトークンは引き続き有効なこと")
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("仮パスワードの生値は応答一度きりで、詳細にも DB にも平文で残らないこと")
  void temporaryPasswordIsNotRetrievableAfterTheResponse() {
    PlatformUser target = ensureResettableTestUser(SECRET_EMAIL);
    String admin = platformToken(ADMIN_EMAIL, TEST_PASSWORD);

    String temporary = temporaryPasswordOf(resetPassword(admin, target.getId()));

    ResponseEntity<String> detail =
        rest.exchange(
            "/platform/staff-accounts/" + target.getId(),
            HttpMethod.GET,
            new HttpEntity<>(bearer(admin)),
            String.class);
    assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(detail.getBody()).as("詳細の応答本文に生値が現れないこと").doesNotContain(temporary);

    String stored = platformUserRepository.findByEmail(SECRET_EMAIL).orElseThrow().getPassword();
    assertThat(stored).as("保存値は bcrypt ハッシュであること").startsWith("$2");
    assertThat(stored).as("保存値が平文でないこと").isNotEqualTo(temporary);
  }

  @Test
  @DisplayName("再設定で書き込まれる版キャッシュの TTL が JWT 有効期間(app.jwt.expiration)と一致すること")
  void credentialVersionCacheTtlMatchesJwtExpiration() {
    PlatformUser target = ensureResettableTestUser(TTL_EMAIL);
    String admin = platformToken(ADMIN_EMAIL, TEST_PASSWORD);

    assertThat(resetPassword(admin, target.getId()).getStatusCode()).isEqualTo(HttpStatus.OK);

    Long ttlMillis =
        redisTemplate.getExpire(CREDENTIAL_VERSION_KEY_PREFIX + TTL_EMAIL, TimeUnit.MILLISECONDS);
    // 下界も固定する: 上界だけだと TTL を 1 秒に誤設定しても緑のままで、AC「TTL = JWT 有効期間」を守れない。
    long expiration = appProperties.getJwtExpiration();
    assertThat(ttlMillis).isNotNull();
    assertThat(ttlMillis).isLessThanOrEqualTo(expiration);
    assertThat(ttlMillis).isGreaterThan(expiration - 60_000);
  }
}
