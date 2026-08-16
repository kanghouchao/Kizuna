package com.kizuna.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.member.domain.Member;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.List;
import java.util.Set;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

/**
 * 店舗削除の可否を本物の PostgreSQL で検証する統合テスト。
 *
 * <p>消せるのは、まだ開店しておらず確定した記録も持たない店舗だけ。消せる場合は店舗授権行（platform_user_stores）まで ON DELETE CASCADE
 * で従い、ユーザー本体は残る（授権集合が空になるだけの fail-closed）。
 *
 * <p>消せない場合の断りは 2 種類で、稼働中そのものと、確定した記録の存在（完了済み受注またはポイント仕訳の帰属）である。 記録による拒否はアプリケーション層でしか成立しない — 台帳の
 * originating_store_id は SET NULL のままで、DB は店舗の削除を 止めず帰属だけを外すため、止める場所はここしかない。
 *
 * <p>様式は {@link SeedSequenceAlignmentIT}（HQ 管理者の平台ログイン + JdbcTemplate による実 DB 断言）に倣う。使い捨て tmpfs DB
 * のためシード store 1 は決して削除せず、第二店舗を直挿して検証する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class StoreDeletionCascadeIT {

  @Autowired private TestRestTemplate rest;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StoreRepository storeRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PointEntryRepository pointEntryRepository;
  @Autowired private MemberRepository memberRepository;

  private String platformLogin() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>("{\"email\": \"admin@kizuna.test\", \"password\": \"pass\"}", headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: HQ 管理者での平台ログインが成功すること").isEqualTo(HttpStatus.OK);
    String token = res.getBody().path("token").asString();
    assertThat(token).isNotBlank();
    return token;
  }

  /** 新規登録した店舗は準備中から始まる（＝削除できる状態）。 */
  private Store freshStore(String name, String domainPrefix) {
    return storeRepository.save(
        new Store(name, domainPrefix + "-" + UUID.randomUUID() + ".kizuna.test", null));
  }

  private ResponseEntity<JsonNode> deleteStore(long storeId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(platformLogin());
    return rest.exchange(
        "/platform/stores/" + storeId,
        HttpMethod.DELETE,
        new HttpEntity<>(headers),
        JsonNode.class);
  }

  @Test
  @DisplayName("準備中で記録の無い店舗は削除でき、SPECIFIC_STORES ユーザーの店舗授権行が CASCADE 消去されユーザー本体は残ること")
  void deletingStoreCascadesStoreGrantButKeepsPlatformUser() {
    Store store = freshStore("削除カスケード検証店舗", "store-delete-it");
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
                .roleIds(Set.of(roleRepository.findByName("店長").orElseThrow().getId()))
                .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of(storeId))
                .build());
    long userId = user.getId();

    // 前提: 削除前は授権行が 1 件存在する（空振りで緑にならないことを固定）。
    assertThat(countStoreGrants(storeId)).as("削除前は店舗授権行が存在すること").isEqualTo(1L);

    ResponseEntity<JsonNode> res = deleteStore(storeId);

    // FK が ON DELETE CASCADE でなければ店舗削除は FK 違反で失敗する。
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    // 店舗授権行は店舗と共に消える。
    assertThat(countStoreGrants(storeId)).as("削除後は店舗授権行が CASCADE 消去されること").isZero();
    // ユーザー本体は残る（授権集合が空になるだけ = authorizes() 全 false の fail-closed）。
    assertThat(countPlatformUser(userId)).as("プラットフォームユーザー本体は残存すること").isEqualTo(1L);
  }

  @Test
  @DisplayName("稼働中の店舗は記録が無くても削除できず、店舗が残ること")
  void activeStoreCannotBeDeleted() {
    Store store = freshStore("稼働中検証店舗", "active-store-delete-it");
    long storeId = store.getId();
    // 遷移そのものは StoreActivationIT が固定する。ここでは稼働中という状態だけを作る。
    jdbcTemplate.update("UPDATE t_stores SET status = 'ACTIVE' WHERE id = ?", storeId);

    ResponseEntity<JsonNode> res = deleteStore(storeId);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody().path("error").asString()).isEqualTo("稼働中の店舗は削除できません");
    assertThat(countStore(storeId)).as("拒否された店舗は残存すること").isEqualTo(1L);
  }

  @Test
  @DisplayName("完了済みの受注を持つ店舗は準備中でも削除できないこと")
  void storeWithCompletedOrderCannotBeDeleted() {
    Store store = freshStore("完了受注検証店舗", "order-store-delete-it");
    long storeId = store.getId();
    insertCompletedOrder(storeId);

    ResponseEntity<JsonNode> res = deleteStore(storeId);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody().path("error").asString()).isEqualTo("完了済みの受注またはポイント仕訳が存在する店舗は削除できません");
    assertThat(countStore(storeId)).as("拒否された店舗は残存すること").isEqualTo(1L);
  }

  @Test
  @DisplayName("ポイント仕訳の帰属を持つ店舗は削除できず、仕訳の発生店舗も外れないこと")
  void storeWithPointEntryCannotBeDeleted() {
    Store store = freshStore("台帳帰属検証店舗", "point-store-delete-it");
    long storeId = store.getId();

    // 台帳は会員が持つ。発生店舗は帰属情報だが、その帰属が読めなくなること自体を削除の拒否理由とする。
    long memberId = registerMember();
    long entryId =
        pointEntryRepository
            .save(
                PointEntry.manualAdjust(
                    memberId,
                    storeId,
                    500,
                    "店舗削除検証の付与",
                    null,
                    List.of(),
                    null,
                    "store-cascade-" + memberId))
            .getId();

    // 前提: 削除前は発生店舗が入っている（空振りで緑にならないことを固定）。
    assertThat(originatingStoreIdOf(entryId)).as("削除前は発生店舗が入っていること").isEqualTo(storeId);

    ResponseEntity<JsonNode> res = deleteStore(storeId);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody().path("error").asString()).isEqualTo("完了済みの受注またはポイント仕訳が存在する店舗は削除できません");
    assertThat(countPointEntry(entryId)).as("ポイント仕訳は残存すること").isEqualTo(1L);
    // 削除に至っていないので SET NULL の外部キーは働かない。帰属が外れていれば削除が通ってしまった証拠になる。
    assertThat(originatingStoreIdOf(entryId)).as("発生店舗は元のまま外れないこと").isEqualTo(storeId);
    assertThat(countStore(storeId)).as("拒否された店舗は残存すること").isEqualTo(1L);
  }

  @Test
  @DisplayName("顧客統合を行った準備中の店舗も削除でき、墓標と統合履歴が CASCADE 消去されること")
  void storeWithCustomerMergeCanStillBeDeleted() {
    Store store = freshStore("統合済み検証店舗", "merge-store-delete-it");
    long storeId = store.getId();
    // 統合が残す形（存続行・統合先を指す墓標・両行を指す統合履歴）を直挿する。ここで見たいのは
    // サービス層ではなく外部キーの働き — 顧客への参照は RESTRICT なので、店舗削除の CASCADE が
    // 統合履歴より先に顧客へ届くと、削除が違反で止まりうるのではないかという疑いを潰す。
    String surviving = "merge-cascade-surviving-" + UUID.randomUUID();
    String merged = "merge-cascade-merged-" + UUID.randomUUID();
    insertCustomer(storeId, surviving, null);
    insertCustomer(storeId, merged, surviving);
    insertCustomerMerge(storeId, surviving, merged);

    // 前提: 削除前は墓標と統合履歴が実在する（空振りで緑にならないことを固定）。
    assertThat(countCustomers(storeId)).as("削除前は顧客が 2 件あること").isEqualTo(2L);
    assertThat(countCustomerMerges(storeId)).as("削除前は統合履歴が 1 件あること").isEqualTo(1L);

    ResponseEntity<JsonNode> res = deleteStore(storeId);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(countCustomers(storeId)).as("顧客は店舗と共に消えること").isZero();
    assertThat(countCustomerMerges(storeId)).as("統合履歴も店舗と共に消えること").isZero();
    assertThat(countStore(storeId)).isZero();
  }

  @Test
  @DisplayName("未完了の受注を抱えた準備中の店舗も削除でき、顧客・キャスト・受注が CASCADE 消去されること")
  void storeWithPendingOrdersCanStillBeDeleted() {
    // 受注は顧客とキャストを参照し、その 2 つは店舗を参照する。どちらの参照も削除を止める外部キーなので、
    // 店舗削除の CASCADE が受注より先に顧客・キャストへ届けば違反で止まりうる。誤って登録した準備中の
    // 店舗を撤回できることは店舗削除の存在理由なので、その疑いをここで潰す。
    Store store = freshStore("受注あり検証店舗", "order-store-delete-it");
    long storeId = store.getId();
    String customerId = "order-cascade-customer-" + UUID.randomUUID();
    String castId = "order-cascade-cast-" + UUID.randomUUID();
    insertCustomer(storeId, customerId, null);
    insertCast(storeId, castId);
    insertOrder(storeId, customerId, castId);

    assertThat(countOrders(storeId)).as("削除前は受注が 1 件あること").isEqualTo(1L);

    ResponseEntity<JsonNode> res = deleteStore(storeId);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(countOrders(storeId)).as("受注は店舗と共に消えること").isZero();
    assertThat(countCustomers(storeId)).as("顧客は店舗と共に消えること").isZero();
    assertThat(countStore(storeId)).isZero();
  }

  private void insertCast(long storeId, String castId) {
    jdbcTemplate.update(
        "INSERT INTO t_casts (id, store_id, name, created_at, updated_at, version)"
            + " VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
        castId,
        storeId,
        "受注カスケード検証キャスト");
  }

  private void insertOrder(long storeId, String customerId, String castId) {
    jdbcTemplate.update(
        "INSERT INTO t_orders (id, store_id, customer_id, cast_id, business_date, status,"
            + " created_at, updated_at, version) VALUES (?, ?, ?, ?, CURRENT_DATE, 'CONFIRMED',"
            + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
        "order-cascade-it-" + UUID.randomUUID(),
        storeId,
        customerId,
        castId);
  }

  private long countOrders(long storeId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM t_orders WHERE store_id = ?", Long.class, storeId);
  }

  private void insertCustomer(long storeId, String customerId, String mergedIntoId) {
    jdbcTemplate.update(
        "INSERT INTO t_customers (id, store_id, name, merged_into_id, created_at, updated_at,"
            + " version) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
        customerId,
        storeId,
        "統合カスケード検証顧客",
        mergedIntoId);
  }

  private void insertCustomerMerge(long storeId, String survivingId, String mergedId) {
    jdbcTemplate.update(
        "INSERT INTO t_customer_merges (id, store_id, surviving_customer_id, merged_customer_id,"
            + " merged_at, moved_order_count, moved_link_count, created_at, updated_at, version)"
            + " VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,"
            + " 0)",
        "merge-cascade-it-" + UUID.randomUUID(),
        storeId,
        survivingId,
        mergedId);
  }

  private long countCustomers(long storeId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM t_customers WHERE store_id = ?", Long.class, storeId);
  }

  private long countCustomerMerges(long storeId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM t_customer_merges WHERE store_id = ?", Long.class, storeId);
  }

  /** 台帳の持ち主となる会員を公開端点から用意し、その id を返す。 */
  private long registerMember() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \"point-cascade-it-"
                    + UUID.randomUUID()
                    + "@kizuna.test\", \"password\": \"password1234\","
                    + " \"display_name\": \"台帳存続検証会員\"}",
                headers),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);
    return memberRepository
        .findByMemberCode(res.getBody().path("member_code").asString())
        .map(Member::getId)
        .orElseThrow();
  }

  /** 会員も顧客も伴わない最小の完了済み受注を直挿する（判定に効くのは店舗と状態だけ）。 */
  private void insertCompletedOrder(long storeId) {
    jdbcTemplate.update(
        "INSERT INTO t_orders (id, store_id, business_date, status, created_at, updated_at, version)"
            + " VALUES (?, ?, CURRENT_DATE, 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
        "order-delete-it-" + UUID.randomUUID(),
        storeId);
  }

  private Long originatingStoreIdOf(long entryId) {
    return jdbcTemplate.queryForObject(
        "SELECT originating_store_id FROM t_point_entries WHERE id = ?", Long.class, entryId);
  }

  private long countPointEntry(long entryId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM t_point_entries WHERE id = ?", Long.class, entryId);
  }

  private long countStore(long storeId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM t_stores WHERE id = ?", Long.class, storeId);
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
