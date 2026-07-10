package com.kizuna.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 公開エンドポイントのテナント未解決時 fail-closed 化（#287）を本物の PostgreSQL/Redis で検証する統合テスト。
 *
 * <p>JWT の tenantId claim・X-Tenant-ID ヘッダのいずれからもテナント文脈を解決できないリクエストが、{@code @TenantOptional}
 * の無いエンドポイント（casts/public・login）では 403 で拒否され、{@code @TenantOptional}
 * 付きの例外（logout・init-admin-user）では 403 にならないことを固定する。
 *
 * <p><b>CSRF に関する注記</b>: logout / init-admin-user への「完全匿名（Authorization も無し）」な POST は、CsrfFilter が
 * interceptor より先に 403 を返す（両者は CSRF 免除パスに含まれず、既定の XorCsrfTokenRequestAttributeHandler は トークン無しの
 * POST を拒否する）。interceptor の {@code @TenantOptional} 挙動そのものを分離検証するため、これらのリクエストには 解析不能な Bearer
 * トークンのみを付与する — Bearer の存在で CSRF 免除となり（本番の logout も失効対象トークンを Bearer で運ぶ）、
 * かつ解析不能なため認証は成立せず、テナント文脈は未解決のまま interceptor に到達する。X-Role/X-Tenant-ID は一切付けない。
 *
 * <p><b>漏洩検証データに関する注記</b>: {@code t_casts.tenant_id} は {@code central_tenants(id)} への外部キーを持ち、シードには
 * tenant 1 しか存在しない（tenant 2 のデータは #285 の interceptor がクロステナントヘッダを拒否するため API 経由でも作れない）。 よって漏洩の的には
 * tenant A のアクティブなキャストを用いる。文脈を持たない匿名リクエストに対してそのデータが返らないことが、 fail-open（文脈未設定で全テナント行が返る）を閉じたことの実証になる。
 */
class TenantContextFailClosedIT extends CrossTenantTestSupport {

  private static final String CASTS_PUBLIC = "/tenant/casts/public";

  /** 認証・テナント文脈を一切持たない完全匿名ヘッダ。 */
  private HttpHeaders anonymous() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  /** 解析不能な Bearer のみを持つヘッダ（CSRF 免除のためだけ。認証は成立せずテナント文脈は未解決のまま）。 */
  private HttpHeaders bearerOnly() {
    HttpHeaders headers = anonymous();
    headers.setBearerAuth("not-a-valid-jwt");
    return headers;
  }

  private void createActiveCastAs(long tenantId, String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/tenant/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", tenantHeaders(tenantId)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful())
        .as("前提: tenant %d でのアクティブなキャスト作成が成功すること", tenantId)
        .isTrue();
  }

  @Test
  @DisplayName("完全匿名で GET /tenant/casts/public を叩くと 403 になり、既存キャストのデータが漏れないこと")
  void anonymousPublicCastsIsForbiddenAndLeaksNoData() {
    String castName = "漏洩検証キャスト_" + UUID.randomUUID();
    createActiveCastAs(TENANT_A, castName);

    // 正向対照: 適切なテナント文脈（X-Role/X-Tenant-ID）があれば公開エンドポイントは 200 でデータを返す。
    HttpHeaders tenantContextHeaders = new HttpHeaders();
    tenantContextHeaders.set("X-Role", "tenant");
    tenantContextHeaders.set("X-Tenant-ID", String.valueOf(TENANT_A));
    ResponseEntity<String> withContext =
        rest.exchange(
            CASTS_PUBLIC, HttpMethod.GET, new HttpEntity<>(tenantContextHeaders), String.class);
    assertThat(withContext.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(withContext.getBody()).contains(castName);

    // 負向: 文脈を一切持たない匿名リクエストは 403、かつ本文にキャストデータが漏れない。
    ResponseEntity<String> anonymous =
        rest.exchange(CASTS_PUBLIC, HttpMethod.GET, new HttpEntity<>(anonymous()), String.class);
    assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    // interceptor が false を返すため本文は空（null）になる。空であれば当然データは漏れていないが、
    // 防御的に castName が含まれないことも確認する（null 安全に判定する）。
    String body = anonymous.getBody();
    assertThat(body == null || !body.contains(castName)).as("匿名リクエストの本文にキャストデータが漏れないこと").isTrue();
  }

  @Test
  @DisplayName("テナント文脈を解決できない POST /tenant/login は 403 で拒否されること")
  void anonymousLoginIsForbidden() {
    ResponseEntity<String> res =
        rest.postForEntity(
            "/tenant/login",
            new HttpEntity<>(
                "{\"username\": \"admin@store1.kizuna.com\", \"password\": \"pass\"}", anonymous()),
            String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("テナント文脈を解決できなくても @TenantOptional の POST /tenant/logout は 403 にならず正常応答すること")
  void logoutIsNotForbiddenWithoutTenantContext() {
    ResponseEntity<String> res =
        rest.postForEntity("/tenant/logout", new HttpEntity<>(bearerOnly()), String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  @DisplayName("テナント文脈を解決できなくても @TenantOptional の POST /tenant/init-admin-user は 403 にならないこと")
  void initAdminUserIsNotForbiddenWithoutTenantContext() {
    ResponseEntity<String> res =
        rest.postForEntity(
            "/tenant/init-admin-user",
            new HttpEntity<>(
                "{\"token\": \"invalid-registration-token-invalid-registration-token\", "
                    + "\"email\": \"newadmin@example.com\", \"password\": \"password123\"}",
                bearerOnly()),
            String.class);
    // 無効な登録トークンにより業務エラーにはなるが、interceptor による 403（fail-closed）ではないこと。
    assertThat(res.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
  }
}
