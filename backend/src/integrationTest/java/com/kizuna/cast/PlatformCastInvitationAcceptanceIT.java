package com.kizuna.cast;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.kizuna.cast.domain.CastInvitation;
import com.kizuna.cast.domain.CastInvitationRepository;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.CrossTenantTestSupport;
import com.kizuna.tenant.domain.TenantRepository;
import com.kizuna.user.domain.PlatformRole;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import java.time.OffsetDateTime;
import java.util.Set;
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

/**
 * 招待受諾 API（公開照会・新規登録受諾・既存アカウント受諾）を本物の PostgreSQL で検証する統合テスト（#327）。
 *
 * <p>身分作成・档案紐づけ・招待状態遷移が単一トランザクションで確定すること、無効 token では副作用が出ないことを DB 断言で固定する。
 */
class PlatformCastInvitationAcceptanceIT extends CrossTenantTestSupport {

  private static final String MANAGER_EMAIL = "tanaka.hanako@kizuna.test";
  private static final String PASSWORD = "pass";
  private static final String EXISTING_CAST_EMAIL = "cast-existing-it@kizuna.test";

  @Autowired private CastRepository castRepository;
  @Autowired private CastInvitationRepository castInvitationRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private String managerToken;

  @BeforeEach
  void prepareManager() {
    managerToken = platformToken(MANAGER_EMAIL, PASSWORD);
  }

  @Test
  @DisplayName("新規登録受諾で CAST 身分が作成され、档案に紐づき、招待が ACCEPTED になること")
  void newUserAcceptanceLinksIdentityAndMarksAccepted() {
    String castId = createCast(TENANT_A, "受諾新規テスト");
    String token = issue(castId, TENANT_A);
    String email = "cast-new-it-" + System.nanoTime() + "@kizuna.test";

    ResponseEntity<JsonNode> res = acceptNewUser(token, email, "password1234", "新人花子");

    assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    PlatformUser user = platformUserRepository.findByEmail(email).orElseThrow();
    assertThat(user.getRole()).isEqualTo(PlatformRole.CAST);
    assertThat(user.getStoreScopeType()).isEqualTo(StoreScopeType.SPECIFIC_STORES);
    assertThat(user.getStoreIds()).contains(TENANT_A);
    assertThat(castRepository.findById(castId).orElseThrow().getPlatformUserId())
        .isEqualTo(user.getId());
    assertThat(castInvitationRepository.findByToken(token).orElseThrow().getStatus())
        .isEqualTo(CastInvitation.Status.ACCEPTED);
  }

  @Test
  @DisplayName("期限切れ token の受諾は 4xx で拒否され、PlatformUser 数が不変であること")
  void expiredTokenAcceptanceIsRejectedWithoutSideEffects() {
    String castId = createCast(TENANT_A, "期限切れ受諾テスト");
    String token =
        directInsertInvitation(
            castId, CastInvitation.Status.PENDING, OffsetDateTime.now().minusHours(1));
    String email = "cast-expired-it-" + System.nanoTime() + "@kizuna.test";
    long before = platformUserRepository.count();

    ResponseEntity<JsonNode> res = acceptNewUser(token, email, "password1234", "花子");

    assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    assertThat(platformUserRepository.count()).isEqualTo(before);
    assertThat(platformUserRepository.findByEmail(email)).isEmpty();
  }

  @Test
  @DisplayName("受諾済み token は再受諾できないこと（4xx）")
  void acceptedTokenCannotBeReaccepted() {
    String castId = createCast(TENANT_A, "再受諾不可テスト");
    String token = issue(castId, TENANT_A);
    acceptNewUser(
        token, "cast-first-it-" + System.nanoTime() + "@kizuna.test", "password1234", "花子");

    ResponseEntity<JsonNode> res =
        acceptNewUser(
            token, "cast-second-it-" + System.nanoTime() + "@kizuna.test", "password1234", "次郎");

    assertThat(res.getStatusCode().is4xxClientError()).isTrue();
  }

  @Test
  @DisplayName("再発行で失効した旧 token は受諾できないこと（4xx）")
  void invalidatedTokenIsRejected() {
    String castId = createCast(TENANT_A, "失効token受諾不可テスト");
    String staleToken = issue(castId, TENANT_A);
    issue(castId, TENANT_A);

    ResponseEntity<JsonNode> res =
        acceptNewUser(
            staleToken,
            "cast-stale-it-" + System.nanoTime() + "@kizuna.test",
            "password1234",
            "花子");

    assertThat(res.getStatusCode().is4xxClientError()).isTrue();
  }

  @Test
  @DisplayName("既に登録済みのメールでの新規登録受諾は 4xx で拒否されること")
  void duplicateEmailRegistrationIsRejected() {
    String castId = createCast(TENANT_A, "重複メール受諾テスト");
    String token = issue(castId, TENANT_A);

    // 既存のシードユーザー（田中花子）の email で新規登録を試みる。
    ResponseEntity<JsonNode> res = acceptNewUser(token, MANAGER_EMAIL, "password1234", "花子");

    assertThat(res.getStatusCode().is4xxClientError()).isTrue();
  }

  @Test
  @DisplayName("既存 CAST アカウントの受諾で所属店舗が追加され、档案に紐づくこと")
  void existingCastAcceptanceAddsStoreAndLinks() {
    PlatformUser castUser = ensureExistingCastUser();
    String castBearer = platformToken(EXISTING_CAST_EMAIL, PASSWORD);
    // 田中は店舗{1,2}授権。既存 CAST は店舗{1}所属なので、店舗2の招待で店舗を追加する。
    String castId = createCast(TENANT_B, "既存受諾店舗2テスト");
    String token = issue(castId, TENANT_B);

    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/cast-invitations/" + token + "/acceptance/existing",
            HttpMethod.POST,
            new HttpEntity<>(bearer(castBearer)),
            JsonNode.class);

    assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
    PlatformUser reloaded = platformUserRepository.findByEmail(EXISTING_CAST_EMAIL).orElseThrow();
    assertThat(reloaded.getStoreIds()).contains(TENANT_A, TENANT_B);
    assertThat(castRepository.findById(castId).orElseThrow().getPlatformUserId())
        .isEqualTo(castUser.getId());
    assertThat(castInvitationRepository.findByToken(token).orElseThrow().getStatus())
        .isEqualTo(CastInvitation.Status.ACCEPTED);
  }

  @Test
  @DisplayName("CAST 以外のアカウントでの既存受諾は 403 で拒否されること")
  void existingAcceptanceWithNonCastRoleIsForbidden() {
    String castId = createCast(TENANT_A, "既存受諾非CASTテスト");
    String token = issue(castId, TENANT_A);

    // token（基底クラスの yamada = STORE_STAFF）で既存受諾 → @PreAuthorize ROLE_CAST で 403
    ResponseEntity<String> res =
        rest.exchange(
            "/platform/cast-invitations/" + token + "/acceptance/existing",
            HttpMethod.POST,
            new HttpEntity<>(bearer(this.token)),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("未認証の既存受諾は 401/403 で拒否されること")
  void existingAcceptanceUnauthenticatedIsRejected() {
    String castId = createCast(TENANT_A, "既存受諾未認証テスト");
    String token = issue(castId, TENANT_A);

    ResponseEntity<String> res =
        rest.exchange(
            "/platform/cast-invitations/" + token + "/acceptance/existing",
            HttpMethod.POST,
            new HttpEntity<>(jsonHeaders()),
            String.class);

    assertThat(res.getStatusCode().value()).isIn(401, 403);
  }

  @Test
  @DisplayName("照会は VALID/EXPIRED/USED の 3 状態と店舗名・档案名を返すこと")
  void viewReturnsValidExpiredUsedStates() {
    // VALID
    String validCast = createCast(TENANT_A, "照会VALIDテスト");
    String validToken = issue(validCast, TENANT_A);
    ResponseEntity<JsonNode> valid = viewInvitation(validToken);
    assertThat(valid.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(valid.getBody().path("status").asText()).isEqualTo("VALID");
    assertThat(valid.getBody().path("cast_name").asText()).isEqualTo("照会VALIDテスト");
    assertThat(valid.getBody().path("store_name").asText())
        .isEqualTo(tenantRepository.findById(TENANT_A).orElseThrow().getName());

    // EXPIRED
    String expiredCast = createCast(TENANT_A, "照会EXPIREDテスト");
    String expiredToken =
        directInsertInvitation(
            expiredCast, CastInvitation.Status.PENDING, OffsetDateTime.now().minusHours(1));
    assertThat(viewInvitation(expiredToken).getBody().path("status").asText()).isEqualTo("EXPIRED");

    // USED
    String usedCast = createCast(TENANT_A, "照会USEDテスト");
    String usedToken =
        directInsertInvitation(
            usedCast, CastInvitation.Status.INVALIDATED, OffsetDateTime.now().plusHours(1));
    assertThat(viewInvitation(usedToken).getBody().path("status").asText()).isEqualTo("USED");
  }

  private PlatformUser ensureExistingCastUser() {
    return platformUserRepository
        .findByEmail(EXISTING_CAST_EMAIL)
        .orElseGet(
            () ->
                platformUserRepository.save(
                    PlatformUser.builder()
                        .email(EXISTING_CAST_EMAIL)
                        .password(passwordEncoder.encode(PASSWORD))
                        .displayName("既存キャストIT")
                        .enabled(true)
                        .role(PlatformRole.CAST)
                        .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                        .storeIds(Set.of(TENANT_A))
                        .build()));
  }

  private String createCast(long tenantId, String name) {
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/tenant/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", tenantHeaders(tenantId, managerToken)),
            JsonNode.class);
    assertThat(res.getStatusCode().is2xxSuccessful())
        .as("前提: tenant %d でのキャスト作成が成功すること", tenantId)
        .isTrue();
    return res.getBody().path("id").asText();
  }

  private String issue(String castId, long tenantId) {
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/tenant/casts/" + castId + "/invitation",
            new HttpEntity<>(tenantHeaders(tenantId, managerToken)),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 招待発行が成功すること").isEqualTo(HttpStatus.CREATED);
    return res.getBody().path("token").asText();
  }

  private String directInsertInvitation(
      String castId, CastInvitation.Status status, OffsetDateTime expiresAt) {
    String token = "cast-inv-it-" + System.nanoTime();
    CastInvitation invitation =
        CastInvitation.builder()
            .castId(castId)
            .token(token)
            .status(status)
            .expiresAt(expiresAt)
            .build();
    invitation.setTenantId(TENANT_A);
    castInvitationRepository.save(invitation);
    return token;
  }

  private ResponseEntity<JsonNode> acceptNewUser(
      String token, String email, String password, String displayName) {
    String body =
        String.format(
            "{\"email\": \"%s\", \"password\": \"%s\", \"display_name\": \"%s\"}",
            email, password, displayName);
    return rest.postForEntity(
        "/platform/cast-invitations/" + token + "/acceptance",
        new HttpEntity<>(body, jsonHeaders()),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> viewInvitation(String token) {
    return rest.exchange(
        "/platform/cast-invitations/" + token,
        HttpMethod.GET,
        new HttpEntity<>(jsonHeaders()),
        JsonNode.class);
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
    String t = res.getBody().path("token").asText();
    assertThat(t).isNotBlank();
    return t;
  }

  private HttpHeaders tenantHeaders(long tenantId, String bearerToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Role", "tenant");
    headers.set("X-Tenant-ID", String.valueOf(tenantId));
    headers.setBearerAuth(bearerToken);
    return headers;
  }

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private static HttpHeaders bearer(String bearerToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(bearerToken);
    return headers;
  }
}
