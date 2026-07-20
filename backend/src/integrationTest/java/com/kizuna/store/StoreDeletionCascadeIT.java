package com.kizuna.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.domain.CapabilityBundleRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 店舗削除が platform_user_stores（プラットフォームユーザーの店舗授権集合）まで ON DELETE CASCADE で従うことを 本物の PostgreSQL
 * で検証する統合テスト（#322）。
 *
 * <p>店舗削除は既存の全店舗参照 FK が CASCADE で従う確立済みセマンティクスであり、店舗授権行も店舗と共に消えるのが整合的。 修正前は
 * platform_user_stores→t_stores の FK が既定の NO ACTION だったため、SPECIFIC_STORES ユーザーが 授権している店舗を削除すると FK
 * 違反で店舗削除自体が失敗していた（本 IT はその退行のガード）。
 *
 * <p>様式は {@link SeedSequenceAlignmentIT}（HQ 管理者の平台ログイン + JdbcTemplate による実 DB 断言）に倣う。使い捨て tmpfs DB
 * のためシード store 1 は決して削除せず、第二店舗を直挿して検証する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StoreDeletionCascadeIT {

  @Autowired private TestRestTemplate rest;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StoreRepository storeRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private CapabilityBundleRepository capabilityBundleRepository;

  private String platformLogin() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>("{\"email\": \"admin@kizuna.test\", \"password\": \"pass\"}", headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: HQ 管理者での平台ログインが成功すること").isEqualTo(HttpStatus.OK);
    String token = res.getBody().path("token").asText();
    assertThat(token).isNotBlank();
    return token;
  }

  @Test
  @DisplayName("店舗削除で SPECIFIC_STORES ユーザーの店舗授権行が CASCADE 消去され、ユーザー本体は残ること")
  void deletingStoreCascadesStoreGrantButKeepsPlatformUser() {
    // 第二店舗を直挿（シード store 1 は他 IT が依存するため決して削除しない）。
    Store store =
        storeRepository.save(
            new Store(
                "削除カスケード検証店舗", "store-delete-it-" + UUID.randomUUID() + ".kizuna.test", null));
    long storeId = store.getId();

    // その店舗のみを授権集合に持つ SPECIFIC_STORES ユーザーを直挿（ログインは行わないため password はエンコード済みダミー）。
    PlatformUser user =
        platformUserRepository.save(
            PlatformUser.builder()
                .email("cascade-it-" + UUID.randomUUID() + "@kizuna.test")
                .password(passwordEncoder.encode("pass"))
                .displayName("店舗授権カスケード検証")
                .enabled(true)
                .userType(UserType.STAFF)
                .bundleIds(
                    Set.of(capabilityBundleRepository.findByName("店長").orElseThrow().getId()))
                .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of(storeId))
                .build());
    long userId = user.getId();

    // 前提: 削除前は授権行が 1 件存在する（空振りで緑にならないことを固定）。
    assertThat(countStoreGrants(storeId)).as("削除前は店舗授権行が存在すること").isEqualTo(1L);

    // 実削除フロー: 中央 admin トークンで DELETE /platform/stores/{id}。
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(platformLogin());
    ResponseEntity<Void> res =
        rest.exchange(
            "/platform/stores/" + storeId,
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            Void.class);

    // FK が ON DELETE CASCADE でなければ店舗削除は FK 違反で失敗する（本修正前の退行）。
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    // 店舗授権行は店舗と共に消える。
    assertThat(countStoreGrants(storeId)).as("削除後は店舗授権行が CASCADE 消去されること").isZero();
    // ユーザー本体は残る（授権集合が空になるだけ = authorizes() 全 false の fail-closed）。
    assertThat(countPlatformUser(userId)).as("プラットフォームユーザー本体は残存すること").isEqualTo(1L);
  }

  private long countStoreGrants(long storeId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM t_user_stores WHERE store_id = ?", Long.class, storeId);
  }

  private long countPlatformUser(long userId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM t_users WHERE id = ?", Long.class, userId);
  }
}
