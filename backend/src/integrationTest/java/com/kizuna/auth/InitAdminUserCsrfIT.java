package com.kizuna.auth;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * {@code POST /tenant/init-admin-user} が CSRF 除外の対象になっていることを、本物の PostgreSQL/Redis で検証する統合テスト（#292）。
 *
 * <p>{@code SecurityConfig} の CSRF 除外パスは以前 {@code /tenant/init-admin-use}（綴り違い）を指しており、正しいパス {@code
 * /tenant/init-admin-user} は除外されていなかった。既定の {@code XorCsrfTokenRequestAttributeHandler} はトークン無しの
 * POST を拒否するため、綴り違いのままだと {@code CsrfFilter} が interceptor より先に 403 を返す。
 *
 * <p>{@code TenantContextFailClosedIT} も同じエンドポイントを叩くが、あちらは Bearer ヘッダ付き（Bearer の存在による CSRF
 * 免除）なので、パス指定の除外が正しいかは検証できない。本テストは Authorization も CSRF トークンも一切持たない「完全匿名」の POST を送り、パス指定の CSRF
 * 除外そのものが効いていることを固定する。 無効な登録トークンにより業務エラーにはなるが、CSRF 由来の 403（fail-closed）ではないことを確認する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InitAdminUserCsrfIT {

  @Autowired private TestRestTemplate rest;

  @Test
  @DisplayName("CSRF トークンも Authorization も無い POST /tenant/init-admin-user が CSRF 由来の 403 にならないこと")
  void initAdminUserIsCsrfExemptOnCorrectPath() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<String> res =
        rest.postForEntity(
            "/tenant/init-admin-user",
            new HttpEntity<>(
                "{\"token\": \"invalid-registration-token-invalid-registration-token\", "
                    + "\"email\": \"newadmin@example.com\", \"password\": \"password123\"}",
                headers),
            String.class);

    assertThat(res.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
  }
}
