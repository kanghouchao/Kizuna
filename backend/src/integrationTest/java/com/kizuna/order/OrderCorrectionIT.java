package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.result.OrderCompletionResults;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;
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

/** 完了後訂正の権限・独立した前後快照・受注版とカーソルの順序を実 PostgreSQL で検証する。 台帳は訂正で変わらず、店舗と授権店舗集合の外へ履歴を返さない。 */
class OrderCorrectionIT extends CrossStoreTestSupport {

  /** demo シード（seed/05-demo.yaml）の山田次郎（STORE_STAFF・授権店舗 = 店舗1）。 */
  private static final long SEED_RECEPTIONIST_ID = 3L;

  private static final int COMPLETED_FEE = 12000;

  @Autowired private PointEntryRepository pointEntryRepository;
  @Autowired private OrderCompletionResults results;
  @Autowired private OrderRepository orders;
  @Autowired private StoreContext storeContext;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformUserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private PasswordEncoder passwords;

  private final long nonce = System.nanoTime();

  @Test
  @DisplayName("店長は完了した受注の三組を訂正でき、訂正のたびに前値の快照が残って鎖から履歴を復元できること")
  void managerCorrectsTheCompletedOrderAndLeavesAChainOfPriorValues() {
    String orderId = completedMemberOrder("鎖");
    long pointEntries = pointEntryRepository.count();
    var revisedCourse = courseFixture(STORE_A, "120 分コース", 120, 18000);

    ResponseEntity<JsonNode> first =
        correct(
            managerHeaders(STORE_A),
            orderId,
            """
            {"reason":"コースの取り違え","actual_arrival_time":"20:15:00","actual_end_time":"22:40:00",
             "course_revision_id":"%s",
             "fee_lines":[{"kind":"CREDIT_SURCHARGE","name":"指名","amount":2000}]}
            """
                .formatted(revisedCourse.revisionId()));

    assertThat(first.getStatusCode()).as("痕を生む操作なので 201").isEqualTo(HttpStatus.CREATED);
    assertThat(first.getBody().path("previous_total_fee").asInt()).isEqualTo(COMPLETED_FEE);
    assertThat(first.getBody().path("total_fee").asInt()).isEqualTo(20000);
    // 応答が名乗るのは会計金額の前後だけ。付与の差額は算出も提示もしない（手当てと結ぶ線が無いため）
    assertThat(first.getBody().path("granted_points").isMissingNode()).isTrue();
    assertThat(first.getBody().path("grant_difference").isMissingNode()).isTrue();

    JsonNode detail = orderJson(managerHeaders(STORE_A), orderId);
    assertThat(detail.path("status").asString()).as("訂正は状態を戻さないこと").isEqualTo("COMPLETED");
    assertThat(detail.path("actual_arrival_time").asString()).isEqualTo("20:15:00");
    assertThat(detail.path("actual_end_time").asString()).isEqualTo("22:40:00");
    assertThat(detail.path("course").path("name").asString()).isEqualTo("120 分コース");
    assertThat(detail.path("course").path("duration_minutes").asInt()).isEqualTo(120);
    assertThat(detail.path("extension_minutes").asInt()).isZero();
    assertThat(detail.path("total_fee").asInt()).isEqualTo(20000);
    assertThat(detail.path("auto_grant_points").asInt()).as("門はポイントを動かさないこと").isEqualTo(120);
    // 基本コース料金の行名称はコース名の写しから採る（金額だけ直る半修状態を作らない）
    assertThat(detail.path("fee_lines").get(0).path("name").asString()).isEqualTo("120 分コース");

    // 全量送信なので、行が在るのにコース名を落とした要求は撥ねられる（金額だけ直る半修状態を作らない）
    assertThat(
            correct(
                    managerHeaders(STORE_A),
                    orderId,
                    """
                    {"reason":"コース名の消去","fee_lines":[{"kind":"BASE_COURSE","amount":18000}]}
                    """)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    // 二度目の訂正。ある訂正の「後値」＝次の訂正の「前値」で、鎖が繋がる
    ResponseEntity<JsonNode> second =
        correct(
            managerHeaders(STORE_A),
            orderId,
            """
            {"reason":"オプションの取り消し","fee_lines":[]}
            """);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(second.getBody().path("previous_total_fee").asInt()).isEqualTo(20000);

    assertThat(pointEntryRepository.count())
        .as("2 度の訂正を通しても台帳へ 1 行も書かないこと")
        .isEqualTo(pointEntries);

    var chain = history(storeHeaders(STORE_A), orderId, "").getBody().path("content");
    assertThat(chain).hasSize(2);
    var latest = chain.get(0);
    var firstRecord = chain.get(1);
    assertThat(firstRecord.path("correction_id").asString())
        .isEqualTo(first.getBody().path("correction_id").asString());
    assertThat(firstRecord.path("before").path("total_fee").asInt()).isEqualTo(COMPLETED_FEE);
    assertThat(firstRecord.path("after").path("total_fee").asInt()).isEqualTo(20000);
    assertThat(latest.path("before")).isEqualTo(firstRecord.path("after"));
    assertThat(latest.path("after").path("total_fee").asInt()).isEqualTo(18000);
    assertThat(firstRecord.path("corrected_by").asLong()).isPositive();
    assertThat(firstRecord.path("business_date")).isEqualTo(detail.path("business_date"));
    assertThat(firstRecord.path("completed_at")).isEqualTo(detail.path("completed_at"));
    assertThat(latest.path("after_version").asLong())
        .isGreaterThan(firstRecord.path("after_version").asLong());

    var page = history(storeHeaders(STORE_A), orderId, "?size=1").getBody();
    assertThat(page.path("content")).hasSize(1);
    var next =
        history(
                storeHeaders(STORE_A),
                orderId,
                "?size=1&cursor=" + page.path("next_cursor").asString())
            .getBody();
    assertThat(next.path("content").get(0)).isEqualTo(firstRecord);
    assertThat(next.has("next_cursor")).isFalse();
    assertThat(history(storeHeaders(STORE_A), orderId, "?cursor=invalid").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    // 二度目の要求は実績時刻と延長分数を載せていない。全量送信なので「変更しない」ではなく「値なし」が当たる
    JsonNode afterSecond = orderJson(managerHeaders(STORE_A), orderId);
    assertThat(afterSecond.path("actual_arrival_time").isMissingNode()).isTrue();
    assertThat(afterSecond.path("actual_end_time").isMissingNode()).isTrue();
    assertThat(afterSecond.path("extension_minutes").asInt()).isZero();
    assertThat(afterSecond.path("course").path("name").asString()).isEqualTo("120 分コース");
  }

  @Test
  @DisplayName("受注管理だけの店員は完了後訂正に届かず、受注も痕も動かないこと")
  void staffWithOrderManageAloneCannotReachTheGate() {
    String orderId = completedOrder("権限");

    ResponseEntity<JsonNode> denied =
        correct(
            storeHeaders(STORE_A),
            orderId,
            """
            {"reason":"金額の誤記","fee_lines":[]}
            """);

    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(orderJson(managerHeaders(STORE_A), orderId).path("total_fee").asInt())
        .isEqualTo(COMPLETED_FEE);
    assertThat(history(storeHeaders(STORE_A), orderId, "").getBody().path("content")).isEmpty();
  }

  @Test
  @DisplayName("ポイント利用の行は門内でも編集できず、訂正を跨いで残ること")
  void pointRedemptionLinesSurviveTheGate() {
    String orderId = completedOrderUsingPoints("ポイント", 100);
    assertThat(history(storeHeaders(STORE_A), orderId, "").getBody().path("content")).isEmpty();
    assertPlatformTotalFee(orderId, COMPLETED_FEE - 100);
    long pointEntries = pointEntryRepository.count();

    // ポイント利用の誤りはポイント機構経由で直す。門の要求に混ぜることはできない
    ResponseEntity<JsonNode> rejected =
        correct(
            managerHeaders(STORE_A),
            orderId,
            """
            {"reason":"ポイントの取り違え",
             "fee_lines":[{"kind":"POINT_REDEMPTION","name":"ポイント利用","amount":300}]}
            """);
    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    ResponseEntity<JsonNode> accepted =
        correct(
            managerHeaders(STORE_A),
            orderId,
            """
            {"reason":"金額の誤記","fee_lines":[{"kind":"CREDIT_SURCHARGE","name":"指名料","amount":8000}]}
            """);
    assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    JsonNode lines = orderJson(managerHeaders(STORE_A), orderId).path("fee_lines");
    assertThat(lines).hasSize(3);
    assertThat(lines.get(1).path("kind").asString()).isEqualTo("POINT_REDEMPTION");
    assertThat(lines.get(1).path("amount").asInt()).as("減項は正値で返ること").isEqualTo(100);
    // 合計はポイント控除後の請求額なので、残った利用の行のぶん下がったまま
    assertThat(accepted.getBody().path("total_fee").asInt()).isEqualTo(8000);
    assertPlatformTotalFee(orderId, 8000);

    assertThat(pointEntryRepository.count()).as("門は台帳へ一切書かないこと").isEqualTo(pointEntries);
  }

  @Test
  @DisplayName("画面が見ていた版と食い違う訂正は 409 で差し戻され、本体も痕も動かないこと")
  void staleCorrectionsAreRefused() {
    // 全量置換なので、開いたまま別の操作者が訂正を済ませていると、送らなかった項目まで開いた時点の
    // 値で押し戻す。楽観ロックは要求ごとに現物を読み直すため、版の照合が無いと検出できない
    String orderId = completedOrder("陳腐化");
    long opened = currentVersion(managerHeaders(STORE_A), orderId);

    assertThat(
            correct(
                    managerHeaders(STORE_A),
                    orderId,
                    """
                    {"reason":"先に済んだ訂正",
                     "fee_lines":[{"kind":"CREDIT_SURCHARGE","name":"指名料","amount":9000}]}
                    """)
                .getStatusCode())
        .as("前提: 先の訂正が成立すること")
        .isEqualTo(HttpStatus.CREATED);

    ResponseEntity<JsonNode> stale =
        correctAt(
            managerHeaders(STORE_A),
            orderId,
            opened,
            """
            {"reason":"開いたままの画面からの訂正","fee_lines":[]}
            """);

    assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(orderJson(managerHeaders(STORE_A), orderId).path("total_fee").asInt())
        .as("撥ねた訂正は先の訂正を巻き戻さないこと")
        .isEqualTo(9100);
    assertThat(history(storeHeaders(STORE_A), orderId, "").getBody().path("content"))
        .as("撥ねた訂正は痕を残さないこと")
        .hasSize(1);
  }

  @Test
  @DisplayName("取消済みの受注と、状態を戻そうとする要求が撥ねられること")
  void cancelledOrdersAndStateRollbackAreRefused() {
    // 誤取消の救済は同内容で受注を起こし直すこと。取消理由と実行者の保護を訂正口で迂回させない
    String cancelled = confirmedOrder("取消");
    ResponseEntity<Void> cancelResponse =
        rest.exchange(
            "/store/orders/" + cancelled + "/cancellation",
            HttpMethod.POST,
            new HttpEntity<>("{\"reason\":\"客都合\"}", storeHeaders(STORE_A)),
            Void.class);
    assertThat(cancelResponse.getStatusCode()).as("前提: 取消が成功すること").isEqualTo(HttpStatus.NO_CONTENT);

    assertThat(
            correct(
                    managerHeaders(STORE_A),
                    cancelled,
                    """
            {"reason":"取消の訂正","fee_lines":[]}
            """)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    // 確定済み（未完了）も門の外。内容の修正は汎用更新が受け持つ
    String confirmed = confirmedOrder("未完了");
    assertThat(
            correct(
                    managerHeaders(STORE_A),
                    confirmed,
                    """
            {"reason":"先回りの訂正","fee_lines":[]}
            """)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    // 状態を戻す口は要求の型に存在しない（未知の項目は撥ねられる）
    assertThat(
            correct(
                    managerHeaders(STORE_A),
                    completedOrder("回退"),
                    """
                    {"reason":"完了の取り消し","status":"CONFIRMED","fee_lines":[]}
                    """)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("同時刻の訂正を版順で辿り、途中の追加でも既存履歴を欠落・重複させないこと")
  void paginationAndPublicResultsPreserveEveryCorrection() {
    String id = completedOrder("ページング");
    var headers = managerHeaders(STORE_A);
    var first = correct(headers, id, "{\"reason\":\"一回目\",\"fee_lines\":[]}").getBody();
    var second =
        correct(
                headers,
                id,
                "{\"reason\":\"二回目\",\"actual_end_time\":\"22:00:00\",\"fee_lines\":[]}")
            .getBody();
    jdbc.update(
        "update t_order_corrections set corrected_at = ?::timestamptz where order_id = ?",
        "2026-09-16T09:00:00Z",
        id);
    var firstPage = history(headers, id, "?size=1").getBody();
    assertThat(firstPage.path("content").get(0).path("correction_id"))
        .isEqualTo(second.path("correction_id"));
    var third =
        correct(
                headers,
                id,
                "{\"reason\":\"三回目\",\"actual_end_time\":\"23:00:00\",\"fee_lines\":[]}")
            .getBody();
    var cursor = firstPage.path("next_cursor").asString();
    var next = history(headers, id, "?size=1&cursor=" + cursor).getBody();
    assertThat(next.path("content")).hasSize(1);
    assertThat(next.path("content").get(0).path("correction_id"))
        .isEqualTo(first.path("correction_id"));
    assertThat(next.has("next_cursor")).isFalse();
    assertThat(history(headers, completedOrder("別の受注"), "?cursor=" + cursor).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(history(headers, id, "?size=999999").getBody().path("content")).hasSize(3);
    try {
      storeContext.setStoreId(STORE_A);
      var result = results.find(id).orElseThrow();
      assertThat(result.latestCorrectionId()).isEqualTo(third.path("correction_id").asString());
      assertThat(result.latestCorrection().afterVersion()).isEqualTo(result.version());
      assertThat(result.latestCorrection().after().totalFee()).isEqualTo(100);
      var page = results.corrections(id, null, 2);
      assertThat(page.content()).hasSize(2);
      var tail = results.corrections(id, page.nextCursor(), 2);
      assertThat(tail.content())
          .extracting(c -> c.correctionId())
          .containsExactly(first.path("correction_id").asString());
      storeContext.setStoreId(STORE_B);
      assertThat(results.find(id)).isEmpty();
    } finally {
      storeContext.clear();
    }
  }

  @Test
  @DisplayName("店舗管理と跨店参照は各作用域だけを読み、履歴カーソルの作用域を混用できないこと")
  void historyRequiresPermissionAndVisibleOrder() {
    String id = completedOrder("隔離");
    var manager = managerHeaders(STORE_A);
    correct(manager, id, "{\"reason\":\"一回目\",\"fee_lines\":[]}");
    correct(manager, id, "{\"reason\":\"二回目\",\"fee_lines\":[]}");
    assertThat(history(storeHeaders(STORE_A), id, "").getStatusCode()).isEqualTo(HttpStatus.OK);
    var unauthenticated = new HttpHeaders();
    unauthenticated.setBearerAuth("invalid-session");
    assertThat(history(unauthenticated, id, "").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(history(storeHeaders(STORE_A), "missing-order", "").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    var platform = new HttpHeaders();
    platform.setBearerAuth(login("admin@kizuna.test"));
    var path = "/platform/orders/" + id + "/corrections";
    var visible = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(platform), JsonNode.class);
    assertThat(visible.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(visible.getBody().path("content")).hasSize(2);
    var readerRole =
        roles.save(
            Role.builder()
                .name("訂正履歴店舗読取-" + nonce)
                .permissionIds(
                    Set.of(
                        jdbc.queryForObject(
                            "select id from t_permissions where code = 'ORDER_MANAGE'",
                            Long.class)))
                .build());
    var readerEmail = "correction-reader-" + nonce + "@kizuna.test";
    var readerCredential = UUID.randomUUID().toString();
    users.save(
        PlatformUser.builder()
            .email(readerEmail)
            .password(passwords.encode(readerCredential))
            .displayName("店舗読取")
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(Set.of(readerRole.getId()))
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(STORE_A))
            .build());
    var reader = new HttpHeaders();
    reader.setBearerAuth(loginWithPassword(readerEmail, readerCredential));
    reader.set("X-Store-ID", Long.toString(STORE_A));
    reader.set("X-Role", "store");
    assertThat(history(reader, id, "").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
            rest.exchange(path, HttpMethod.GET, new HttpEntity<>(reader), JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    var cursor = history(manager, id, "?size=1").getBody().path("next_cursor").asString();
    assertThat(
            rest.exchange(
                    path + "?cursor=" + cursor,
                    HttpMethod.GET,
                    new HttpEntity<>(platform),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    var scopedPlatform = new HttpHeaders();
    var email = "correction-scope-" + nonce + "@kizuna.test";
    var credential = UUID.randomUUID().toString();
    users.save(
        PlatformUser.builder()
            .email(email)
            .password(passwords.encode(credential))
            .displayName("店舗限定参照")
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(Set.of(roles.findByName("店長").orElseThrow().getId()))
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(STORE_A))
            .build());
    scopedPlatform.setBearerAuth(loginWithPassword(email, credential));
    assertThat(
            rest.exchange(path, HttpMethod.GET, new HttpEntity<>(scopedPlatform), JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var foreign =
        Order.builder()
            .course(courseFixture(STORE_B, 100))
            .businessDate(LocalDate.of(2026, 9, 14))
            .status(OrderStatus.CONFIRMED)
            .build();
    foreign.setStoreId(STORE_B);
    foreign.completeWith(0, 0);
    var foreignId = orders.saveAndFlush(foreign).getId();
    var foreignPath = "/platform/orders/" + foreignId + "/corrections";
    assertThat(
            rest.exchange(
                    foreignPath, HttpMethod.GET, new HttpEntity<>(scopedPlatform), JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(history(storeHeaders(STORE_A), foreignId, "").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("同じ版を同時保存した敗者は 409 となり履歴と報酬を残さないこと")
  void concurrentCorrectionsHaveOnlyOneWinner() throws Exception {
    String id = completedOrder("並行訂正");
    var headers = managerHeaders(STORE_A);
    long version = currentVersion(headers, id);
    var request =
        confirmedRequest(
            "/store/orders/" + id + "/correction-preview",
            "{\"expected_version\":" + version + ",\"reason\":\"並行\",\"fee_lines\":[]}",
            headers);
    var start = new CountDownLatch(1);
    var calls =
        IntStream.range(0, 2)
            .mapToObj(
                i ->
                    CompletableFuture.supplyAsync(
                        () -> {
                          try {
                            start.await();
                          } catch (InterruptedException e) {
                            throw new IllegalStateException(e);
                          }
                          return rest.exchange(
                              "/store/orders/" + id + "/corrections",
                              HttpMethod.POST,
                              request,
                              JsonNode.class);
                        }))
            .toList();
    start.countDown();
    var statuses = List.of(calls.get(0).get().getStatusCode(), calls.get(1).get().getStatusCode());
    assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
    var rows = history(headers, id, "").getBody().path("content");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).path("after").path("accrued_remuneration"))
        .isEqualTo(orderJson(headers, id).path("accrued_remuneration"));
  }

  private ResponseEntity<JsonNode> history(HttpHeaders headers, String id, String query) {
    return rest.exchange(
        "/store/orders/" + id + "/corrections" + query,
        HttpMethod.GET,
        new HttpEntity<>(headers),
        JsonNode.class);
  }

  /** 現物の版を載せて訂正する。画面が読み直してから送る通常の経路にあたる。 */
  private ResponseEntity<JsonNode> correct(HttpHeaders headers, String orderId, String body) {
    return correctAt(headers, orderId, currentVersion(headers, orderId), body);
  }

  /** 版を明示して訂正する。陳腐化した要求の拒否を見るテストだけが直に使う。 */
  private ResponseEntity<JsonNode> correctAt(
      HttpHeaders headers, String orderId, long version, String body) {
    String input = body.replaceFirst("\\{", "{\"expected_version\":" + version + ",");
    return submitPreviewed(
        "/store/orders/" + orderId + "/corrections",
        HttpMethod.POST,
        "/store/orders/" + orderId + "/correction-preview",
        input,
        headers);
  }

  private long currentVersion(HttpHeaders headers, String orderId) {
    return orderJson(headers, orderId).path("version").asLong();
  }

  private void assertPlatformTotalFee(String orderId, int expected) {
    var headers = new HttpHeaders();
    headers.setBearerAuth(login("admin@kizuna.test"));
    var response =
        rest.exchange(
            "/platform/orders?size=2000&sort=createdAt,desc",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    for (var row : response.getBody().path("content")) {
      if (row.path("id").asString().equals(orderId)) {
        assertThat(row.path("total_fee").isIntegralNumber()).isTrue();
        assertThat(row.path("total_fee").asInt()).isEqualTo(expected);
        return;
      }
    }
    throw new AssertionError("平台一覧に対象受注がありません: " + orderId);
  }

  @Test
  void invalidationRetainsFactsAndRejectsFurtherCorrections() {
    String id = completedMemberOrder("無効化");
    var headers = managerHeaders(STORE_A);
    var before = orderJson(headers, id);
    long entries = pointEntryRepository.count();
    var response =
        rest.postForEntity(
            "/store/orders/" + id + "/completion-invalidation",
            new HttpEntity<>(
                "{\"expected_version\":"
                    + before.path("version").asLong()
                    + ",\"reason\":\"提供前の誤完了\"}",
                headers),
            JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var change = response.getBody();
    assertThat(change.path("change_type").asString()).isEqualTo("COMPLETION_INVALIDATION");
    assertThat(change.path("before").path("total_fee").asInt()).isEqualTo(COMPLETED_FEE);
    assertThat(change.path("after").path("total_fee").asInt()).isZero();
    assertThat(change.path("after").path("completion_invalidated").asBoolean()).isTrue();
    var after = orderJson(headers, id);
    assertThat(after.path("completion_invalidated").asBoolean()).isTrue();
    assertThat(after.path("status").asString()).isEqualTo("COMPLETED");
    assertThat(after.path("completed_at")).isEqualTo(before.path("completed_at"));
    assertThat(after.path("business_date")).isEqualTo(before.path("business_date"));
    assertThat(after.path("fee_lines")).isEqualTo(before.path("fee_lines"));
    assertThat(after.path("total_remuneration").asInt()).isZero();
    assertThat(pointEntryRepository.count()).isEqualTo(entries);
    assertThat(history(headers, id, "").getBody().path("content").get(0)).isEqualTo(change);
    assertThat(
            rest.postForEntity(
                    "/store/orders/" + id + "/completion-invalidation",
                    new HttpEntity<>(
                        "{\"expected_version\":"
                            + after.path("version").asLong()
                            + ",\"reason\":\"再実行\"}",
                        headers),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(correct(headers, id, "{\"reason\":\"訂正\",\"fee_lines\":[]}").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void invalidationAuthorizationVersionAndReplacementFollowCurrentCreationRules() {
    String id = completedOrder("再提供");
    var headers = managerHeaders(STORE_A);
    long version = currentVersion(headers, id);
    assertThat(invalidate(storeHeaders(STORE_A), id, version).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(invalidate(managerHeaders(STORE_B), id, version).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    var conflict = invalidate(headers, id, version + 1);
    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(conflict.getBody().path("error").asString())
        .isEqualTo("受注が変更されています。最新の内容を再取得して確認してください");
    assertThat(conflict.getBody().path("details").path("expected_version").asString())
        .isEqualTo("最新の内容を再取得して確認してください");
    assertThat(history(headers, id, "").getBody().path("content")).isEmpty();
    var source = orderJson(headers, id);
    String body =
        "{\"business_date\":\""
            + LocalDate.now()
            + "\",\"cast_id\":\""
            + source.path("cast_id").asString()
            + "\",\"course_id\":\""
            + source.path("course").path("service_id").asString()
            + "\",\"replacement_for_order_id\":\""
            + id
            + "\"}";
    assertThat(
            rest.postForEntity(
                    "/store/orders/preview", new HttpEntity<>(body, headers), JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(invalidate(headers, id, version).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var request = confirmedRequest("/store/orders/preview", body, headers);
    var replacement = rest.postForEntity("/store/orders", request, JsonNode.class);
    assertThat(replacement.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(replacement.getBody().path("replacement_for_order_id").asString()).isEqualTo(id);
    assertThat(replacement.getBody().path("id").asString()).isNotEqualTo(id);
    assertThat(replacement.getBody().path("status").asString()).isEqualTo("CONFIRMED");
    assertThat(replacement.getBody().path("completion_invalidated").asBoolean()).isFalse();
    assertThat(replacement.getBody().path("total_fee").asInt()).isEqualTo(100);
    storeContext.setStoreId(STORE_A);
    try {
      var result = results.find(id).orElseThrow();
      assertThat(result.completionInvalidated()).isTrue();
      assertThat(result.accruedRemuneration()).isZero();
      assertThat(result.latestCorrection().changeType()).isEqualTo("COMPLETION_INVALIDATION");
    } finally {
      storeContext.clear();
    }
  }

  @Test
  void concurrentCorrectionAndInvalidationLeaveOnlyOneChange() throws Exception {
    String id = completedOrder("訂正と無効化の競合");
    var headers = managerHeaders(STORE_A);
    long version = currentVersion(headers, id);
    var correction =
        confirmedRequest(
            "/store/orders/" + id + "/correction-preview",
            "{\"expected_version\":" + version + ",\"reason\":\"競合訂正\",\"fee_lines\":[]}",
            headers);
    var start = new CountDownLatch(1);
    var first =
        CompletableFuture.supplyAsync(
            () -> {
              awaitStart(start);
              return rest.postForEntity(
                  "/store/orders/" + id + "/corrections", correction, JsonNode.class);
            });
    var second =
        CompletableFuture.supplyAsync(
            () -> {
              awaitStart(start);
              return invalidate(headers, id, version);
            });
    start.countDown();
    assertThat(List.of(first.get().getStatusCode(), second.get().getStatusCode()))
        .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
    var changes = history(headers, id, "").getBody().path("content");
    assertThat(changes).hasSize(1);
    assertThat(changes.get(0).path("after").path("total_fee"))
        .isEqualTo(orderJson(headers, id).path("total_fee"));
    assertThat(changes.get(0).path("after").path("accrued_remuneration"))
        .isEqualTo(orderJson(headers, id).path("accrued_remuneration"));
  }

  private void awaitStart(CountDownLatch start) {
    try {
      start.await();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }

  @Test
  void failedInvalidationHistoryInsertRollsBackTheOrder() {
    String id = completedOrder("無効化の保存失敗");
    var headers = managerHeaders(STORE_A);
    var before = orderJson(headers, id);
    jdbc.execute(
        "CREATE FUNCTION fail_invalidation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.change_type = 'COMPLETION_INVALIDATION' THEN RAISE EXCEPTION 'test failure'; END IF; RETURN NEW; END $$");
    jdbc.execute(
        "CREATE TRIGGER fail_invalidation BEFORE INSERT ON t_order_corrections FOR EACH ROW EXECUTE FUNCTION fail_invalidation()");
    try {
      assertThat(invalidate(headers, id, before.path("version").asLong()).getStatusCode())
          .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(orderJson(headers, id)).isEqualTo(before);
      assertThat(history(headers, id, "").getBody().path("content")).isEmpty();
    } finally {
      jdbc.execute("DROP TRIGGER fail_invalidation ON t_order_corrections");
      jdbc.execute("DROP FUNCTION fail_invalidation()");
    }
    assertThat(invalidate(headers, id, before.path("version").asLong()).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void invalidationReasonIsValidatedAfterTrimming() {
    String id = completedOrder("理由の境界");
    var headers = managerHeaders(STORE_A);
    long version = currentVersion(headers, id);
    String reason = "理".repeat(500);
    var response =
        rest.postForEntity(
            "/store/orders/" + id + "/completion-invalidation",
            new HttpEntity<>(
                "{\"expected_version\":" + version + ",\"reason\":\"  " + reason + "  \"}",
                headers),
            JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().path("reason").asString()).isEqualTo(reason);
  }

  private ResponseEntity<JsonNode> invalidate(HttpHeaders headers, String id, long version) {
    return rest.postForEntity(
        "/store/orders/" + id + "/completion-invalidation",
        new HttpEntity<>("{\"expected_version\":" + version + ",\"reason\":\"未提供の誤完了\"}", headers),
        JsonNode.class);
  }

  private JsonNode orderJson(HttpHeaders headers, String orderId) {
    return rest.exchange(
            "/store/orders/" + orderId, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class)
        .getBody();
  }

  /** 会計 12000 円で完了した、顧客も会員も着かない受注（付与は起こらない）。 */
  private String completedOrder(String label) {
    return complete(confirmedOrder(label, null), null);
  }

  /** 会計 12000 円で完了した会員の受注。付与 120 ポイントが台帳へ入るので、訂正が付与を動かさないことを見られる。 */
  private String completedMemberOrder(String label) {
    return complete(confirmedOrder(label, linkedCustomer(label)), null);
  }

  /**
   * ポイント利用の行を持つ完了済みの受注。
   *
   * <p>利用には残高が要るので、同じ顧客で 1 件先に完了させて付与を積む — この経路（完了時の会員解決）が
   * ポイント利用の行が生まれる唯一の入口であり、台帳へ直接積んで作る形では門の対象になる行が同じ由来にならない。
   */
  private String completedOrderUsingPoints(String label, int usePoints) {
    String customerId = linkedCustomer(label);
    complete(confirmedOrder(label + "-獲得", customerId), null);
    return complete(confirmedOrder(label + "-利用", customerId), usePoints);
  }

  private String complete(String orderId, Integer usePoints) {
    ResponseEntity<JsonNode> completed =
        rest.exchange(
            "/store/orders/" + orderId + "/completion",
            HttpMethod.POST,
            completionFixtureRequest(orderId, COMPLETED_FEE, usePoints, storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(completed.getStatusCode()).as("前提: 完了が成功すること").isEqualTo(HttpStatus.OK);
    return orderId;
  }

  private String confirmedOrder(String label) {
    return confirmedOrder(label, null);
  }

  private String confirmedOrder(String label, String customerId) {
    String castId = createCast(label);
    String body =
        "{\"receptionist_id\": "
            + SEED_RECEPTIONIST_ID
            + ", \"business_date\": \""
            + LocalDate.now()
            + "\", \"cast_id\": \""
            + castId
            + "\""
            + (customerId == null
                ? ""
                : ", \"customer_selection\": {\"mode\":\"EXISTING\",\"customer_id\": \""
                    + customerId
                    + "\"}")
            + ", \"remarks\": \""
            + label
            + "\"}";
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/orders", orderFixtureRequest(body, storeHeaders(STORE_A)), JsonNode.class);
    assertThat(created.getStatusCode()).as("前提: 受注作成が成功すること").isEqualTo(HttpStatus.CREATED);
    return created.getBody().path("id").asString();
  }

  /** 新しい会員を登録し、新しい顧客行へ紐づけてその顧客 ID を返す。 */
  private String linkedCustomer(String label) {
    HttpHeaders anonymous = new HttpHeaders();
    anonymous.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> member =
        rest.postForEntity(
            "/platform/members",
            new HttpEntity<>(
                "{\"email\": \"correction-it-"
                    + nonce
                    + "-"
                    + System.nanoTime()
                    + "@kizuna.test\", \"password\": \"password1234\", \"display_name\": \"訂正検証会員\"}",
                anonymous),
            JsonNode.class);
    assertThat(member.getStatusCode()).as("前提: 会員登録が成功すること").isEqualTo(HttpStatus.CREATED);

    ResponseEntity<JsonNode> customer =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>(
                "{\"name\": \"訂正検証-" + label + "-" + nonce + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(customer.getStatusCode().is2xxSuccessful()).as("前提: 顧客作成が成功すること").isTrue();
    String customerId = customer.getBody().path("id").asString();

    ResponseEntity<JsonNode> linked =
        rest.exchange(
            "/store/customers/" + customerId + "/member-link",
            HttpMethod.POST,
            new HttpEntity<>(
                "{\"member_code\": \"" + member.getBody().path("member_code").asString() + "\"}",
                storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(linked.getStatusCode()).as("前提: 会員の紐づけが成功すること").isEqualTo(HttpStatus.CREATED);
    return customerId;
  }

  private String createCast(String label) {
    ResponseEntity<JsonNode> created =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>(
                "{\"name\": \"訂正検証-" + label + "-" + nonce + "\"}", storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode()).as("前提: キャスト作成が成功すること").isEqualTo(HttpStatus.CREATED);
    return created.getBody().path("id").asString();
  }
}
