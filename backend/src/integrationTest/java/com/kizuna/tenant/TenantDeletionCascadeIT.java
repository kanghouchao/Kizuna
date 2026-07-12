package com.kizuna.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.kizuna.tenant.domain.Tenant;
import com.kizuna.tenant.domain.TenantRepository;
import com.kizuna.user.domain.PlatformRole;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
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
 * テナント削除が platform_user_stores（プラットフォームユーザーの店舗授権集合）まで ON DELETE CASCADE で従うことを 本物の PostgreSQL
 * で検証する統合テスト（#322）。
 *
 * <p>テナント削除は既存の全テナント参照 FK が CASCADE で従う確立済みセマンティクスであり、店舗授権行も店舗と共に消えるのが整合的。 修正前は
 * platform_user_stores→central_tenants の FK が既定の NO ACTION だったため、SPECIFIC_STORES ユーザーが
 * 授権している店舗を削除すると FK 違反でテナント削除自体が失敗していた（本 IT はその退行のガード）。
 *
 * <p>様式は {@link SeedSequenceAlignmentIT}（中央ログイン + JdbcTemplate による実 DB 断言）に倣う。使い捨て tmpfs DB のためシード
 * tenant 1 は決して削除せず、第二テナントを直挿して検証する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantDeletionCascadeIT {

  @Autowired private TestRestTemplate rest;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private String centralLogin() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/central/login",
            new HttpEntity<>("{\"username\": \"admin\", \"password\": \"pass\"}", headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 中央 admin でのログインが成功すること").isEqualTo(HttpStatus.OK);
    String token = res.getBody().path("token").asText();
    assertThat(token).isNotBlank();
    return token;
  }

  @Test
  @DisplayName("テナント削除で SPECIFIC_STORES ユーザーの店舗授権行が CASCADE 消去され、ユーザー本体は残ること")
  void deletingTenantCascadesStoreGrantButKeepsPlatformUser() {
    // 第二テナントを直挿（シード tenant 1 は他 IT が依存するため決して削除しない）。
    Tenant tenant =
        tenantRepository.save(
            new Tenant(
                "削除カスケード検証テナント", "tenant-delete-it-" + UUID.randomUUID() + ".kizuna.test", null));
    long storeId = tenant.getId();

    // その店舗のみを授権集合に持つ SPECIFIC_STORES ユーザーを直挿（ログインは行わないため password はエンコード済みダミー）。
    PlatformUser user =
        platformUserRepository.save(
            PlatformUser.builder()
                .email("cascade-it-" + UUID.randomUUID() + "@kizuna.test")
                .password(passwordEncoder.encode("pass"))
                .displayName("店舗授権カスケード検証")
                .enabled(true)
                .role(PlatformRole.STORE_MANAGER)
                .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of(storeId))
                .build());
    long userId = user.getId();

    // 前提: 削除前は授権行が 1 件存在する（空振りで緑にならないことを固定）。
    assertThat(countStoreGrants(storeId)).as("削除前は店舗授権行が存在すること").isEqualTo(1L);

    // 実削除フロー: 中央 admin トークンで DELETE /central/tenant/{id}。
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(centralLogin());
    ResponseEntity<Void> res =
        rest.exchange(
            "/central/tenant/" + storeId, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);

    // FK が ON DELETE CASCADE でなければテナント削除は FK 違反で失敗する（本修正前の退行）。
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    // 店舗授権行は店舗と共に消える。
    assertThat(countStoreGrants(storeId)).as("削除後は店舗授権行が CASCADE 消去されること").isZero();
    // ユーザー本体は残る（授権集合が空になるだけ = authorizes() 全 false の fail-closed）。
    assertThat(countPlatformUser(userId)).as("プラットフォームユーザー本体は残存すること").isEqualTo(1L);
  }

  private long countStoreGrants(long storeId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM platform_user_stores WHERE store_id = ?", Long.class, storeId);
  }

  private long countPlatformUser(long userId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM platform_users WHERE id = ?", Long.class, userId);
  }
}
