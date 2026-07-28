package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.CrossStoreTestSupport;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

/**
 * GET /store/orders/receptionists の集合作用域を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>{@code OrderService#listReceptionists} が {@code validateReceptionist}
 * と同一の適格条件を共有することの証跡。断言は「帰属不一致」型の弱断言ではなく、応答生ボディに授権外店舗の実データ（表示名）が 一切現れないこと（負向強断言）で行う。先例は {@link
 * PlatformOrderScopeIT}（リポジトリ直挿 + 実データ断言）。
 */
class OrderReceptionistIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "pass";
  private static final String STORE_B_STAFF_EMAIL = "receptionist-it-store-b-staff@kizuna.test";
  private static final String STORE_B_STAFF_NAME = "統合テスト店舗B専属スタッフ";
  private static final String CAST_EMAIL = "receptionist-it-cast@kizuna.test";

  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void prepareFixture() {
    ensurePlatformUser(
        STORE_B_STAFF_EMAIL,
        STORE_B_STAFF_NAME,
        UserType.STAFF,
        roleIdsOf("店舗スタッフ"),
        StoreScopeType.SPECIFIC_STORES,
        Set.of(STORE_B));
    ensurePlatformUser(
        CAST_EMAIL,
        "統合テストキャスト（受付一覧IT）",
        UserType.CAST,
        Set.of(),
        StoreScopeType.ALL_STORES,
        Set.of());
  }

  /** リポジトリ直挿（テストスレッドは @StoreScoped を経由せず storeFilter が無効なので他店舗にも書ける）。 */
  private void ensurePlatformUser(
      String email,
      String displayName,
      UserType userType,
      Set<Long> roleIds,
      StoreScopeType scopeType,
      Set<Long> storeIds) {
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
                        .userType(userType)
                        .roleIds(roleIds)
                        .storeScopeType(scopeType)
                        .storeIds(storeIds)
                        .build()));
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

  private static HttpHeaders headersFor(String token, long storeId) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(storeId));
    headers.setBearerAuth(token);
    return headers;
  }

  @Test
  @DisplayName("store A の受付候補一覧にログイン中のシードスタッフ(山田次郎)が含まれること(正向)")
  void storeAReceptionistsIncludeSeedStaff() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/store/orders/receptionists",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    boolean found = false;
    for (JsonNode node : res.getBody()) {
      if ("山田次郎".equals(node.path("display_name").asString())) {
        found = true;
        break;
      }
    }
    assertThat(found).as("ログイン中のシードスタッフが受付候補として現れること").isTrue();
  }

  @Test
  @DisplayName("store B専属スタッフの実データが store A の受付候補一覧に一切現れないこと(負向強断言)")
  void storeAReceptionistsNeverLeakStoreBStaff() {
    ResponseEntity<String> storeAResponse =
        rest.exchange(
            "/store/orders/receptionists",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            String.class);
    assertThat(storeAResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(storeAResponse.getBody())
        .as("授権外店舗(store B)専属スタッフの実データが生ボディに現れないこと")
        .doesNotContain(STORE_B_STAFF_NAME);

    // 正向対照: 本人(store B専属)自身のトークンで store B へ照会すれば現れる
    // （述語が常に空を返しているのではないことの証明。山田次郎のトークンでは store B を授権しないため使えない）。
    String storeBStaffToken = platformToken(STORE_B_STAFF_EMAIL, PASSWORD);
    ResponseEntity<String> storeBResponse =
        rest.exchange(
            "/store/orders/receptionists",
            HttpMethod.GET,
            new HttpEntity<>(headersFor(storeBStaffToken, STORE_B)),
            String.class);
    assertThat(storeBResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(storeBResponse.getBody())
        .as("正向対照: store Bへの照会では同スタッフが現れること")
        .contains(STORE_B_STAFF_NAME);
  }

  @Test
  @DisplayName("PERM_ORDER_MANAGE を持たない CAST ロールで店舗コンソールに触れると 403")
  void castRoleIsRejected() {
    String castToken = platformToken(CAST_EMAIL, PASSWORD);

    ResponseEntity<String> res =
        rest.exchange(
            "/store/orders/receptionists",
            HttpMethod.GET,
            new HttpEntity<>(headersFor(castToken, STORE_A)),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
}
