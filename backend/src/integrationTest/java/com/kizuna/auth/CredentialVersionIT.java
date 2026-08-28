package com.kizuna.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.auth.infrastructure.CredentialVersionService;
import com.kizuna.auth.infrastructure.PlatformJwtIssuer;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 資格情報の版によるセッション失効（ADR 0022）のうち、auth モジュール側の面を本物の PostgreSQL + Redis で固定する統合テスト: 自助パスワード変更（{@code
 * PUT /platform/me/password}）の全端末失効・版 claim 欠落トークンの拒否・版キャッシュの単調書込みの収束。 代理再設定・停止・再開の発火点は {@code
 * PlatformStaffPasswordResetIT} / {@code PlatformStaffRevocationIT} が固定する。
 *
 * <p>対象は repository 直挿の専用テストユーザーのみを使う（種子ユーザーの資格情報を書き換えると後続 IT が連鎖破綻するため）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class CredentialVersionIT {

  private static final String TEST_PASSWORD = "pass";

  /** 変更後パスワードのダミー値。実物と同形の字面はシークレット走査が誤検知するため、明示的な placeholder にする。 */
  private static final String NEW_PASSWORD = "dummy-placeholder-password";

  private static final String CHANGE_EMAIL = "credver-change@kizuna.test";
  private static final String CLAIMLESS_EMAIL = "credver-claimless@kizuna.test";
  private static final String MONOTONIC_EMAIL = "credver-monotonic@kizuna.test";

  /** 対象に使う店舗側ロール（権限の中身は本 IT の関心外 — authorities claim が空でなければよい）。 */
  private static final String TARGET_ROLE = "店長";

  private static final String CREDENTIAL_VERSION_KEY_PREFIX = "credential-version:";

  /** {@link com.kizuna.shared.exception.CommonExceptionHandler} の汎用 401 文言と一致する固定値。 */
  private static final String UNAUTHENTICATED_MESSAGE = "認証に失敗しました";

  @Autowired private TestRestTemplate rest;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RedisTemplate<String, Object> redisTemplate;
  @Autowired private CredentialVersionService credentialVersionService;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private AppProperties appProperties;

  /** 版キャッシュの key を後始末する（テスト間の Redis 状態汚染を防ぐ。次のアクセスは DB から埋め戻される）。 */
  @AfterEach
  void cleanupRedis() {
    for (String email : List.of(CHANGE_EMAIL, CLAIMLESS_EMAIL, MONOTONIC_EMAIL)) {
      redisTemplate.delete(CREDENTIAL_VERSION_KEY_PREFIX + email);
    }
  }

  /** 専用テストユーザーを取得または作成し、パスワードを毎回初期値へ戻す（本 IT は対象のパスワードを書き換えるため）。 */
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
                            .displayName("版IT " + email)
                            .enabled(true)
                            .userType(UserType.STAFF)
                            .roleIds(
                                Set.of(
                                    roleRepository.findByName(TARGET_ROLE).orElseThrow().getId()))
                            .storeScopeType(StoreScopeType.ALL_STORES)
                            .storeIds(Set.of())
                            .build()));
    user.changePassword(passwordEncoder.encode(TEST_PASSWORD));
    return platformUserRepository.saveAndFlush(user);
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

  private ResponseEntity<String> changePassword(
      String token, String currentPassword, String newPassword) {
    return rest.exchange(
        "/platform/me/password",
        HttpMethod.PUT,
        new HttpEntity<>(
            String.format(
                "{\"current_password\": \"%s\", \"new_password\": \"%s\"}",
                currentPassword, newPassword),
            bearerJson(token)),
        String.class);
  }

  @Test
  @DisplayName("自助パスワード変更で当該トークンを含む全端末のセッションが即時に失効し、新パスワードでのログインは通ること")
  void selfPasswordChangeRevokesAllDevicesIncludingTheChangingOne() {
    ensureResettableTestUser(CHANGE_EMAIL);
    // 2 端末を模す: 同一利用者の独立した 2 トークン。
    String deviceA = platformToken(CHANGE_EMAIL, TEST_PASSWORD);
    String deviceB = platformToken(CHANGE_EMAIL, TEST_PASSWORD);
    assertThat(meWith(deviceA).getStatusCode()).as("前提: 変更前は me が読めること").isEqualTo(HttpStatus.OK);
    assertThat(meWith(deviceB).getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(changePassword(deviceA, TEST_PASSWORD, NEW_PASSWORD).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    // 版照合は時刻を解釈しないため、断言前の sleep なしで即時に不一致になる。
    assertThat(meWith(deviceA).getStatusCode())
        .as("変更を行った端末自身のトークンも失効すること")
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(meWith(deviceB).getStatusCode())
        .as("他端末のトークンも失効すること")
        .isEqualTo(HttpStatus.UNAUTHORIZED);

    // 変更と同一秒に発行される新トークンも受理される（判定が時刻でなく版の相等比較であることの実証）。
    assertThat(meWith(platformToken(CHANGE_EMAIL, NEW_PASSWORD)).getStatusCode())
        .as("新パスワードで得た新しいトークンは通ること")
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("資格情報の版 claim を欠くトークンは、正規署名でも 401 で拒否されること（移行期なし）")
  void tokenWithoutCredentialVersionClaimIsRejected() {
    ensureResettableTestUser(CLAIMLESS_EMAIL);

    // 正向対照: 同一の組み立てで claim だけを載せたトークンは通る。これにより後段の 401 の拒否理由が
    // issuer・署名・期限ではなく claim 欠落そのものであることを固定する。
    long currentVersion =
        platformUserRepository.findCredentialVersionByEmail(CLAIMLESS_EMAIL).orElseThrow();
    assertThat(meWith(handcraftedToken(CLAIMLESS_EMAIL, currentVersion)).getStatusCode())
        .as("前提: claim を載せた同型トークンは通ること")
        .isEqualTo(HttpStatus.OK);

    ResponseEntity<String> res = meWith(handcraftedToken(CLAIMLESS_EMAIL, null));
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(new ObjectMapper().readTree(res.getBody()).path("error").asString())
        .isEqualTo(UNAUTHENTICATED_MESSAGE);
  }

  /** 実サーバーと同一の encoder・issuer・有効期限でトークンを組み立てる（credentialVersion だけを可変にする）。 */
  private String handcraftedToken(String email, Long credentialVersion) {
    Instant now = Instant.now();
    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder()
            .issuer(PlatformJwtIssuer.ISSUER_PLATFORM)
            .subject(email)
            .issuedAt(now)
            .expiresAt(now.plusMillis(appProperties.getJwtExpiration()))
            .claim("authorities", List.of("PERM_TEST"));
    if (credentialVersion != null) {
      claims.claim(CredentialVersionService.CLAIM, credentialVersion);
    }
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
  }

  @Test
  @DisplayName("版キャッシュの書込みは単調で、遅れて届いた古い版が新しい版を巻き戻さないこと")
  void credentialVersionCacheWriteIsMonotonic() {
    // 増分の反映と miss の埋め戻しは commit 順と到着順が一致しない。素の SET なら本テストは古い版へ巻き戻って赤になる。
    String key = CREDENTIAL_VERSION_KEY_PREFIX + MONOTONIC_EMAIL;

    credentialVersionService.reflect(MONOTONIC_EMAIL, 5L);
    // 増分（5）より前に DB から読まれた旧版（4）の埋め戻しが、増分の反映より後に到着したケース。
    credentialVersionService.reflect(MONOTONIC_EMAIL, 4L);

    assertThat(redisTemplate.opsForValue().get(key)).as("より新しい版が残ること").isEqualTo("5");
  }
}
