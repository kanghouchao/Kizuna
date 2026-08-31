package com.kizuna.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.auth.infrastructure.CredentialVersionService;
import com.kizuna.auth.infrastructure.PlatformJwtIssuer;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.user.domain.EmergencyElevation;
import com.kizuna.user.domain.EmergencyElevationRepository;
import com.kizuna.user.domain.EmergencyElevationStatus;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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

/**
 * 緊急昇格（発動・昇格トークン・撤回）を本物の PostgreSQL + Redis + JWT 検証の噛み合わせで固定する統合テスト。 種子ユーザーの資格情報は書き換えない（後続 IT
 * が連鎖破綻する）— repository 直挿の専用ユーザーのみを使い、 資格情報の版を動かすケースは 1 ケース 1 ユーザーで分ける。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class EmergencyElevationIT {

  private static final String PASSWORD = "pass";
  private static final String WRONG_PASSWORD = "dummy-placeholder-password";

  private static final long STORE_A = 1L;
  private static final long STORE_B = 2L;

  /** 実在しない店舗 id（種子・他 IT の採番と衝突しない大きな値）。 */
  private static final long MISSING_STORE = 9_999_999L;

  private static final String HQ_ROLE = "HQ管理者";
  private static final String STORE_STAFF_ROLE = "店舗スタッフ";

  /** 版を動かさないケース（発動の成否・トークンの射程）が共有する保持者。 */
  private static final String HOLDER_EMAIL = "elevation-holder@kizuna.test";

  private static final String NON_HOLDER_EMAIL = "elevation-nonholder@kizuna.test";
  private static final String REVOKE_EMAIL = "elevation-revoke@kizuna.test";
  private static final String REACTIVATE_EMAIL = "elevation-reactivate@kizuna.test";
  private static final String CROSS_ACTIVATOR_EMAIL = "elevation-cross-activator@kizuna.test";
  private static final String CROSS_REVOKER_EMAIL = "elevation-cross-revoker@kizuna.test";

  private static final List<String> ALL_EMAILS =
      List.of(
          HOLDER_EMAIL,
          NON_HOLDER_EMAIL,
          REVOKE_EMAIL,
          REACTIVATE_EMAIL,
          CROSS_ACTIVATOR_EMAIL,
          CROSS_REVOKER_EMAIL);

  private static final String CREDENTIAL_VERSION_KEY_PREFIX = "credential-version:";

  /** {@link com.kizuna.shared.exception.CommonExceptionHandler} の固定文言。 */
  private static final String UNAUTHENTICATED_MESSAGE = "認証に失敗しました";

  private static final String BAD_CREDENTIALS_MESSAGE = "メールアドレスまたはパスワードが正しくありません";

  /** {@link EmergencyElevation#revoke} のドメイン例外文言（400 の出所が守衛であることの証明に使う）。 */
  private static final String NOT_REVOCABLE_MESSAGE = "この緊急昇格は撤回できる状態ではありません";

  private static final String MISSING_STORE_MESSAGE = "指定された店舗が見つかりません";

  @Autowired private TestRestTemplate rest;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private EmergencyElevationRepository elevationRepository;
  @Autowired private CastRepository castRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RedisTemplate<String, Object> redisTemplate;
  @Autowired private JwtEncoder jwtEncoder;

  /** 版キャッシュの key を後始末する（撤回で版が進むため。次のアクセスは DB から埋め戻される）。 */
  @AfterEach
  void cleanupRedis() {
    ALL_EMAILS.forEach(email -> redisTemplate.delete(CREDENTIAL_VERSION_KEY_PREFIX + email));
  }

  // ---------------------------------------------------------------- 土台

  /**
   * 専用ユーザーを取得または作成する。
   *
   * <p>権限保持者を敢えて {@link StoreScopeType#ALL_STORES} で作る。素の作用域なら全店舗が通るので、昇格トークンで 非対象店舗が 403
   * になることが「昇格が作用域を絞った」ことの証明になる（継承なら 200 になってしまう）。
   */
  private PlatformUser ensureUser(
      String email, String roleName, StoreScopeType scopeType, Set<Long> storeIds) {
    return platformUserRepository
        .findByEmail(email)
        .orElseGet(
            () ->
                platformUserRepository.save(
                    PlatformUser.builder()
                        .email(email)
                        .password(passwordEncoder.encode(PASSWORD))
                        .displayName("緊急昇格IT " + email)
                        .enabled(true)
                        .userType(UserType.STAFF)
                        .roleIds(Set.of(roleRepository.findByName(roleName).orElseThrow().getId()))
                        .storeScopeType(scopeType)
                        .storeIds(storeIds)
                        .build()));
  }

  private PlatformUser ensureHolder(String email) {
    return ensureUser(email, HQ_ROLE, StoreScopeType.ALL_STORES, Set.of());
  }

  private String login(String email) {
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, PASSWORD),
                jsonHeaders()),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: %s のログインが成功すること", email).isEqualTo(HttpStatus.OK);
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
    HttpHeaders headers = bearer(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  /** 平台トークン + 店舗文脈ヘッダ。昇格トークンが店舗コンソールへ届くかを問う正規のリクエスト形。 */
  private static HttpHeaders storeHeaders(String token, long storeId) {
    HttpHeaders headers = bearerJson(token);
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(storeId));
    return headers;
  }

  private ResponseEntity<JsonNode> activate(String token, long storeId, String reason) {
    return activateWithPassword(token, storeId, reason, PASSWORD);
  }

  private ResponseEntity<JsonNode> activateWithPassword(
      String token, long storeId, String reason, String password) {
    return rest.postForEntity(
        "/platform/emergency-elevations",
        new HttpEntity<>(
            String.format(
                "{\"store_id\": %d, \"reason\": \"%s\", \"password\": \"%s\"}",
                storeId, reason, password),
            bearerJson(token)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> revoke(String token, long elevationId) {
    return rest.exchange(
        "/platform/emergency-elevations/" + elevationId + "/revocation",
        HttpMethod.POST,
        new HttpEntity<>(bearer(token)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> me(String token) {
    return rest.exchange(
        "/platform/me", HttpMethod.GET, new HttpEntity<>(bearer(token)), JsonNode.class);
  }

  /** 店舗コンソールの読み（CAST_MANAGE）。昇格トークンが店舗文脈を確立できるかの観測面。 */
  private ResponseEntity<JsonNode> listCasts(String token, long storeId) {
    return rest.exchange(
        "/store/casts",
        HttpMethod.GET,
        new HttpEntity<>(storeHeaders(token, storeId)),
        JsonNode.class);
  }

  /** 店舗コンソールの書き（CAST_MANAGE）。store_id は StoreContext から採番されるので、着地先が観測できる。 */
  private ResponseEntity<JsonNode> createCast(String token, long storeId, String name) {
    return rest.postForEntity(
        "/store/casts",
        new HttpEntity<>(String.format("{\"name\": \"%s\"}", name), storeHeaders(token, storeId)),
        JsonNode.class);
  }

  private Optional<Cast> findCastByName(String name) {
    return castRepository.findAll().stream().filter(c -> name.equals(c.getName())).findFirst();
  }

  private long elevationCountOf(Long userId) {
    return elevationRepository.findAll().stream()
        .filter(e -> userId.equals(e.getActivatedBy()))
        .count();
  }

  private EmergencyElevation reload(long elevationId) {
    return elevationRepository.findById(elevationId).orElseThrow();
  }

  private static String errorOf(JsonNode body) {
    return body == null ? "" : body.path("error").asString();
  }

  // ---------------------------------------------------------------- ケース

  @Test
  @DisplayName("EMERGENCY_ELEVATE を持たない者の発動は 403 で、記録も残らないこと")
  void activationWithoutPermissionIsForbidden() {
    PlatformUser nonHolder =
        ensureUser(
            NON_HOLDER_EMAIL, STORE_STAFF_ROLE, StoreScopeType.SPECIFIC_STORES, Set.of(STORE_A));
    long before = elevationCountOf(nonHolder.getId());

    ResponseEntity<JsonNode> res = activate(login(NON_HOLDER_EMAIL), STORE_A, "権限なしの発動");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(elevationCountOf(nonHolder.getId())).as("拒否された発動が記録を残さないこと").isEqualTo(before);
  }

  @Test
  @DisplayName("再認証に失敗した発動は 401 で、記録が 1 行も書かれないこと（再認証がいかなる書き込みよりも先）")
  void failedReauthenticationLeavesNoRecord() {
    PlatformUser holder = ensureHolder(HOLDER_EMAIL);
    String token = login(HOLDER_EMAIL);
    long before = elevationCountOf(holder.getId());

    ResponseEntity<JsonNode> res =
        activateWithPassword(token, STORE_A, "誤パスワードの発動", WRONG_PASSWORD);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    // 401 の出所がログインと同じ資格情報判定であること（認証エントリポイントの汎用文言ではない）。
    assertThat(errorOf(res.getBody())).isEqualTo(BAD_CREDENTIALS_MESSAGE);
    assertThat(elevationCountOf(holder.getId())).as("失敗した再認証が記録を残さないこと").isEqualTo(before);
  }

  @Test
  @DisplayName("発動は 201 で ACTIVE な記録を残し、返るトークンで対象店舗の店舗コンソールへ書けること")
  void activationIssuesTokenThatWritesToTargetStore() {
    PlatformUser holder = ensureHolder(HOLDER_EMAIL);
    String reason = "深夜障害の一次対応";

    ResponseEntity<JsonNode> res = activate(login(HOLDER_EMAIL), STORE_A, reason);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    long elevationId = res.getBody().path("id").asLong();
    String elevatedToken = res.getBody().path("token").asString();
    assertThat(elevatedToken).isNotBlank();

    EmergencyElevation record = reload(elevationId);
    assertThat(record.getActivatedBy()).isEqualTo(holder.getId());
    assertThat(record.getTargetStoreId()).isEqualTo(STORE_A);
    assertThat(record.getReason()).isEqualTo(reason);
    assertThat(record.getStatus()).isEqualTo(EmergencyElevationStatus.ACTIVE);
    assertThat(record.getRevokedBy()).isNull();
    assertThat(record.getRevokedAt()).isNull();
    assertThat(record.getExpiresAt())
        .as("期限は発動時刻から固定の 60 分")
        .isEqualTo(record.getActivatedAt().plus(EmergencyElevation.EFFECTIVE_DURATION));
    assertThat(res.getBody().path("expires_at").asLong())
        .as("応答の期限は記録の期限そのもの（トークンと記録で別々に数えない）")
        .isEqualTo(record.getExpiresAt().toInstant().toEpochMilli());

    String castName = "緊急昇格IT_書込_" + elevationId;
    ResponseEntity<JsonNode> write = createCast(elevatedToken, STORE_A, castName);

    assertThat(write.getStatusCode()).as("昇格トークンで店舗コンソールの書きが通ること").isEqualTo(HttpStatus.CREATED);
    assertThat(findCastByName(castName).orElseThrow().getStoreId())
        .as("書いた行が対象店舗へ着地すること")
        .isEqualTo(STORE_A);
  }

  @Test
  @DisplayName("昇格トークンは対象店舗にのみ届き、非対象店舗は 403 で書き込みも残らないこと")
  void elevatedTokenReachesOnlyTargetStore() {
    ensureHolder(HOLDER_EMAIL);
    ResponseEntity<JsonNode> activation = activate(login(HOLDER_EMAIL), STORE_A, "射程確認の発動");
    assertThat(activation.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String elevatedToken = activation.getBody().path("token").asString();

    // 正向対照。同じトークン・同じ端点で対象店舗が通ることが、後段の 403 の出所を作用域の検証へ絞り込む
    // （@PreAuthorize と storeBridge の 2 つの門は、この 200 によって既に通過が示されている）。
    // AccessDeniedException の応答文言は型ごとに固定されており、拒否理由の区別は文言では取れない。
    assertThat(listCasts(elevatedToken, STORE_A).getStatusCode())
        .as("前提: 対象店舗は同じトークンで通ること")
        .isEqualTo(HttpStatus.OK);

    String castName = "緊急昇格IT_非対象店舗_" + System.nanoTime();
    ResponseEntity<JsonNode> write = createCast(elevatedToken, STORE_B, castName);

    assertThat(write.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(findCastByName(castName)).as("拒否された書きが永続化されていないこと").isEmpty();
  }

  @Test
  @DisplayName("期限切れの昇格トークンは店舗文脈の呼び出しで 401 になること（exp 以外は同一の正向対照付き）")
  void expiredElevatedTokenIsRejected() {
    ensureHolder(HOLDER_EMAIL);
    Instant now = Instant.now();

    // 正向対照と本番の差は exp だけ。版 claim も権限も同一なので、401 の出所は期限に絞られる。
    String live = forgedElevatedToken(now.minusSeconds(10), now.plusSeconds(600));
    assertThat(listCasts(live, STORE_A).getStatusCode())
        .as("前提: 期限内の同型トークンは通ること")
        .isEqualTo(HttpStatus.OK);

    String expired = forgedElevatedToken(now.minusSeconds(600), now.minusSeconds(1));
    ResponseEntity<JsonNode> res = listCasts(expired, STORE_A);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(errorOf(res.getBody())).isEqualTo(UNAUTHENTICATED_MESSAGE);
  }

  /**
   * 実サーバーと同じ鍵・同じ形の昇格トークンを、期限だけ差し替えて組み立てる。
   *
   * <p>{@link PlatformJwtIssuer#issue} は issuedAt に常に現在時刻を使うため期限切れを表現できない（{@code Jwt} が exp &gt;
   * iat を要求する）。iat 自体も過去へ置くことでその制約を保つ。{@code elevationId} は現状どこでも検証されないので 実在する記録は要らない。
   */
  private String forgedElevatedToken(Instant issuedAt, Instant expiresAt) {
    long credentialVersion =
        platformUserRepository.findCredentialVersionByEmail(HOLDER_EMAIL).orElseThrow();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(PlatformJwtIssuer.ISSUER_PLATFORM)
            .subject(HOLDER_EMAIL)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("authorities", List.of("PERM_CAST_MANAGE"))
            .claim("userType", UserType.STAFF.name())
            .claim("storeBridge", true)
            .claim("storeScopeType", StoreScopeType.SPECIFIC_STORES.name())
            .claim("storeIds", List.of(STORE_A))
            .claim("elevationId", 0L)
            .claim(CredentialVersionService.CLAIM, credentialVersion)
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  @Test
  @DisplayName("撤回は記録を閉じ、発動者の通常トークンと昇格トークンを同時に失効させ、二度目の撤回は 400 になること")
  void revocationClosesRecordAndKillsBothTokens() {
    PlatformUser holder = ensureHolder(REVOKE_EMAIL);
    String normalToken = login(REVOKE_EMAIL);
    ResponseEntity<JsonNode> activation = activate(normalToken, STORE_A, "撤回確認の発動");
    assertThat(activation.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    long elevationId = activation.getBody().path("id").asLong();
    String elevatedToken = activation.getBody().path("token").asString();

    // 正向対照: 撤回前は双方が通る（後段の 401 が撤回起因であることの証明）。
    assertThat(me(normalToken).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(listCasts(elevatedToken, STORE_A).getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(revoke(normalToken, elevationId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    EmergencyElevation record = reload(elevationId);
    assertThat(record.getStatus()).isEqualTo(EmergencyElevationStatus.REVOKED);
    assertThat(record.getRevokedBy()).isEqualTo(holder.getId());
    assertThat(record.getRevokedAt()).isNotNull();

    assertThat(me(normalToken).getStatusCode())
        .as("撤回は発動者の通常セッションも落とすこと")
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(listCasts(elevatedToken, STORE_A).getStatusCode())
        .as("昇格トークンも同じ失効機構に載っていること")
        .isEqualTo(HttpStatus.UNAUTHORIZED);

    // 二度目は新しいセッションで叩く。撤回済みトークンのままだと 401 が 400 の不在を覆い隠す。
    ResponseEntity<JsonNode> second = revoke(login(REVOKE_EMAIL), elevationId);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errorOf(second.getBody()))
        .as("400 の出所がドメインの守衛であること")
        .isEqualTo(NOT_REVOCABLE_MESSAGE);
  }

  @Test
  @DisplayName("撤回後の再発動は新しい行になり、先の記録は書き換わらないこと（追記型）")
  void reactivationCreatesNewRecord() {
    PlatformUser holder = ensureHolder(REACTIVATE_EMAIL);
    ResponseEntity<JsonNode> first = activate(login(REACTIVATE_EMAIL), STORE_A, "一度目の発動");
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    long firstId = first.getBody().path("id").asLong();

    assertThat(revoke(login(REACTIVATE_EMAIL), firstId).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    EmergencyElevation revoked = reload(firstId);
    OffsetDateTime revokedAt = revoked.getRevokedAt();
    OffsetDateTime firstExpiresAt = revoked.getExpiresAt();

    ResponseEntity<JsonNode> second = activate(login(REACTIVATE_EMAIL), STORE_A, "二度目の発動");
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    long secondId = second.getBody().path("id").asLong();

    assertThat(secondId).as("再発動は新しい行になること").isNotEqualTo(firstId);
    assertThat(elevationCountOf(holder.getId())).as("同一利用者・同一店舗で 2 行が並ぶこと").isEqualTo(2);
    assertThat(reload(secondId).getStatus()).isEqualTo(EmergencyElevationStatus.ACTIVE);

    EmergencyElevation firstAfter = reload(firstId);
    assertThat(firstAfter.getStatus())
        .as("先の記録は撤回済みのまま")
        .isEqualTo(EmergencyElevationStatus.REVOKED);
    assertThat(firstAfter.getReason()).isEqualTo("一度目の発動");
    assertThat(firstAfter.getRevokedAt()).isEqualTo(revokedAt);
    assertThat(firstAfter.getExpiresAt()).isEqualTo(firstExpiresAt);
  }

  @Test
  @DisplayName("実在しない店舗への発動は 404 になり、記録も残らないこと")
  void activationAgainstMissingStoreIsNotFound() {
    PlatformUser holder = ensureHolder(HOLDER_EMAIL);
    long before = elevationCountOf(holder.getId());

    ResponseEntity<JsonNode> res = activate(login(HOLDER_EMAIL), MISSING_STORE, "存在しない店舗への発動");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(errorOf(res.getBody())).isEqualTo(MISSING_STORE_MESSAGE);
    assertThat(elevationCountOf(holder.getId())).as("外部キー違反の行が残らないこと").isEqualTo(before);
  }

  @Test
  @DisplayName("他人の昇格を撤回でき、失効するのは発動者のセッションだけであること")
  void anotherHolderRevokesAndOnlyActivatorLosesSessions() {
    PlatformUser activator = ensureHolder(CROSS_ACTIVATOR_EMAIL);
    PlatformUser revoker = ensureHolder(CROSS_REVOKER_EMAIL);
    String activatorToken = login(CROSS_ACTIVATOR_EMAIL);
    String revokerToken = login(CROSS_REVOKER_EMAIL);

    ResponseEntity<JsonNode> activation = activate(activatorToken, STORE_A, "代理撤回確認の発動");
    assertThat(activation.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    long elevationId = activation.getBody().path("id").asLong();
    String elevatedToken = activation.getBody().path("token").asString();

    assertThat(revoke(revokerToken, elevationId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    EmergencyElevation record = reload(elevationId);
    assertThat(record.getActivatedBy()).isEqualTo(activator.getId());
    assertThat(record.getRevokedBy()).as("撤回者は撤回を実行した者").isEqualTo(revoker.getId());

    assertThat(me(activatorToken).getStatusCode())
        .as("版が進むのは発動者の側")
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(listCasts(elevatedToken, STORE_A).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(me(revokerToken).getStatusCode())
        .as("撤回者自身のセッションは巻き添えにならないこと")
        .isEqualTo(HttpStatus.OK);
  }
}
