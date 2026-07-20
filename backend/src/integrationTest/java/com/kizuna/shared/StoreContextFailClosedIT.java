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
 * 公開エンドポイントの店舗未解決時 fail-closed 化（#287）を本物の PostgreSQL/Redis で検証する統合テスト。
 *
 * <p>X-Store-ID ヘッダから店舗文脈を解決できないリクエストが、{@code @StoreOptional} の無いエンドポイント（casts/public）では 403
 * で拒否されることを固定する。
 *
 * <p><b>漏洩検証データに関する注記</b>: {@code t_casts.store_id} は {@code t_stores(id)} への外部キーを持ち、シードには store 1
 * しか存在しない（store 2 のデータは #285 の interceptor がクロス店舗ヘッダを拒否するため API 経由でも作れない）。 よって漏洩の的には store A
 * のアクティブなキャストを用いる。文脈を持たない匿名リクエストに対してそのデータが返らないことが、 fail-open（文脈未設定で全店舗行が返る）を閉じたことの実証になる。
 */
class StoreContextFailClosedIT extends CrossStoreTestSupport {

  private static final String CASTS_PUBLIC = "/store/casts/public";

  /** 認証・店舗文脈を一切持たない完全匿名ヘッダ。 */
  private HttpHeaders anonymous() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private void createActiveCastAs(long storeId, String name) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", storeHeaders(storeId)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful())
        .as("前提: store %d でのアクティブなキャスト作成が成功すること", storeId)
        .isTrue();
  }

  @Test
  @DisplayName("完全匿名で GET /store/casts/public を叩くと 403 になり、既存キャストのデータが漏れないこと")
  void anonymousPublicCastsIsForbiddenAndLeaksNoData() {
    String castName = "漏洩検証キャスト_" + UUID.randomUUID();
    createActiveCastAs(STORE_A, castName);

    // 正向対照: 適切な店舗文脈（X-Role/X-Store-ID）があれば公開エンドポイントは 200 でデータを返す。
    HttpHeaders storeContextHeaders = new HttpHeaders();
    storeContextHeaders.set("X-Role", "store");
    storeContextHeaders.set("X-Store-ID", String.valueOf(STORE_A));
    ResponseEntity<String> withContext =
        rest.exchange(
            CASTS_PUBLIC, HttpMethod.GET, new HttpEntity<>(storeContextHeaders), String.class);
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
  @DisplayName("匿名 + X-Store-ID が long 桁あふれの GET /store/casts/public は 500 でなく 400 になること（#288）")
  void anonymousOverflowingStoreIdHeaderReturns400() {
    HttpHeaders headers = anonymous();
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", "99999999999999999999");

    ResponseEntity<String> res =
        rest.exchange(CASTS_PUBLIC, HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
