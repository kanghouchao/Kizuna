package com.kizuna.cast;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastFieldDefinitionRepository;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.CrossTenantTestSupport;
import java.util.Map;
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

/**
 * カスタムフィールド定義・値のクロステナント分離を本物の PostgreSQL で検証する統合テスト（issue #277）。
 *
 * <p>新規テーブル {@code t_cast_field_definitions} と {@code t_casts.custom_fields} に storeFilter が実際に効くこと
 * （#227 の applyToLoadByKey 型の穴が無いこと）を、リポジトリ直挿し＋実データ非混入の強アサーションで固定する
 * （弱い「所有者不一致」ではなく他テナントの値・ラベルが本文に一切現れないことを断言）。
 *
 * <p>定義 CRUD は {@code ROLE_STORE_MANAGER} 限定のため、店舗{1,2} 授権の店長シードユーザー tanaka.hanako を使う。 tanaka
 * は両テナントに授権されるため、越境は「インターセプタのスコープ拒否 403」ではなく「storeFilter による不可視化 → 400（見つからない）」として現れる。純粋なヘッダ詐称 403
 * は基底クラスの yamada（店舗{1} 授権）で別途固定する。
 */
class CastFieldDefinitionCrossTenantIT extends CrossTenantTestSupport {

  @Autowired private CastFieldDefinitionRepository fieldDefinitionRepository;
  @Autowired private CastRepository castRepository;

  private String managerToken;
  private final long nonce = System.nanoTime();

  @BeforeEach
  void loginAsManager() {
    managerToken = loginAs("tanaka.hanako@kizuna.test");
  }

  private String loginAs(String email) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>("{\"email\": \"" + email + "\", \"password\": \"pass\"}", headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: %s でのログインが成功する", email).isEqualTo(HttpStatus.OK);
    return res.getBody().path("token").asText();
  }

  private HttpHeaders managerHeaders(long tenantId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(tenantId));
    headers.setBearerAuth(managerToken);
    return headers;
  }

  private String createDefinitionAs(long tenantId, String key, String label, boolean isPublic) {
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/store/casts/fields",
            new HttpEntity<>(
                "{\"key\": \""
                    + key
                    + "\", \"label\": \""
                    + label
                    + "\", \"is_public\": "
                    + isPublic
                    + "}",
                managerHeaders(tenantId)),
            JsonNode.class);
    assertThat(res.getStatusCode().is2xxSuccessful())
        .as(
            "前提: tenant %d で定義作成が成功する (status=%s body=%s)",
            tenantId, res.getStatusCode(), res.getBody())
        .isTrue();
    String id = res.getBody().path("id").asText();
    assertThat(id).isNotBlank();
    return id;
  }

  /** storeFilter を経由しない直挿しで第二テナント(=2)に定義を用意する（save で store_id を明示）。 */
  private CastFieldDefinition insertDefinitionForTenantB(
      String key, String label, boolean isPublic) {
    CastFieldDefinition definition =
        CastFieldDefinition.builder()
            .key(key)
            .label(label)
            .displayOrder(0)
            .isPublic(isPublic)
            .build();
    definition.setStoreId(TENANT_B);
    return fieldDefinitionRepository.save(definition);
  }

  private Cast insertActiveCastForTenantB(Map<String, String> customFields) {
    Cast cast =
        Cast.builder().name("テナントBキャスト").status("ACTIVE").customFields(customFields).build();
    cast.setStoreId(TENANT_B);
    return castRepository.save(cast);
  }

  @Test
  @DisplayName("他テナントの定義は storeFilter で不可視化され、GET一覧に現れず PUT/DELETE も 400 になること")
  void foreignTenantDefinitionIsInvisibleAndUnmutable() {
    String keyA = "blood_type_" + nonce;
    String idA = createDefinitionAs(TENANT_A, keyA, "血液型A", true);

    String keyB = "hobby_" + nonce;
    String labelB = "趣味B-" + nonce;
    String idB = insertDefinitionForTenantB(keyB, labelB, true).getId();

    // 一覧の非混入: tenant A の一覧に tenant B の key/label が一切現れない
    ResponseEntity<String> listA =
        rest.exchange(
            "/store/casts/fields",
            HttpMethod.GET,
            new HttpEntity<>(managerHeaders(TENANT_A)),
            String.class);
    assertThat(listA.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(listA.getBody()).contains(keyA).doesNotContain(keyB).doesNotContain(labelB);

    // 正向対照: tenant B の一覧では tenant B の定義が見える（idB が存在することの証明）
    ResponseEntity<String> listB =
        rest.exchange(
            "/store/casts/fields",
            HttpMethod.GET,
            new HttpEntity<>(managerHeaders(TENANT_B)),
            String.class);
    assertThat(listB.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(listB.getBody()).contains(keyB).contains(labelB);

    // 変更不可: tenant A 文脈で tenant B の定義を PUT/DELETE すると不可視で 400
    ResponseEntity<JsonNode> putForeign =
        rest.exchange(
            "/store/casts/fields/" + idB,
            HttpMethod.PUT,
            new HttpEntity<>("{\"label\": \"改ざん\"}", managerHeaders(TENANT_A)),
            JsonNode.class);
    assertThat(putForeign.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    ResponseEntity<JsonNode> deleteForeign =
        rest.exchange(
            "/store/casts/fields/" + idB,
            HttpMethod.DELETE,
            new HttpEntity<>(managerHeaders(TENANT_A)),
            JsonNode.class);
    assertThat(deleteForeign.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // データ不変: tenant B からは idB の定義がなお見える（改ざん・削除されていない）
    ResponseEntity<String> listBAfter =
        rest.exchange(
            "/store/casts/fields",
            HttpMethod.GET,
            new HttpEntity<>(managerHeaders(TENANT_B)),
            String.class);
    assertThat(listBAfter.getBody()).contains(labelB);

    // 正向対照: tenant A は自テナントの定義を更新できる（400 がバリデーション起因でない証明）
    ResponseEntity<JsonNode> putOwn =
        rest.exchange(
            "/store/casts/fields/" + idA,
            HttpMethod.PUT,
            new HttpEntity<>("{\"label\": \"血液型A更新\"}", managerHeaders(TENANT_A)),
            JsonNode.class);
    assertThat(putOwn.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("公開APIは tenant A の呼び出しに tenant B の公開カスタムフィールドの値・ラベルを一切含めないこと")
  void publicApiDoesNotLeakForeignTenantCustomFields() {
    String keyB = "blood_type_" + nonce;
    String labelB = "血液型B-" + nonce;
    String valueB = "AB型-" + nonce;
    insertDefinitionForTenantB(keyB, labelB, true);
    insertActiveCastForTenantB(Map.of(keyB, valueB));

    // 正向対照: tenant B の公開APIでは値・ラベルが現れる（漏れうるデータであることの証明）
    ResponseEntity<String> underB =
        rest.exchange(
            "/store/casts/public",
            HttpMethod.GET,
            new HttpEntity<>(managerHeaders(TENANT_B)),
            String.class);
    assertThat(underB.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(underB.getBody()).contains(labelB).contains(valueB);

    // 非混入: tenant A の公開APIには tenant B の値・ラベルが一切含まれない（実データそのものの非混入）
    ResponseEntity<String> underA =
        rest.exchange(
            "/store/casts/public",
            HttpMethod.GET,
            new HttpEntity<>(managerHeaders(TENANT_A)),
            String.class);
    assertThat(underA.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(underA.getBody()).doesNotContain(labelB).doesNotContain(valueB);
  }

  @Test
  @DisplayName("STORE_STAFF は定義一覧を取得できるが、定義の作成・更新・削除は 403 で拒否されること")
  void staffCanListDefinitionsButCannotMutateThem() {
    // 基底クラスの yamada.jiro（STORE_STAFF・店舗1授権、token）で自テナント(=1)を操作する。
    // 値入力には活きた定義の読み取りが必要なため一覧(GET)は STAFF にも許可する
    // （#277 裁定「値の入力自体は既存 PUT /store/casts/{id} のまま MANAGER+STAFF」）。
    // 一方、定義そのものの CRUD は構造変更のため ROLE_STORE_MANAGER 限定を維持する。

    ResponseEntity<String> list =
        rest.exchange(
            "/store/casts/fields",
            HttpMethod.GET,
            new HttpEntity<>(tenantHeaders(TENANT_A)),
            String.class);
    assertThat(list.getStatusCode()).as("STAFF は定義一覧を取得できる").isEqualTo(HttpStatus.OK);

    ResponseEntity<String> create =
        rest.exchange(
            "/store/casts/fields",
            HttpMethod.POST,
            new HttpEntity<>(
                "{\"key\": \"staff_denied_"
                    + nonce
                    + "\", \"label\": \"拒否\", \"is_public\": false}",
                tenantHeaders(TENANT_A)),
            String.class);
    assertThat(create.getStatusCode()).as("STAFF は定義を作成できない").isEqualTo(HttpStatus.FORBIDDEN);

    // 認可は method security でメソッド本体より前に効くため、存在しない id でも 403 が先行する。
    ResponseEntity<String> update =
        rest.exchange(
            "/store/casts/fields/nonexistent",
            HttpMethod.PUT,
            new HttpEntity<>("{\"label\": \"改ざん\"}", tenantHeaders(TENANT_A)),
            String.class);
    assertThat(update.getStatusCode()).as("STAFF は定義を更新できない").isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<String> delete =
        rest.exchange(
            "/store/casts/fields/nonexistent",
            HttpMethod.DELETE,
            new HttpEntity<>(tenantHeaders(TENANT_A)),
            String.class);
    assertThat(delete.getStatusCode()).as("STAFF は定義を削除できない").isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("他テナントを詐称したヘッダは定義エンドポイントでも 403 で拒否され、本文を返さないこと")
  void spoofedForeignTenantHeaderIsRejected() {
    // 基底クラスの yamada（店舗{1} 授権）で X-Store-ID: 2 を詐称 → インターセプタのスコープ検証が 403 で弾く。
    ResponseEntity<String> spoofed =
        rest.exchange(
            "/store/casts/fields",
            HttpMethod.GET,
            new HttpEntity<>(tenantHeaders(TENANT_B)),
            String.class);
    assertThat(spoofed.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(spoofed.getBody()).as("拒否時は本文を返さない").isNullOrEmpty();
  }
}
