package com.kizuna.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.user.domain.CapabilityBundleRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

/**
 * 統一（プラットフォーム）認証境界の統合テスト。本物の PostgreSQL/Redis に対して検証する。
 *
 * <p>様式は {@link com.kizuna.menu.MenuCrossStoreIT}（リポジトリ直挿 + 実データ断言）に倣う。 匿名ログインの CSRF 免除、シード資格情報での
 * me 応答、および平台トークンの店舗端点への過橋拒否を HTTP 境界で固定する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PlatformAuthIT {

  private static final String SEED_EMAIL = "admin@kizuna.test";
  private static final String SEED_PASSWORD = "pass";
  private static final String SEED_DISPLAY_NAME = "HQ管理者";

  private static final String SPECIFIC_EMAIL = "manager@kizuna.test";
  private static final String SPECIFIC_PASSWORD = "pass";

  private static final String DISABLED_EMAIL = "disabled@kizuna.test";
  private static final String DISABLED_PASSWORD = "pass";

  private static final String INVALID_CREDENTIALS_MESSAGE = "メールアドレスまたはパスワードが正しくありません";
  private static final String DISABLED_ACCOUNT_MESSAGE = "アカウントが無効化されています";

  @Autowired private TestRestTemplate rest;
  @Autowired private CapabilityBundleRepository capabilityBundleRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private ResponseEntity<JsonNode> platformLogin(String email, String password) {
    return rest.postForEntity(
        "/platform/login",
        new HttpEntity<>(
            String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, password),
            jsonHeaders()),
        JsonNode.class);
  }

  private String platformToken(String email, String password) {
    ResponseEntity<JsonNode> res = platformLogin(email, password);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    String token = res.getBody().path("token").asString();
    assertThat(token).isNotBlank();
    return token;
  }

  private ResponseEntity<JsonNode> getPlatformMe(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return rest.exchange("/platform/me", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
  }

  @Test
  @DisplayName("匿名 POST /platform/login がシード資格情報で 200 + 非空トークンを返す（CSRF 免除 + シード投入の同時証明）")
  void anonymousLoginWithSeedCredentialsSucceeds() {
    ResponseEntity<JsonNode> res = platformLogin(SEED_EMAIL, SEED_PASSWORD);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().path("token").asString()).isNotBlank();
  }

  @Test
  @DisplayName("誤パスワードは 401（403 でないこと = CSRF 免除の回帰ガード）+ 標準スタックの資格情報文言を返す")
  void wrongPasswordReturns401NotForbidden() {
    ResponseEntity<JsonNode> res = platformLogin(SEED_EMAIL, "wrong-password");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(res.getBody().path("error").asString()).isEqualTo(INVALID_CREDENTIALS_MESSAGE);
  }

  @Test
  @DisplayName("不存在メールは誤パスワードと同一の 401 文言を返す（AuthenticationManager の列挙耐性が実 DB で担保される）")
  void missingEmailReturnsSameMessageAsWrongPassword() {
    ResponseEntity<JsonNode> res = platformLogin("missing@kizuna.test", "whatever-password");

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(res.getBody().path("error").asString()).isEqualTo(INVALID_CREDENTIALS_MESSAGE);
  }

  @Test
  @DisplayName("無効化アカウントは正パスワードでも 401 + 無効化文言を返す（enabled 判定がパスワード照合に先行する）")
  void disabledAccountWithCorrectPasswordReturns401DisabledMessage() {
    platformUserRepository
        .findByEmail(DISABLED_EMAIL)
        .orElseGet(
            () ->
                platformUserRepository.save(
                    PlatformUser.builder()
                        .email(DISABLED_EMAIL)
                        .password(passwordEncoder.encode(DISABLED_PASSWORD))
                        .displayName("無効化済み")
                        .enabled(false)
                        .userType(UserType.STAFF)
                        .bundleIds(managerBundleIds())
                        .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                        .storeIds(Set.of(1L))
                        .build()));

    ResponseEntity<JsonNode> res = platformLogin(DISABLED_EMAIL, DISABLED_PASSWORD);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(res.getBody().path("error").asString()).isEqualTo(DISABLED_ACCOUNT_MESSAGE);
  }

  @Test
  @DisplayName("GET /platform/me がシード HQ 管理者のロール・店舗集合を返す（ALL_STORES は store_ids 空）")
  void meReturnsSeedHqAdminRoleAndScope() {
    ResponseEntity<JsonNode> res = getPlatformMe(platformToken(SEED_EMAIL, SEED_PASSWORD));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = res.getBody();
    assertThat(body.path("email").asString()).isEqualTo(SEED_EMAIL);
    assertThat(body.path("display_name").asString()).isEqualTo(SEED_DISPLAY_NAME);
    assertThat(body.path("user_type").asString()).isEqualTo("STAFF");
    assertThat(body.path("console").asString()).isEqualTo("platform");
    assertThat(body.path("capabilities").toString()).contains("STAFF_MANAGE");
    assertThat(body.path("store_scope_type").asString()).isEqualTo("ALL_STORES");
    assertThat(body.path("store_ids")).isEmpty();
  }

  @Test
  @DisplayName("SPECIFIC_STORES ユーザーはログイン後 me が store_ids=[1] を返す（リポジトリ直挿の実データ断言）")
  void specificStoresUserSeesOwnStoreIds() {
    platformUserRepository
        .findByEmail(SPECIFIC_EMAIL)
        .orElseGet(
            () ->
                platformUserRepository.save(
                    PlatformUser.builder()
                        .email(SPECIFIC_EMAIL)
                        .password(passwordEncoder.encode(SPECIFIC_PASSWORD))
                        .displayName("個別店舗担当")
                        .enabled(true)
                        .userType(UserType.STAFF)
                        .bundleIds(managerBundleIds())
                        .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                        .storeIds(Set.of(1L))
                        .build()));

    ResponseEntity<JsonNode> res = getPlatformMe(platformToken(SPECIFIC_EMAIL, SPECIFIC_PASSWORD));

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = res.getBody();
    assertThat(body.path("store_scope_type").asString()).isEqualTo("SPECIFIC_STORES");
    assertThat(body.path("store_ids").isArray()).isTrue();
    assertThat(body.path("store_ids").size()).isEqualTo(1);
    assertThat(body.path("store_ids").get(0).asLong()).isEqualTo(1L);
  }

  @Test
  @DisplayName("platform トークンで店舗ヘッダなしの store 系 GET は 403（過橋: 拒否主体は interceptor fail-closed）")
  void platformTokenRejectedOnStoreEndpoint() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(platformToken(SEED_EMAIL, SEED_PASSWORD));

    ResponseEntity<String> res =
        rest.exchange("/store/orders", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  /** 種子の既定束「店長」を名称で解決する（束はデータ — id を決め打ちしない）。 */
  private Set<Long> managerBundleIds() {
    return Set.of(capabilityBundleRepository.findByName("店長").orElseThrow().getId());
  }
}
