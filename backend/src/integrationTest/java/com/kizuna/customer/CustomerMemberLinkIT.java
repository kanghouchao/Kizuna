package com.kizuna.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.CrossStoreTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 会員コードによる顧客台帳への紐づけを本物の PostgreSQL で検証する統合テスト。
 *
 * <p>紐づけ・変更・解除がいずれも履歴として残り（実行者・日時が引ける）、変更が中間状態を作らずに 旧区間の解除と新区間の作成をまとめて行うこと、CRM 一覧・詳細に関連状態が投影されることを
 * HTTP 境界で固定する。
 *
 * <p>越境の確認は 2 種類ある。基底クラスの yamada（店舗1 授権）は他店舗ヘッダ自体を拒否されるので 403 になり、これはインターセプタの検証。 対して店舗{1,2} 授権の店長
 * tanaka は両店舗の文脈を確立できるため、同一会員を店舗ごとに別々の顧客へ紐づけられること（会員の一意性が 店舗内に閉じていること）を確かめられる。
 */
class CustomerMemberLinkIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "password1234";

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("会員コードで紐づけると一覧・詳細に紐づけ状態と会員コードが投影されること")
  void linkIsProjectedOntoListAndDetail() {
    // 一覧はクエリ文字列で絞り込むため、顧客名は ASCII の一意な字面にする
    String name = "cml-projected-" + nonce;
    String customerId = createCustomer(STORE_A, name);
    String memberCode = registerMember("link-projected");

    ResponseEntity<JsonNode> linked = link(STORE_A, customerId, memberCode, token);

    assertThat(linked.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(linked.getBody().path("linked").asBoolean()).isTrue();
    assertThat(linked.getBody().path("member_code").asString()).isEqualTo(memberCode);
    assertThat(linked.getBody().path("linked_at").asString()).isNotBlank();

    ResponseEntity<JsonNode> detail =
        rest.exchange(
            "/store/customers/" + customerId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(detail.getBody().path("member_linked").asBoolean()).isTrue();
    assertThat(detail.getBody().path("linked_member_code").asString()).isEqualTo(memberCode);

    ResponseEntity<JsonNode> list =
        rest.exchange(
            "/store/customers?search=" + name,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    JsonNode row = list.getBody().path("content").get(0);
    assertThat(row.path("id").asString()).isEqualTo(customerId);
    assertThat(row.path("member_linked").asBoolean()).isTrue();
    assertThat(row.path("linked_member_code").asString()).isEqualTo(memberCode);
  }

  @Test
  @DisplayName("未紐づけの顧客でも member_linked は真偽値で返り、会員コードは載らないこと")
  void unlinkedCustomerCarriesFalseFlag() {
    String customerId = createCustomer(STORE_A, "未紐づけ顧客-" + nonce);

    ResponseEntity<JsonNode> detail =
        rest.exchange(
            "/store/customers/" + customerId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);

    assertThat(detail.getBody().path("member_linked").isBoolean()).isTrue();
    assertThat(detail.getBody().path("member_linked").asBoolean()).isFalse();
    // 応答は non_null 包含のため未紐づけでは項目ごと落ちる。値が無いことだけを見る
    assertThat(detail.getBody().hasNonNull("linked_member_code")).isFalse();
  }

  @Test
  @DisplayName("解除すると一覧の投影が外れ、履歴に解除の実行者と日時が残ること")
  void unlinkKeepsHistoryWithActor() {
    String customerId = createCustomer(STORE_A, "解除顧客-" + nonce);
    String memberCode = registerMember("link-unlink");
    link(STORE_A, customerId, memberCode, token);

    ResponseEntity<JsonNode> released =
        rest.exchange(
            memberLinkPath(customerId),
            HttpMethod.DELETE,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(released.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<JsonNode> detail =
        rest.exchange(
            "/store/customers/" + customerId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(detail.getBody().path("member_linked").asBoolean()).isFalse();

    JsonNode history = history(STORE_A, customerId, token).getBody();
    assertThat(history).hasSize(1);
    JsonNode row = history.get(0);
    assertThat(row.path("member_code").asString()).isEqualTo(memberCode);
    assertThat(row.path("status").asString()).isEqualTo("RELEASED");
    assertThat(row.path("linked_by_name").asString()).isEqualTo("山田次郎");
    assertThat(row.path("linked_at").asString()).isNotBlank();
    assertThat(row.path("released_by_name").asString()).isEqualTo("山田次郎");
    assertThat(row.path("released_at").asString()).isNotBlank();
  }

  @Test
  @DisplayName("別会員への変更は 1 要求で旧区間を解除し新区間を作り、履歴が新→旧の順に 2 件になること")
  void switchingMemberLeavesOrderedHistory() {
    String customerId = createCustomer(STORE_A, "変更顧客-" + nonce);
    String firstCode = registerMember("link-switch-1");
    String secondCode = registerMember("link-switch-2");
    link(STORE_A, customerId, firstCode, token);

    ResponseEntity<JsonNode> switched = link(STORE_A, customerId, secondCode, token);
    assertThat(switched.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(switched.getBody().path("member_code").asString()).isEqualTo(secondCode);

    JsonNode history = history(STORE_A, customerId, token).getBody();
    assertThat(history).hasSize(2);
    // 新しい区間が先頭。旧区間は削除されず RELEASED として残る。
    assertThat(history.get(0).path("member_code").asString()).isEqualTo(secondCode);
    assertThat(history.get(0).path("status").asString()).isEqualTo("ACTIVE");
    assertThat(history.get(0).path("linked_by_name").asString()).isEqualTo("山田次郎");
    assertThat(history.get(0).hasNonNull("released_at")).isFalse();
    assertThat(history.get(1).path("member_code").asString()).isEqualTo(firstCode);
    assertThat(history.get(1).path("status").asString()).isEqualTo("RELEASED");
    assertThat(history.get(1).path("released_by_name").asString()).isEqualTo("山田次郎");

    // 変更後の投影は新しい会員コードのみ
    ResponseEntity<JsonNode> detail =
        rest.exchange(
            "/store/customers/" + customerId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(detail.getBody().path("linked_member_code").asString()).isEqualTo(secondCode);
  }

  @Test
  @DisplayName("同じ会員をもう一度紐づけると 409 になること")
  void relinkingSameMemberConflicts() {
    String customerId = createCustomer(STORE_A, "重複顧客-" + nonce);
    String memberCode = registerMember("link-dup");
    link(STORE_A, customerId, memberCode, token);

    assertThat(link(STORE_A, customerId, memberCode, token).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  @DisplayName("同一店舗で他の顧客に紐づいている会員は紐づけられないこと（409）")
  void memberTakenByAnotherCustomerConflicts() {
    String firstCustomer = createCustomer(STORE_A, "占有顧客-" + nonce);
    String secondCustomer = createCustomer(STORE_A, "後着顧客-" + nonce);
    String memberCode = registerMember("link-taken");
    link(STORE_A, firstCustomer, memberCode, token);

    assertThat(link(STORE_A, secondCustomer, memberCode, token).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  @DisplayName("数字 12 桁でない会員コードは 400 で拒否されること")
  void malformedMemberCodeIsRejected() {
    String customerId = createCustomer(STORE_A, "書式顧客-" + nonce);

    assertThat(link(STORE_A, customerId, "12345", token).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(link(STORE_A, customerId, "abcdefghijkl", token).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("存在しない会員コード・存在しない顧客・紐づけの無い解除はいずれも 404 になること")
  void unknownTargetsAreNotFound() {
    String customerId = createCustomer(STORE_A, "不在顧客-" + nonce);

    assertThat(link(STORE_A, customerId, "000000000000", token).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(link(STORE_A, "no-such-customer", registerMember("link-404"), token).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            rest.exchange(
                    memberLinkPath(customerId),
                    HttpMethod.DELETE,
                    new HttpEntity<>(storeHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("授権外店舗のヘッダでは紐づけ端点に到達できないこと（403）")
  void foreignStoreHeaderIsRejected() {
    String customerId = createCustomer(STORE_A, "越境顧客-" + nonce);
    String memberCode = registerMember("link-foreign");

    // yamada は店舗1 のみ授権のため、店舗2 を名乗った時点でインターセプタが拒否する
    assertThat(link(STORE_B, customerId, memberCode, token).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(history(STORE_B, customerId, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("同一会員を店舗ごとに別々の顧客へ紐づけられること（一意性は店舗内に閉じる）")
  void sameMemberCanBeLinkedInEachStore() {
    String managerToken = loginAs("tanaka.hanako@kizuna.test");
    String customerInA = createCustomerAs(STORE_A, "跨店顧客A-" + nonce, managerToken);
    String customerInB = createCustomerAs(STORE_B, "跨店顧客B-" + nonce, managerToken);
    String memberCode = registerMember("link-both-stores");

    assertThat(link(STORE_A, customerInA, memberCode, managerToken).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(link(STORE_B, customerInB, memberCode, managerToken).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    // 店舗Aの履歴には店舗Bの区間が混ざらない
    assertThat(history(STORE_A, customerInA, managerToken).getBody()).hasSize(1);
    assertThat(history(STORE_B, customerInB, managerToken).getBody()).hasSize(1);
  }

  @Test
  @DisplayName("他店舗の顧客 ID は不可視のため紐づけ端点でも 404 になること")
  void foreignStoreCustomerIsInvisible() {
    String managerToken = loginAs("tanaka.hanako@kizuna.test");
    String customerInB = createCustomerAs(STORE_B, "不可視顧客-" + nonce, managerToken);
    String memberCode = registerMember("link-invisible");

    // tanaka は両店舗に授権されるためヘッダは通り、越境は storeFilter による 404 として現れる
    assertThat(link(STORE_A, customerInB, memberCode, managerToken).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(history(STORE_A, customerInB, managerToken).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("会員トークンでは紐づけ端点に到達できないこと（403）")
  void memberTokenCannotReachStoreEndpoint() {
    String customerId = createCustomer(STORE_A, "会員拒否顧客-" + nonce);
    String email = uniqueEmail("link-member-token");
    registerMemberAs(email);
    String memberToken = loginAs(email, PASSWORD);

    assertThat(link(STORE_A, customerId, "123456789012", memberToken).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  private static String memberLinkPath(String customerId) {
    return "/store/customers/" + customerId + "/member-link";
  }

  private ResponseEntity<JsonNode> link(
      long storeId, String customerId, String memberCode, String bearerToken) {
    return rest.exchange(
        memberLinkPath(customerId),
        HttpMethod.POST,
        new HttpEntity<>(
            "{\"member_code\": \"" + memberCode + "\"}", headersFor(storeId, bearerToken)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> history(long storeId, String customerId, String bearerToken) {
    return rest.exchange(
        memberLinkPath(customerId) + "/history",
        HttpMethod.GET,
        new HttpEntity<>(headersFor(storeId, bearerToken)),
        JsonNode.class);
  }

  private String createCustomer(long storeId, String name) {
    return createCustomerAs(storeId, name, token);
  }

  private String createCustomerAs(long storeId, String name, String bearerToken) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>("{\"name\": \"" + name + "\"}", headersFor(storeId, bearerToken)),
            JsonNode.class);
    assertThat(created.getStatusCode().is2xxSuccessful())
        .as("前提: store %d での顧客作成が成功すること", storeId)
        .isTrue();
    String id = created.getBody().path("id").asString();
    assertThat(id).isNotBlank();
    return id;
  }

  /** 新しい会員を登録してその会員コードを返す。 */
  private String registerMember(String prefix) {
    return registerMemberAs(uniqueEmail(prefix));
  }

  private String registerMemberAs(String email) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \""
                    + email
                    + "\", \"password\": \""
                    + PASSWORD
                    + "\", \"display_name\": \"紐づけ会員\"}",
                headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);
    String memberCode = res.getBody().path("member_code").asString();
    assertThat(memberCode).matches("\\d{12}");
    return memberCode;
  }

  private String uniqueEmail(String prefix) {
    return prefix + "-it-" + nonce + "-" + System.nanoTime() + "@kizuna.test";
  }

  private String loginAs(String email) {
    return loginAs(email, "pass");
  }

  private String loginAs(String email, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}", headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: %s でのログインが成功すること", email).isEqualTo(HttpStatus.OK);
    return res.getBody().path("token").asString();
  }

  private static HttpHeaders headersFor(long storeId, String bearerToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(storeId));
    headers.setBearerAuth(bearerToken);
    return headers;
  }
}
