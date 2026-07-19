package com.kizuna.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * シード（明示 id 挿入）後に IDENTITY シーケンスが MAX(id) へ整合されることを本物の PostgreSQL で検証する統合テスト（issue #237）。
 *
 * <p>使い捨て tmpfs DB（{@code docker-compose.test.yml}）に対して毎回全新初期化された状態で走るため、issue の再現条件（seed が明示 id=1
 * を挿入し IDENTITY シーケンスが未進行）がそのまま成立する。changeset で setval 整合されていれば、中央 API からの初回テナント作成が リトライ不要で成功し、3
 * 表のシーケンスが全て MAX(id) を上回る。
 *
 * <p>{@link com.kizuna.shared.CrossTenantTestSupport} は店舗スタッフでの平台ログイン前提のため継承せず、HQ
 * 管理者での平台ログインを自前で行う。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeedSequenceAlignmentIT {

  /**
   * 明示 id を播種している autoIncrement 表（issue #237 の setval 対象）。 #409 で central_menus は統合メニュー化により廃止された。
   */
  private static final List<String> SEEDED_IDENTITY_TABLES = List.of("t_stores", "t_users");

  @Autowired private TestRestTemplate rest;
  @Autowired private JdbcTemplate jdbcTemplate;

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
  @DisplayName("全新 DB で中央 API から初回テナント作成がリトライ不要で 204 になること")
  void firstTenantCreationSucceedsOnFreshDb() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(platformLogin());
    // domain には一意制約（uq_t_stores_domain）があるため、
    // 同一 DB へ手動で再実行しても衝突しないよう実行ごとに一意化する
    String body =
        String.format(
            "{\"name\": \"シード整合テストテナント\","
                + " \"domain\": \"seed-seq-it-%s.kizuna.test\","
                + " \"email\": \"seed-seq-it@kizuna.test\"}",
            UUID.randomUUID());

    ResponseEntity<Void> res =
        rest.postForEntity("/central/tenant", new HttpEntity<>(body, headers), Void.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  @DisplayName("明示 id 播種の autoIncrement 2 表のシーケンスが全て MAX(id) を上回ること")
  void seededIdentitySequencesAreAligned() {
    for (String table : SEEDED_IDENTITY_TABLES) {
      // 使い捨て DB なので nextval の消費は無害。nextval > MAX(id) は実行順に依存しない述語。
      String sql =
          String.format(
              "SELECT nextval(pg_get_serial_sequence('%1$s','id')) > (SELECT MAX(id) FROM %1$s)",
              table);
      Boolean aligned = jdbcTemplate.queryForObject(sql, Boolean.class);
      assertThat(aligned).as("%s のシーケンスが MAX(id) 以下のままになっている", table).isTrue();
    }
  }
}
