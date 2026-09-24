package com.kizuna.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.customer.domain.CustomerMemberLink;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.LinkReason;
import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.user.domain.Permission;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

  @Autowired private CustomerMemberLinkRepository customerMemberLinkRepository;
  @Autowired private MemberRepository memberRepository;

  @Autowired private PlatformUserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private PermissionRepository permissions;
  @Autowired private PasswordEncoder passwords;
  @Autowired private JdbcTemplate jdbc;

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("会員コードで紐づけると詳細と現況に会員コードが、一覧に紐づけ状態が投影されること")
  void linkIsProjectedOntoListAndDetail() {
    // 一覧はクエリ文字列で絞り込むため、顧客名は ASCII の一意な字面にする
    String name = "cml-projected-" + nonce;
    String customerId = createCustomer(STORE_A, name);
    String memberCode = registerMember("link-projected");

    ResponseEntity<JsonNode> linked = link(STORE_A, customerId, memberCode, token);

    assertThat(linked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
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
    // 一覧の行は会員コードを持たない（要るのは有無だけ）。コードは現況の読み口が答える
    assertThat(row.hasNonNull("linked_member_code")).isFalse();

    ResponseEntity<JsonNode> current = memberLink(STORE_A, customerId, token);
    assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(current.getBody().path("member_code").asString()).isEqualTo(memberCode);
    assertThat(current.getBody().path("linked").asBoolean()).isTrue();
    assertThat(current.getBody().path("linked_at").asString()).isNotBlank();
  }

  @Test
  @DisplayName("会員コードで成立した関連には成立根拠 MEMBER_CODE が記録されること")
  void linkRecordsMemberCodeReason() {
    String customerId = createCustomer(STORE_A, "根拠顧客-" + nonce);
    String memberCode = registerMember("link-reason");

    assertThat(link(STORE_A, customerId, memberCode, token).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);

    JsonNode row = history(STORE_A, customerId, token).getBody().path("content").get(0);
    assertThat(row.path("reason").asString()).isEqualTo("MEMBER_CODE");
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
    // 現況の読み口は「紐づいていない」を本体で表さず 404 で返す
    assertThat(memberLink(STORE_A, customerId, token).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("解除すると一覧の投影が外れ、履歴に解除の実行者と日時が残ること")
  void unlinkKeepsHistoryWithActor() {
    String customerId = createCustomer(STORE_A, "解除顧客-" + nonce);
    String memberCode = registerMember("link-unlink");
    link(STORE_A, customerId, memberCode, token);

    ResponseEntity<JsonNode> released = releaseMemberLink(customerId, storeHeaders(STORE_A));
    assertThat(released.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<JsonNode> detail =
        rest.exchange(
            "/store/customers/" + customerId,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(detail.getBody().path("member_linked").asBoolean()).isFalse();

    JsonNode history = history(STORE_A, customerId, token).getBody().path("content");
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

    String expectedId = memberLink(STORE_A, customerId, token).getBody().path("id").asString();
    ResponseEntity<JsonNode> switched =
        rest.exchange(
            memberLinkPath(customerId),
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "member_code",
                    secondCode,
                    "expected_link_id",
                    expectedId,
                    "operation_reason",
                    "本人確認"),
                storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(switched.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(switched.getBody().path("member_code").asString()).isEqualTo(secondCode);

    JsonNode history = history(STORE_A, customerId, token).getBody().path("content");
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
  @DisplayName("存在しない対象は 404、関連の無い解除は 409 になること")
  void unknownTargetsAreNotFound() {
    String customerId = createCustomer(STORE_A, "不在顧客-" + nonce);

    assertThat(link(STORE_A, customerId, "000000000000", token).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(link(STORE_A, "no-such-customer", registerMember("link-404"), token).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(releaseMemberLink(customerId, storeHeaders(STORE_A)).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
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
    assertThat(memberLink(STORE_B, customerId, token).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("同一会員を店舗ごとに別々の顧客へ紐づけられること（一意性は店舗内に閉じる）")
  void sameMemberCanBeLinkedInEachStore() {
    String managerToken = loginAs("tanaka.hanako@kizuna.test");
    String customerInA = createCustomerAs(STORE_A, "跨店顧客A-" + nonce, managerToken);
    String customerInB = createCustomerAs(STORE_B, "跨店顧客B-" + nonce, managerToken);
    String memberCode = registerMember("link-both-stores");

    assertThat(link(STORE_A, customerInA, memberCode, managerToken).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(link(STORE_B, customerInB, memberCode, managerToken).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);

    // 店舗Aの履歴には店舗Bの区間が混ざらない
    assertThat(history(STORE_A, customerInA, managerToken).getBody().path("content")).hasSize(1);
    assertThat(history(STORE_B, customerInB, managerToken).getBody().path("content")).hasSize(1);
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
    // 越境の 404 は「紐づいていない」の 404 と同形になる。現況の読み口でも顧客の存在自体が漏れない
    ResponseEntity<JsonNode> foreign = memberLink(STORE_A, customerInB, managerToken);
    assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(foreign.getBody().hasNonNull("member_code")).as("拒否応答に他店舗の会員コードが現れないこと").isFalse();
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

  @Test
  @DisplayName("変更理由と解除理由を区間ごとに保持し、古い区間への操作を拒否すること")
  void reasonsAndExpectedIntervalArePreserved() {
    String customerId = createCustomer(STORE_A, "理由区間-" + nonce);
    String firstCode = registerMember("reason-first");
    String secondCode = registerMember("reason-second");
    ResponseEntity<JsonNode> first = link(STORE_A, customerId, firstCode, token);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String firstId = first.getBody().path("id").asString();
    assertThat(firstId).isNotBlank();

    ResponseEntity<JsonNode> missingReason =
        rest.exchange(
            memberLinkPath(customerId),
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("member_code", secondCode, "expected_link_id", firstId),
                storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(missingReason.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(history(STORE_A, customerId, token).getBody().path("content")).hasSize(1);

    ResponseEntity<JsonNode> changed =
        rest.exchange(
            memberLinkPath(customerId),
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "member_code",
                    secondCode,
                    "expected_link_id",
                    firstId,
                    "operation_reason",
                    "  本人確認による変更  "),
                storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String secondId = changed.getBody().path("id").asString();
    JsonNode intervals = history(STORE_A, customerId, token).getBody().path("content");
    assertThat(intervals).hasSize(2);
    assertThat(intervals.get(0).path("operation_reason").asString()).isEqualTo("本人確認による変更");
    assertThat(intervals.get(0).path("reason").asString()).isEqualTo("MEMBER_CODE");
    assertThat(intervals.get(1).path("release_reason").asString()).isEqualTo("本人確認による変更");
    assertThat(intervals.get(1).path("id").asString()).isEqualTo(firstId);
    assertThat(intervals.get(0).path("linked_at")).isEqualTo(intervals.get(1).path("released_at"));
    assertThat(intervals.get(0).path("linked_by").asLong()).isPositive();

    assertThat(release(customerId, firstId, "古い画面で解除").getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(release(customerId, secondId, " ").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(memberLink(STORE_A, customerId, token).getBody().path("id").asString())
        .isEqualTo(secondId);
    assertThat(release(customerId, secondId, "本人から解除依頼").getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(release(customerId, secondId, "再送").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(link(STORE_A, customerId, firstCode, token).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    JsonNode retained = history(STORE_A, customerId, token).getBody().path("content");
    assertThat(retained).hasSize(3);
    assertThat(retained.get(1).path("release_reason").asString()).isEqualTo("本人から解除依頼");
    assertThat(retained.get(1).path("released_by").asLong()).isPositive();
  }

  @Test
  @DisplayName("変更先が競合した場合も理由なし・長すぎる理由の場合も現在の区間を保持すること")
  void failedChangesRetainTheActiveInterval() {
    String customer = createCustomer(STORE_A, "競合保持-" + nonce);
    String other = createCustomer(STORE_A, "競合先-" + nonce);
    String code = registerMember("retain-current");
    String taken = registerMember("retain-taken");
    String free = registerMember("retain-free");
    String id = link(STORE_A, customer, code, token).getBody().path("id").asString();
    link(STORE_A, other, taken, token);
    for (String invalid : List.of(" ", "あ".repeat(501))) {
      assertThat(
              rest.exchange(
                      memberLinkPath(customer),
                      HttpMethod.POST,
                      new HttpEntity<>(
                          Map.of(
                              "member_code",
                              free,
                              "expected_link_id",
                              id,
                              "operation_reason",
                              invalid),
                          storeHeaders(STORE_A)),
                      JsonNode.class)
                  .getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    assertThat(
            rest.exchange(
                    memberLinkPath(customer),
                    HttpMethod.POST,
                    new HttpEntity<>(
                        Map.of(
                            "member_code",
                            taken,
                            "expected_link_id",
                            id,
                            "operation_reason",
                            "本人確認"),
                        storeHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(link(STORE_A, customer, free, token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(memberLink(STORE_A, customer, token).getBody().path("id").asString()).isEqualTo(id);
    JsonNode rows = history(STORE_A, customer, token).getBody().path("content");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).has("release_reason")).isFalse();
  }

  @Test
  @DisplayName("変更・解除・履歴は店舗隔離と CUSTOMER_MANAGE を要求すること")
  void writesAndHistoryRespectScopeAndPermission() {
    String manager = loginAs("tanaka.hanako@kizuna.test");
    String customer = createCustomerAs(STORE_B, "理由隔離-" + nonce, manager);
    String code = registerMember("scope-reason");
    String id = link(STORE_B, customer, code, manager).getBody().path("id").asString();
    var body = Map.of("expected_link_id", id, "operation_reason", "本人依頼");
    assertThat(
            rest.exchange(
                    memberLinkPath(customer) + "/releases",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headersFor(STORE_A, manager)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    String email = uniqueEmail("reason-permission");
    registerMemberAs(email);
    String memberToken = loginAs(email, PASSWORD);
    assertThat(
            rest.exchange(
                    memberLinkPath(customer) + "/releases",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headersFor(STORE_B, memberToken)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(history(STORE_B, customer, memberToken).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(memberLink(STORE_B, customer, manager).getBody().path("id").asString())
        .isEqualTo(id);
  }

  @Test
  @DisplayName("店舗アクセスを持つが CUSTOMER_MANAGE のない担当者は関連の全操作を拒否されること")
  void storeStaffWithoutCustomerPermissionCannotAccessLinks() {
    String customer = createCustomer(STORE_A, "関連権限-" + nonce);
    String code = registerMember("staff-scope");
    String id = link(STORE_A, customer, code, token).getBody().path("id").asString();
    var role =
        roles.save(
            Role.builder()
                .name("関連権限検証-" + nonce)
                .permissionIds(
                    Set.of(
                        permissions.findByCodeIn(Set.of("ORDER_MANAGE")).stream()
                            .map(Permission::getId)
                            .findFirst()
                            .orElseThrow()))
                .build());
    String email = uniqueEmail("unprivileged-link");
    String password = UUID.randomUUID().toString();
    users.save(
        PlatformUser.builder()
            .email(email)
            .password(passwords.encode(password))
            .displayName("関連権限なし")
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(Set.of(role.getId()))
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(STORE_A))
            .build());
    var headers = headersFor(STORE_A, loginAs(email, password));
    for (String suffix : List.of("", "/history")) {
      assertThat(
              rest.exchange(
                      memberLinkPath(customer) + suffix,
                      HttpMethod.GET,
                      new HttpEntity<>(headers),
                      JsonNode.class)
                  .getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
    assertThat(
            rest.exchange(
                    memberLinkPath(customer),
                    HttpMethod.POST,
                    new HttpEntity<>(
                        Map.of(
                            "member_code",
                            code,
                            "expected_link_id",
                            id,
                            "operation_reason",
                            "本人確認"),
                        headers),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            rest.exchange(
                    memberLinkPath(customer) + "/releases",
                    HttpMethod.POST,
                    new HttpEntity<>(
                        Map.of("expected_link_id", id, "operation_reason", "本人依頼"), headers),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(memberLink(STORE_A, customer, token).getBody().path("id").asString()).isEqualTo(id);
  }

  @Test
  @DisplayName("同じ会員への同時変更は一方だけ成立し、失敗側の旧関連と履歴をロールバックすること")
  void simultaneousChangesRollbackTheLosingInterval() throws Exception {
    String a = createCustomer(STORE_A, "並行関連A-" + nonce);
    String b = createCustomer(STORE_A, "並行関連B-" + nonce);
    String target = registerMember("concurrent-target");
    String aId =
        link(STORE_A, a, registerMember("concurrent-a"), token).getBody().path("id").asString();
    String bId =
        link(STORE_A, b, registerMember("concurrent-b"), token).getBody().path("id").asString();
    var pool = Executors.newFixedThreadPool(2);
    try (var connection = jdbc.getDataSource().getConnection()) {
      connection.setAutoCommit(false);
      int blocker;
      try (var statement = connection.createStatement();
          var result = statement.executeQuery("select pg_backend_pid()")) {
        result.next();
        blocker = result.getInt(1);
      }
      try (var lock =
          connection.prepareStatement(
              "select id from t_customer_member_links where id in (?, ?) for update")) {
        lock.setString(1, aId);
        lock.setString(2, bId);
        lock.executeQuery().close();
      }
      var first = pool.submit(() -> change(a, target, aId));
      var second = pool.submit(() -> change(b, target, bId));
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
      int waiting = 0;
      while (waiting < 2 && System.nanoTime() < deadline) {
        waiting =
            jdbc.queryForObject(
                "select count(*) from pg_stat_activity where ? = any(pg_blocking_pids(pid))",
                Integer.class,
                blocker);
        if (waiting < 2) Thread.sleep(20);
      }
      assertThat(waiting).as("双方が事前確認を通り旧区間の更新で待つこと").isEqualTo(2);
      connection.commit();
      var firstResult = first.get(30, TimeUnit.SECONDS);
      var secondResult = second.get(30, TimeUnit.SECONDS);
      assertThat(List.of(firstResult.getStatusCode(), secondResult.getStatusCode()))
          .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
      String loser = firstResult.getStatusCode() == HttpStatus.CONFLICT ? a : b;
      String original = loser.equals(a) ? aId : bId;
      assertThat(memberLink(STORE_A, loser, token).getBody().path("id").asString())
          .isEqualTo(original);
      JsonNode rows = history(STORE_A, loser, token).getBody().path("content");
      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).path("status").asString()).isEqualTo("ACTIVE");
      assertThat(rows.get(0).has("release_reason")).isFalse();
    } finally {
      pool.shutdownNow();
    }
  }

  private ResponseEntity<JsonNode> change(String customer, String code, String expectedId) {
    return rest.exchange(
        memberLinkPath(customer),
        HttpMethod.POST,
        new HttpEntity<>(
            Map.of("member_code", code, "expected_link_id", expectedId, "operation_reason", "本人確認"),
            storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> release(String customerId, String expectedId, String reason) {
    return rest.exchange(
        memberLinkPath(customerId) + "/releases",
        HttpMethod.POST,
        new HttpEntity<>(
            Map.of("expected_link_id", expectedId, "operation_reason", reason),
            storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private static String memberLinkPath(String customerId) {
    return "/store/customers/" + customerId + "/member-link";
  }

  @Test
  @DisplayName("紐づけ時刻が同一の履歴でも、カーソルで重複・欠落なく辿れること")
  void walksEveryHistoryRowThroughTheCursorEvenWhenLinkedAtTheSameInstant() {
    // 並びの鍵（linked_at）だけでは同値の 3 行の前後が決まらない。size=2 の境界がその群の
    // 内側に落ちるので、副キー id が無いと 2 頁目が手前へ戻って重複するか、行を飛ばす。
    String customerId = createCustomer(STORE_A, "cml-same-instant-" + nonce);
    long memberId = memberIdOf(registerMember("link-same-instant"));
    OffsetDateTime sameInstant = OffsetDateTime.parse("2026-08-10T12:00:00+09:00");

    List<String> seeded = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      CustomerMemberLink row =
          CustomerMemberLink.builder()
              .customerId(customerId)
              .memberId(memberId)
              .memberCode("00000000000" + i)
              .reason(LinkReason.MEMBER_CODE)
              .linkedBy(1L)
              .linkedAt(sameInstant)
              .build();
      // 3 行とも解除済みにする。ACTIVE は顧客・会員ごとに 1 件までの部分一意索引に当たる
      row.release(1L, "本人依頼", OffsetDateTime.now());
      row.setStoreId(STORE_A);
      seeded.add(customerMemberLinkRepository.saveAndFlush(row).getId());
    }

    List<String> walked = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    do {
      String url =
          memberLinkPath(customerId)
              + "/history?size=2"
              + (cursor == null ? "" : "&cursor=" + cursor);
      ResponseEntity<JsonNode> page =
          rest.exchange(
              url, HttpMethod.GET, new HttpEntity<>(headersFor(STORE_A, token)), JsonNode.class);
      assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
      page.getBody().path("content").forEach(row -> walked.add(row.path("id").asString()));
      JsonNode next = page.getBody().path("next_cursor");
      cursor = next.isString() ? next.asString() : null;
      pages++;
    } while (cursor != null && pages < 10);

    assertThat(cursor).as("続きを辿り切ること").isNull();
    assertThat(walked).as("同じ行を二度返さないこと").doesNotHaveDuplicates();
    assertThat(walked).as("同刻の 3 行がすべて現れること").containsAll(seeded);
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

  /** 現に有効な紐づけ（未紐づけなら 404）。 */
  private ResponseEntity<JsonNode> memberLink(long storeId, String customerId, String bearerToken) {
    return rest.exchange(
        memberLinkPath(customerId),
        HttpMethod.GET,
        new HttpEntity<>(headersFor(storeId, bearerToken)),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> history(long storeId, String customerId, String bearerToken) {
    return rest.exchange(
        memberLinkPath(customerId) + "/history",
        HttpMethod.GET,
        new HttpEntity<>(headersFor(storeId, bearerToken)),
        JsonNode.class);
  }

  private long memberIdOf(String memberCode) {
    return memberRepository.findByMemberCode(memberCode).map(Member::getId).orElseThrow();
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
