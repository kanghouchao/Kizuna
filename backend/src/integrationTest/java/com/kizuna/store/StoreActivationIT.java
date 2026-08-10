package com.kizuna.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

/**
 * 準備中の店舗が店舗コンソールへの着地で稼働中へ移ることを本物の PostgreSQL で検証する統合テスト。
 *
 * <p>引き金は「店舗文脈を正当に確立できた店舗側利用者の 1 リクエスト」で、HQ の下見は引き金にならない。HQ 側は そもそも storeBridge
 * を持たないため店舗文脈の確立自体が拒まれる — 状態が動かないことに加えて、拒まれる事実まで固定する。
 *
 * <p>使い捨て tmpfs DB のためシード store 1 は用いず、判定ごとに新しい店舗を直挿する（一方向の遷移なので 店舗を使い回すと 2 件目以降が空振りで緑になる）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class StoreActivationIT {

  @Autowired private TestRestTemplate rest;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StoreRepository storeRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RoleRepository roleRepository;

  private static final String PASSWORD = "pass";

  private String login(String email, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}", headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 平台ログインが成功すること").isEqualTo(HttpStatus.OK);
    String token = res.getBody().path("token").asString();
    assertThat(token).isNotBlank();
    return token;
  }

  /** 新規登録した店舗は準備中から始まる。 */
  private Store freshStore(String name) {
    return storeRepository.save(
        new Store(name, "activation-it-" + UUID.randomUUID() + ".kizuna.test", null));
  }

  /** その店舗だけを授権集合に持つ店長（STORE コンソール権限＝storeBridge 保持者）を直挿し、その email を返す。 */
  private String createStoreManager(long storeId) {
    String email = "activation-it-" + UUID.randomUUID() + "@kizuna.test";
    platformUserRepository.save(
        PlatformUser.builder()
            .email(email)
            .password(passwordEncoder.encode(PASSWORD))
            .displayName("稼働遷移検証店長")
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(Set.of(roleRepository.findByName("店長").orElseThrow().getId()))
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(storeId))
            .build());
    return email;
  }

  /** 店舗コンソールの読み取りを 1 回行う（平台トークン + 店舗文脈ヘッダ）。 */
  private ResponseEntity<String> storeConsoleRequest(String token, long storeId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(storeId));
    return rest.exchange("/store/orders", HttpMethod.GET, new HttpEntity<>(headers), String.class);
  }

  private String statusOf(long storeId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM t_stores WHERE id = ?", String.class, storeId);
  }

  @Test
  @DisplayName("店舗側利用者の初回コンソール着地で店舗が稼働中へ移ること")
  void storeUserConsoleAccessActivatesStore() {
    Store store = freshStore("稼働遷移検証店舗");
    long storeId = store.getId();
    String email = createStoreManager(storeId);

    // 前提: 新規登録の直後は準備中（空振りで緑にならないことを固定）。
    assertThat(statusOf(storeId)).as("着地前は準備中であること").isEqualTo("PREPARING");

    ResponseEntity<String> res = storeConsoleRequest(login(email, PASSWORD), storeId);

    assertThat(res.getStatusCode()).as("店長の店舗コンソール読み取りが通ること").isEqualTo(HttpStatus.OK);
    assertThat(statusOf(storeId)).as("着地後は稼働中へ移ること").isEqualTo("ACTIVE");
  }

  @Test
  @DisplayName("HQ 管理者は店舗文脈を名乗れず、店舗は準備中のままであること")
  void platformAdminAccessDoesNotActivateStore() {
    Store store = freshStore("HQ下見検証店舗");
    long storeId = store.getId();

    assertThat(statusOf(storeId)).as("着地前は準備中であること").isEqualTo("PREPARING");

    ResponseEntity<String> res = storeConsoleRequest(login("admin@kizuna.test", PASSWORD), storeId);

    // HQ は STORE コンソール権限を持たないため storeBridge が false で、店舗文脈の確立が拒まれる。
    assertThat(res.getStatusCode()).as("HQ の店舗文脈は拒まれること").isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(statusOf(storeId)).as("HQ の下見では準備中のままであること").isEqualTo("PREPARING");
  }
}
