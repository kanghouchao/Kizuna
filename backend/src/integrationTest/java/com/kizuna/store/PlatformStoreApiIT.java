package com.kizuna.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
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
import tools.jackson.databind.JsonNode;

/**
 * /platform 命名空間へ統合した店舗 API の権限マトリクス・literal/{id} 共存・旧パス消滅を 本物の PostgreSQL で固定する統合テスト。
 *
 * <p>受入基準 3（stores/me・CRUD・stats・lookup の権限）、4（literal セグメントが {id} に吸われない）、 1（旧 /central・/tenant
 * パスの消滅）を対象とする。シードは v0.1.0（admin=HQ ALL_STORES はベースライン / 田中花子=店長 SPECIFIC{1,2} と
 * store1.kizuna.test=id 1 は demo コンテキスト）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PlatformStoreApiIT {

  /** ベースラインシードの HQ 管理者（ALL_STORES・PERM_STORE_MANAGE 保持）。 */
  private static final String HQ_EMAIL = "admin@kizuna.test";

  /** demo シードの店長（SPECIFIC_STORES {1,2}・STORE_VIEW 保持 / STORE_MANAGE 非保持）。 */
  private static final String MANAGER_EMAIL = "tanaka.hanako@kizuna.test";

  private static final String PASSWORD = "pass";

  /** demo シードの店舗1のドメイン（id=1）。 */
  private static final String STORE1_DOMAIN = "store1.kizuna.test";

  @Autowired private TestRestTemplate rest;

  private String platformToken(String email) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, PASSWORD),
                headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: %s の平台ログインが成功すること", email).isEqualTo(HttpStatus.OK);
    String token = res.getBody().path("token").asString();
    assertThat(token).isNotBlank();
    return token;
  }

  private static HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  private ResponseEntity<String> get(String path, String token) {
    return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
  }

  private static List<Long> idsOf(JsonNode array) {
    List<Long> ids = new ArrayList<>();
    array.forEach(node -> ids.add(node.path("id").asLong()));
    return ids;
  }

  @Test
  @DisplayName("POST /platform/stores は HQ 管理者で 204 になること")
  void hqAdminCanCreateStore() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(platformToken(HQ_EMAIL));
    // domain には一意制約（uq_t_stores_domain）があるため、
    // 同一 DB へ手動で再実行しても衝突しないよう実行ごとに一意化する
    String body =
        String.format(
            "{\"name\": \"店舗作成テスト\","
                + " \"domain\": \"store-create-it-%s.kizuna.test\","
                + " \"email\": \"store-create-it@kizuna.test\"}",
            UUID.randomUUID());

    ResponseEntity<Void> res =
        rest.postForEntity("/platform/stores", new HttpEntity<>(body, headers), Void.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  // ドメインは管理画面で店舗サイトへのリンクとして描画される。`/` `:` `@` を通すと
  // `正規.example@攻撃者.example` が別ホストへの導線になるため、保存の手前で閉じる。
  @Test
  @DisplayName("POST /platform/stores はホスト名の形でないドメインを 400 で拒むこと")
  void createStoreRejectsNonHostnameDomain() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(platformToken(HQ_EMAIL));
    String body =
        String.format(
            "{\"name\": \"店舗作成テスト\","
                + " \"domain\": \"store-create-it-%s.kizuna.test@evil.example\","
                + " \"email\": \"store-create-it@kizuna.test\"}",
            UUID.randomUUID());

    ResponseEntity<String> res =
        rest.postForEntity("/platform/stores", new HttpEntity<>(body, headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // ドメインは Host ヘッダによる店舗文脈の解決キー。ブラウザは Host を小文字で送るのに
  // 照合は大小を区別するため、大文字を呑むと誰も到達できない店舗ができる。
  @Test
  @DisplayName("POST /platform/stores は大文字を含むドメインを 400 で拒むこと")
  void createStoreRejectsUppercaseDomain() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(platformToken(HQ_EMAIL));
    String body =
        String.format(
            "{\"name\": \"店舗作成テスト\","
                + " \"domain\": \"Store-Create-IT-%s.Kizuna.test\","
                + " \"email\": \"store-create-it@kizuna.test\"}",
            UUID.randomUUID());

    ResponseEntity<String> res =
        rest.postForEntity("/platform/stores", new HttpEntity<>(body, headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // DNS のラベル上限は 63 文字。64 文字以上のラベルは名前解決できず、全体長の Size(max=128) だけでは
  // 到達不能な店舗の保存を防げないため、ラベル単位でも境界を閉じる。
  @Test
  @DisplayName("POST /platform/stores は 64 文字のラベルを含むドメインを 400 で拒むこと")
  void createStoreRejectsDomainWithOverlongLabel() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(platformToken(HQ_EMAIL));
    String overlongLabel = "a".repeat(64);
    String body =
        String.format(
            "{\"name\": \"店舗作成テスト\","
                + " \"domain\": \"%s.kizuna.test\","
                + " \"email\": \"store-create-it@kizuna.test\"}",
            overlongLabel);

    ResponseEntity<String> res =
        rest.postForEntity("/platform/stores", new HttpEntity<>(body, headers), String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // 上の境界の反対側: 63 文字ちょうどのラベルは解決可能なので通ること（正規表現の off-by-one を防ぐ）。
  @Test
  @DisplayName("POST /platform/stores は 63 文字のラベルを含むドメインを受け付けること")
  void createStoreAcceptsDomainWithMaxLengthLabel() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(platformToken(HQ_EMAIL));
    // UUID 由来の断片で一意化しつつ、末尾を 'a' で埋めて 63 文字ちょうどにする
    String unique = UUID.randomUUID().toString().replace("-", "");
    String maxLabel = (unique + "a".repeat(63)).substring(0, 63);
    String body =
        String.format(
            "{\"name\": \"店舗作成テスト\","
                + " \"domain\": \"%s.kizuna.test\","
                + " \"email\": \"store-create-it@kizuna.test\"}",
            maxLabel);

    ResponseEntity<Void> res =
        rest.postForEntity("/platform/stores", new HttpEntity<>(body, headers), Void.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  // 編集画面は name と email を送る。更新 DTO が受け取らない項目は
  // fail-on-unknown-properties: false により黙って捨てられ、成功応答だけが返る。
  // 読み戻しは storeByDomain キャッシュを経ない /{id}（編集画面が実際に読む経路）で行う。
  @Test
  @DisplayName("PUT /platform/stores/{id} は email だけの変更を永続化すること")
  void updateStorePersistsEmail() {
    String hq = platformToken(HQ_EMAIL);
    String unique = UUID.randomUUID().toString();
    String name = "store-it-update-email-" + unique;
    String id =
        createStore(
            hq, name, "store-it-update-email-" + unique + ".kizuna.test", "before@kizuna.test");

    HttpHeaders headers = bearer(hq);
    headers.setContentType(MediaType.APPLICATION_JSON);
    String body = String.format("{\"name\": \"%s\", \"email\": \"after@kizuna.test\"}", name);
    ResponseEntity<Void> updated =
        rest.exchange(
            "/platform/stores/" + id, HttpMethod.PUT, new HttpEntity<>(body, headers), Void.class);
    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<JsonNode> reloaded =
        rest.exchange(
            "/platform/stores/" + id, HttpMethod.GET, new HttpEntity<>(bearer(hq)), JsonNode.class);
    assertThat(reloaded.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(reloaded.getBody().path("email").asString()).isEqualTo("after@kizuna.test");
    assertThat(reloaded.getBody().path("name").asString()).as("name は据え置きのまま").isEqualTo(name);
  }

  // email を伴わない更新要求で email が null に上書きされないこと。
  // PUT は全項目の置換であり、必須項目の欠落は保存の手前で拒む。
  @Test
  @DisplayName("PUT /platform/stores/{id} は email を欠く要求を 400 で拒むこと")
  void updateStoreRejectsMissingEmail() {
    String hq = platformToken(HQ_EMAIL);
    String unique = UUID.randomUUID().toString();
    String name = "store-it-update-noemail-" + unique;
    String id =
        createStore(
            hq, name, "store-it-update-noemail-" + unique + ".kizuna.test", "keep@kizuna.test");

    HttpHeaders headers = bearer(hq);
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<String> res =
        rest.exchange(
            "/platform/stores/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(String.format("{\"name\": \"%s\"}", name), headers),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    ResponseEntity<JsonNode> reloaded =
        rest.exchange(
            "/platform/stores/" + id, HttpMethod.GET, new HttpEntity<>(bearer(hq)), JsonNode.class);
    assertThat(reloaded.getBody().path("email").asString())
        .as("拒まれた要求で既存の email が消えないこと")
        .isEqualTo("keep@kizuna.test");
  }

  /** 店舗を作成し、その id を返す。POST は 204 で本文を返さないため、一意な name で検索して引く。 */
  private String createStore(String token, String name, String domain, String email) {
    HttpHeaders headers = bearer(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    String body =
        String.format(
            "{\"name\": \"%s\", \"domain\": \"%s\", \"email\": \"%s\"}", name, domain, email);
    ResponseEntity<Void> created =
        rest.postForEntity("/platform/stores", new HttpEntity<>(body, headers), Void.class);
    assertThat(created.getStatusCode()).as("前提: 店舗が作成されること").isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<JsonNode> found =
        rest.exchange(
            "/platform/stores?search=" + name,
            HttpMethod.GET,
            new HttpEntity<>(bearer(token)),
            JsonNode.class);
    assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(found.getBody().path("content").size()).as("前提: 作成した店舗が一意に引けること").isEqualTo(1);
    return found.getBody().path("content").get(0).path("id").asString();
  }

  @Test
  @DisplayName("GET /platform/stores/lookup?domain= は未認証で 200 と店舗情報を返すこと（受入基準3）")
  void lookupByDomainIsPublic() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/stores/lookup?domain=" + STORE1_DOMAIN,
            HttpMethod.GET,
            HttpEntity.EMPTY,
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().path("domain").asString()).isEqualTo(STORE1_DOMAIN);
  }

  @Test
  @DisplayName(
      "GET /platform/stores/lookup は陳腐な Bearer が付いていても 200 を返すこと"
          + "（PlatformBearerTokenResolver が公開端点で坏 token を無視する）")
  void lookupByDomainIgnoresBrokenBearer() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, "Bearer stale-or-broken-token-value");

    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/stores/lookup?domain=" + STORE1_DOMAIN,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody().path("domain").asString()).isEqualTo(STORE1_DOMAIN);
  }

  @Test
  @DisplayName("GET /platform/stores/lookup は domain 未指定なら未認証でも 400 になること（params 分岐廃止・独立子路径化の確認）")
  void lookupWithoutDomainIsBadRequest() {
    // /lookup は独立子路径のため、必須 @RequestParam の欠落として CommonExceptionHandler が 400 へ映射する。
    ResponseEntity<String> res =
        rest.exchange("/platform/stores/lookup", HttpMethod.GET, HttpEntity.EMPTY, String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("GET /platform/stores は STORE_MANAGE 無しトークン(店長)で 403、HQ 管理者で 200 になること（受入基準3）")
  void storeManagementListRequiresStoreManage() {
    assertThat(get("/platform/stores", platformToken(MANAGER_EMAIL)).getStatusCode())
        .as("STORE_MANAGE を持たない店長は CRUD 一覧に到達できない")
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(get("/platform/stores", platformToken(HQ_EMAIL)).getStatusCode())
        .as("STORE_MANAGE を持つ HQ 管理者は CRUD 一覧に到達できる")
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("GET /platform/stores の一覧応答は他一覧と同形の Spring Page 形（0 起点）であること")
  void storeListUsesSpringPageShape() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/stores?page=0&size=10",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(HQ_EMAIL))),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = res.getBody();
    assertThat(body.path("content").isArray()).as("content 配列を持つこと").isTrue();
    assertThat(body.path("content").size()).as("シード店舗が載ること").isGreaterThan(0);
    assertThat(body.path("number").asInt()).as("ページ番号は 0 起点であること").isEqualTo(0);
    assertThat(body.path("total_elements").asLong()).isGreaterThan(0);
    assertThat(body.path("total_pages").asInt()).isGreaterThan(0);
    assertThat(body.has("data")).as("Laravel 風封筒の data キーが消えていること").isFalse();
    assertThat(body.has("current_page")).as("Laravel 風封筒の current_page キーが消えていること").isFalse();
  }

  // findByNameContainingIgnoreCase 系の派生クエリは Spring Data JPA が _ % を既定でエスケープする
  // （EscapeCharacter.DEFAULT）。手書き like への置き換えでこの保証を失わないよう固定する。
  @Test
  @DisplayName("GET /platform/stores の検索語中の LIKE メタ文字は字面として扱われ、ワイルドカードにならないこと")
  void searchTreatsLikeMetacharactersAsLiterals() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(platformToken(HQ_EMAIL));
    String uniqueSuffix = UUID.randomUUID().toString();
    String name = "store-it-likeesc-acb-" + uniqueSuffix;
    String body =
        String.format(
            "{\"name\": \"%s\","
                + " \"domain\": \"store-it-likeesc-%s.kizuna.test\","
                + " \"email\": \"store-it-likeesc@kizuna.test\"}",
            name, uniqueSuffix);

    ResponseEntity<Void> created =
        rest.postForEntity("/platform/stores", new HttpEntity<>(body, headers), Void.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    String wildcardSearch = name.replace("acb", "a_b");
    ResponseEntity<JsonNode> underscore =
        rest.exchange(
            "/platform/stores?search=" + wildcardSearch,
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(HQ_EMAIL))),
            JsonNode.class);
    assertThat(underscore.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(underscore.getBody().path("total_elements").asLong()).isZero();

    ResponseEntity<JsonNode> literal =
        rest.exchange(
            "/platform/stores?search=" + name,
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(HQ_EMAIL))),
            JsonNode.class);
    assertThat(literal.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(literal.getBody().path("total_elements").asLong()).isEqualTo(1);
  }

  // /lookup は @Cacheable("storeByDomain") 経由。更新でキャッシュを失効させないと、未認証で叩ける
  // 公開照会が古い StoreVO を返し続ける（frontend の middleware が店舗解決に使う経路）。
  // StoreVO は name と email を含むため、更新できる項目はいずれも陳腐化する。
  @Test
  @DisplayName("PUT /platform/stores/{id} の後、GET /platform/stores/lookup が更新後の値を返すこと")
  void updateInvalidatesDomainLookupCache() {
    String hq = platformToken(HQ_EMAIL);
    String unique = UUID.randomUUID().toString();
    String originalName = "store-it-cache-evict-" + unique;
    String domain = "store-it-cache-evict-" + unique + ".kizuna.test";
    String id = createStore(hq, originalName, domain, "before@kizuna.test");

    // 更新前に一度引いてキャッシュへ載せる（これを省くと失効の有無を判定できない）
    ResponseEntity<JsonNode> beforeUpdate =
        rest.exchange(
            "/platform/stores/lookup?domain=" + domain,
            HttpMethod.GET,
            HttpEntity.EMPTY,
            JsonNode.class);
    assertThat(beforeUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(beforeUpdate.getBody().path("name").asString()).isEqualTo(originalName);
    assertThat(beforeUpdate.getBody().path("email").asString()).isEqualTo("before@kizuna.test");

    HttpHeaders headers = bearer(hq);
    headers.setContentType(MediaType.APPLICATION_JSON);
    String updatedName = originalName + "-updated";
    String body =
        String.format("{\"name\": \"%s\", \"email\": \"after@kizuna.test\"}", updatedName);
    ResponseEntity<Void> updated =
        rest.exchange(
            "/platform/stores/" + id, HttpMethod.PUT, new HttpEntity<>(body, headers), Void.class);
    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<JsonNode> afterUpdate =
        rest.exchange(
            "/platform/stores/lookup?domain=" + domain,
            HttpMethod.GET,
            HttpEntity.EMPTY,
            JsonNode.class);
    assertThat(afterUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(afterUpdate.getBody().path("name").asString())
        .as("公開照会が更新後の店舗名を返すこと")
        .isEqualTo(updatedName);
    assertThat(afterUpdate.getBody().path("email").asString())
        .as("公開照会が更新後の email を返すこと")
        .isEqualTo("after@kizuna.test");
  }

  @Test
  @DisplayName("GET /platform/stores/me は店長トークンで 200 と授権店舗(1,2)のみを返すこと（受入基準3）")
  void meReturnsAuthorizedStoresOnly() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/stores/me",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(MANAGER_EMAIL))),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(idsOf(res.getBody())).as("店長の授権店舗集合(1,2)のみが返ること").containsExactly(1L, 2L);
  }

  @Test
  @DisplayName("literal セグメント(/me,/stats,/lookup)は /{id} に吸われず、/{id} は実在 id で 200 を返すこと（受入基準4）")
  void literalSegmentsCoexistWithIdPath() {
    String hq = platformToken(HQ_EMAIL);

    // /{id}: 実在店舗 id=1 は getById が 200 で解決する。
    ResponseEntity<JsonNode> byId =
        rest.exchange(
            "/platform/stores/1", HttpMethod.GET, new HttpEntity<>(bearer(hq)), JsonNode.class);
    assertThat(byId.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(byId.getBody().path("id").asString()).isEqualTo("1");
    assertThat(byId.getBody().path("domain").asString()).isEqualTo(STORE1_DOMAIN);

    // literal は {id} より優先されるため、それぞれ専用ハンドラに解決され 200 になる
    // （{id} に吸われれば getById("stats"/"me") が 404 になり区別できる）。
    assertThat(get("/platform/stores/stats", hq).getStatusCode())
        .as("/stats が getById({id}) に吸われないこと")
        .isEqualTo(HttpStatus.OK);
    assertThat(get("/platform/stores/me", hq).getStatusCode())
        .as("/me が getById({id}) に吸われないこと")
        .isEqualTo(HttpStatus.OK);
    ResponseEntity<String> lookup =
        rest.exchange(
            "/platform/stores/lookup?domain=" + STORE1_DOMAIN,
            HttpMethod.GET,
            HttpEntity.EMPTY,
            String.class);
    assertThat(lookup.getStatusCode())
        .as("/lookup が getById({id}) に吸われないこと")
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("旧 /central/tenants と旧 /tenant/orders は 404 で消滅していること（受入基準1）")
  void legacyPathsAreGone() {
    String hq = platformToken(HQ_EMAIL);

    assertThat(get("/central/tenants", hq).getStatusCode())
        .as("中央名前空間の旧テナント一覧は消滅していること")
        .isEqualTo(HttpStatus.NOT_FOUND);

    HttpHeaders storeContext = bearer(hq);
    storeContext.set("X-Role", "store");
    storeContext.set("X-Store-ID", "1");
    ResponseEntity<String> orders =
        rest.exchange(
            "/tenant/orders", HttpMethod.GET, new HttpEntity<>(storeContext), String.class);
    assertThat(orders.getStatusCode())
        .as("店舗文脈ヘッダを付けても旧 /tenant 名前空間は消滅していること")
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
