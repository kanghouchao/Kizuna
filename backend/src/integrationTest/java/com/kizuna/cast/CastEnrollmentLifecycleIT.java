package com.kizuna.cast;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.cast.application.CastEnrollmentService;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

class CastEnrollmentLifecycleIT extends CrossStoreTestSupport {
  @Autowired PlatformUserRepository users;
  @Autowired PasswordEncoder passwords;
  @Autowired CastEnrollmentService lifecycle;
  @Autowired StoreContext storeContext;
  @Autowired PlatformTransactionManager transactions;
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;

  @Test
  void withdrawalInvalidatesPendingInvitationAndRejectsReissueAndAcceptance() {
    String id = create();
    var invitation =
        rest.postForEntity(
            "/store/casts/" + id + "/invitation",
            new HttpEntity<>(managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(invitation.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String token = invitation.getBody().path("token").asString();
    assertThat(
            rest.postForEntity(
                    "/store/casts/" + id + "/withdrawal",
                    new HttpEntity<>(storeHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            jdbc.queryForObject(
                "select status from t_cast_invitations where token = ?", String.class, token))
        .isEqualTo("INVALIDATED");
    assertThat(
            rest.postForEntity(
                    "/store/casts/" + id + "/invitation",
                    new HttpEntity<>(managerHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    String email = "withdrawn-invitation-" + System.nanoTime() + "@kizuna.test";
    assertThat(
            rest.postForEntity(
                    "/platform/cast-invitations/acceptance",
                    Map.of(
                        "token",
                        token,
                        "email",
                        email,
                        "password",
                        NEW_ACCOUNT_PASSWORD,
                        "display_name",
                        "退店済み"),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(users.findByEmail(email)).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select cast_id from t_cast_enrollments where id = ?", Long.class, id))
        .isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"suspension", "resumption", "withdrawal"})
  void stateChangeLocksStoreBeforeEnrollment(String operation) throws Exception {
    String id = create();
    var headers = storeHeaders(STORE_A);
    if (operation.equals("resumption")) {
      assertThat(
              rest.postForEntity(
                      "/store/casts/" + id + "/suspension",
                      new HttpEntity<>(headers),
                      JsonNode.class)
                  .getStatusCode())
          .isEqualTo(HttpStatus.OK);
    }
    try (Connection holder = dataSource.getConnection()) {
      holder.setAutoCommit(false);
      try (var statement =
          holder.prepareStatement("select id from t_stores where id = ? for update")) {
        statement.setLong(1, STORE_A);
        assertThat(statement.executeQuery().next()).isTrue();
      }
      var waiting =
          CompletableFuture.supplyAsync(
              () ->
                  rest.postForEntity(
                      "/store/casts/" + id + "/" + operation,
                      new HttpEntity<>(headers),
                      JsonNode.class));
      try {
        boolean blocked = false;
        for (int attempt = 0; attempt < 100; attempt++) {
          if (jdbc.queryForObject(
                  "select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock' and query like '%t_stores%'",
                  Integer.class)
              > 0) {
            blocked = true;
            break;
          }
          if (waiting.isDone()) break;
          TimeUnit.MILLISECONDS.sleep(50);
        }
        assertThat(blocked).as("店舗行のロック待ちに到達すること").isTrue();
        try (var statement =
            holder.prepareStatement(
                "select id from t_cast_enrollments where id = ? for update nowait")) {
          statement.setString(1, id);
          assertThat(statement.executeQuery().next()).as("店舗で待つ間は在籍行を押さえていないこと").isTrue();
        }
      } finally {
        holder.rollback();
      }
      assertThat(waiting.get(30, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
  }

  private String create() {
    var response =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>(Map.of("name", "在籍履歴検証"), storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody().path("id").asString();
  }

  private JsonNode history(String id, String kind) {
    var response =
        rest.exchange(
            "/store/casts/" + id + "/" + kind,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  @Test
  void creationAndTransitionsAreReadableInOrder() {
    String id = create();
    assertThat(history(id, "status-histories").path("content").size()).isEqualTo(1);
    for (String action : new String[] {"suspension", "resumption", "withdrawal"}) {
      var response =
          rest.postForEntity(
              "/store/casts/" + id + "/" + action,
              new HttpEntity<>(storeHeaders(STORE_A)),
              JsonNode.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    JsonNode rows = history(id, "status-histories").path("content");
    assertThat(rows.size()).isEqualTo(4);
    assertThat(rows.get(0).path("new_status").asString()).isEqualTo("WITHDRAWN");
    assertThat(rows.get(1).path("new_status").asString()).isEqualTo("ENROLLED");
    assertThat(rows.get(2).path("new_status").asString()).isEqualTo("SUSPENDED");
    assertThat(rows.get(3).path("new_status").asString()).isEqualTo("ENROLLED");
    for (JsonNode row : rows) {
      assertThat(row.path("actor_id").asLong()).isPositive();
      assertThat(row.path("recorded_at").asString()).isNotBlank();
    }
    var repeated =
        rest.postForEntity(
            "/store/casts/" + id + "/withdrawal",
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(history(id, "status-histories").path("content").size()).isEqualTo(4);
  }

  @Test
  void internalSnapshotsSurviveDefinitionDeletionAndIgnoreUnchangedValues() {
    String id = create();
    String key = "internal_" + System.nanoTime();
    var definition =
        rest.postForEntity(
            "/store/casts/fields",
            new HttpEntity<>(
                Map.of("key", key, "label", "内部", "is_public", false), managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(definition.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String definitionId = definition.getBody().path("id").asString();
    for (String value : new String[] {"初回", "初回", "変更"}) {
      var updated =
          rest.exchange(
              "/store/casts/" + id,
              HttpMethod.PUT,
              new HttpEntity<>(Map.of("custom_fields", Map.of(key, value)), storeHeaders(STORE_A)),
              JsonNode.class);
      assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    JsonNode rows = history(id, "snapshots").path("content");
    assertThat(rows.size()).isEqualTo(2);
    assertThat(rows.get(0).path("custom_fields").path(key).asString()).isEqualTo("初回");
    assertThat(rows.get(1).path("custom_fields").size()).isZero();
    var deleted =
        rest.exchange(
            "/store/casts/fields/" + definitionId,
            HttpMethod.DELETE,
            new HttpEntity<>(managerHeaders(STORE_A)),
            Void.class);
    assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    rows = history(id, "snapshots").path("content");
    assertThat(rows.size()).isEqualTo(3);
    assertThat(rows.get(0).path("custom_fields").path(key).asString()).isEqualTo("変更");
  }

  @Test
  void withdrawnHistoryCannotBeDeletedAndIsScopedAndPaged() {
    String id = create();
    rest.postForEntity(
        "/store/casts/" + id + "/withdrawal",
        new HttpEntity<>(storeHeaders(STORE_A)),
        JsonNode.class);
    JsonNode first = history(id, "status-histories?size=1");
    assertThat(first.path("content").size()).isEqualTo(1);
    JsonNode second =
        history(id, "status-histories?size=1&cursor=" + first.path("next_cursor").asString());
    assertThat(second.path("content").get(0).path("new_status").asString()).isEqualTo("ENROLLED");
    assertThat(second.path("next_cursor").isMissingNode() || second.path("next_cursor").isNull())
        .isTrue();
    var deleted =
        rest.exchange(
            "/store/casts/" + id,
            HttpMethod.DELETE,
            new HttpEntity<>(storeHeaders(STORE_A)),
            Void.class);
    assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    CastEnrollment other = CastEnrollment.builder().build();
    other.setStoreId(STORE_B);
    String otherId = saveEnrollmentFixture(other, "別店舗", null).getId();
    for (String suffix : new String[] {"status-histories", "snapshots"}) {
      var hidden =
          rest.exchange(
              "/store/casts/" + otherId + "/" + suffix,
              HttpMethod.GET,
              new HttpEntity<>(storeHeaders(STORE_A)),
              JsonNode.class);
      assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
    var invalid =
        rest.exchange(
            "/store/casts/" + id + "/status-histories?cursor=invalid",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void withdrawalClosesSubmissionAndApprovalWithoutRevokingStoresOrHidingHistory() {
    String email = "lifecycle-" + System.nanoTime() + "@kizuna.test";
    PlatformUser user =
        users.save(
            PlatformUser.builder()
                .email(email)
                .password(passwords.encode(NEW_ACCOUNT_PASSWORD))
                .displayName("在籍検証")
                .userType(UserType.CAST)
                .enabled(true)
                .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of(STORE_A))
                .build());
    CastEnrollment enrollment = CastEnrollment.builder().build();
    enrollment.setStoreId(STORE_A);
    String id = saveEnrollmentFixture(enrollment, "希望検証", user.getId()).getId();
    HttpHeaders bearer = new HttpHeaders();
    bearer.setContentType(MediaType.APPLICATION_JSON);
    bearer.setBearerAuth(loginWithPassword(email, NEW_ACCOUNT_PASSWORD));
    var request =
        Map.of(
            "store_id",
            STORE_A,
            "work_date",
            "2999-06-01",
            "start_time",
            "18:00",
            "end_time",
            "23:00");
    var suspended =
        rest.postForEntity(
            "/store/casts/" + id + "/suspension",
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(suspended.getStatusCode()).isEqualTo(HttpStatus.OK);
    var submitted =
        rest.postForEntity(
            "/platform/me/shift-requests", new HttpEntity<>(request, bearer), JsonNode.class);
    assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String requestId = submitted.getBody().path("id").asString();
    var firstApproval =
        rest.postForEntity(
            "/store/shift-requests/" + requestId + "/approval",
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(firstApproval.getStatusCode()).isEqualTo(HttpStatus.OK);
    String shiftId = firstApproval.getBody().path("shift_id").asString();
    var pending =
        rest.postForEntity(
            "/platform/me/shift-requests", new HttpEntity<>(request, bearer), JsonNode.class);
    assertThat(pending.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String pendingId = pending.getBody().path("id").asString();
    var change =
        Map.of(
            "shift_id",
            shiftId,
            "work_date",
            "2999-06-02",
            "start_time",
            "19:00",
            "end_time",
            "23:00");
    var pendingChange =
        rest.postForEntity(
            "/platform/me/shift-requests/changes",
            new HttpEntity<>(change, bearer),
            JsonNode.class);
    assertThat(pendingChange.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String changeId = pendingChange.getBody().path("id").asString();
    assertThat(
            rest.postForEntity(
                    "/store/casts/" + id + "/withdrawal",
                    new HttpEntity<>(storeHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            rest.postForEntity(
                    "/platform/me/shift-requests",
                    new HttpEntity<>(request, bearer),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            rest.postForEntity(
                    "/platform/me/shift-requests/changes",
                    new HttpEntity<>(change, bearer),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(users.findById(user.getId()).orElseThrow().getStoreIds()).containsExactly(STORE_A);
    var stores =
        rest.exchange(
            "/platform/me/stores", HttpMethod.GET, new HttpEntity<>(bearer), JsonNode.class);
    assertThat(stores.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(stores.getBody().size()).isZero();
    CastEnrollment reenrollment = CastEnrollment.builder().build();
    reenrollment.setStoreId(STORE_A);
    saveEnrollmentFixture(reenrollment, "再入店", user.getId());
    for (String pendingRequestId : new String[] {pendingId, changeId}) {
      assertThat(
              rest.postForEntity(
                      "/store/shift-requests/" + pendingRequestId + "/approval",
                      new HttpEntity<>(storeHeaders(STORE_A)),
                      JsonNode.class)
                  .getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    var inbox =
        rest.exchange(
            "/store/shift-requests",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(inbox.getStatusCode()).isEqualTo(HttpStatus.OK);
    for (JsonNode row : inbox.getBody()) {
      if (Set.of(pendingId, changeId).contains(row.path("id").asString()))
        assertThat(row.path("approvable").asBoolean()).isFalse();
    }
    var past =
        rest.exchange(
            "/platform/me/shift-requests",
            HttpMethod.GET,
            new HttpEntity<>(bearer),
            JsonNode.class);
    assertThat(past.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(past.getBody().path("content").size()).isEqualTo(3);
    var candidates =
        rest.exchange(
            "/store/orders/cast-candidates",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(candidates.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(candidates.getBody().toString()).doesNotContain(id);
  }

  @Test
  void concurrentWithdrawalsProduceExactlyOneTransition() throws Exception {
    String id = create();
    CountDownLatch start = new CountDownLatch(1);
    var headers = storeHeaders(STORE_A);
    var operation =
        (Supplier<HttpStatus>)
            () -> {
              try {
                if (!start.await(10, TimeUnit.SECONDS))
                  throw new IllegalStateException("開始待機が時間切れです");
              } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
              }
              return HttpStatus.valueOf(
                  rest.postForEntity(
                          "/store/casts/" + id + "/withdrawal",
                          new HttpEntity<>(headers),
                          JsonNode.class)
                      .getStatusCode()
                      .value());
            };
    var first = CompletableFuture.supplyAsync(operation);
    var second = CompletableFuture.supplyAsync(operation);
    start.countDown();
    assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
        .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.BAD_REQUEST);
    assertThat(history(id, "status-histories").path("content").size()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NEW_SUBMIT", "CHANGE_SUBMIT", "NEW_APPROVE", "CHANGE_APPROVE"})
  void withdrawalSerializesWithSubmissionAndApproval(String operation) throws Exception {
    String email = "race-" + System.nanoTime() + "@kizuna.test";
    PlatformUser user =
        users.save(
            PlatformUser.builder()
                .email(email)
                .password(passwords.encode(NEW_ACCOUNT_PASSWORD))
                .displayName("競合検証")
                .userType(UserType.CAST)
                .enabled(true)
                .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of(STORE_A))
                .build());
    CastEnrollment enrollment = CastEnrollment.builder().build();
    enrollment.setStoreId(STORE_A);
    String id = saveEnrollmentFixture(enrollment, "競合検証", user.getId()).getId();
    HttpHeaders bearer = new HttpHeaders();
    bearer.setContentType(MediaType.APPLICATION_JSON);
    bearer.setBearerAuth(loginWithPassword(email, NEW_ACCOUNT_PASSWORD));
    var requestedSlot =
        Map.of(
            "store_id",
            STORE_A,
            "work_date",
            "2999-06-01",
            "start_time",
            "18:00",
            "end_time",
            "23:00");
    var newRequest =
        rest.postForEntity(
            "/platform/me/shift-requests", new HttpEntity<>(requestedSlot, bearer), JsonNode.class);
    assertThat(newRequest.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String pendingId = newRequest.getBody().path("id").asString();
    String path = "/platform/me/shift-requests";
    HttpEntity<?> entity = new HttpEntity<>(requestedSlot, bearer);
    if (operation.startsWith("CHANGE")) {
      var approved =
          rest.postForEntity(
              "/store/shift-requests/" + pendingId + "/approval",
              new HttpEntity<>(storeHeaders(STORE_A)),
              JsonNode.class);
      assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
      var requestedChange =
          Map.of(
              "shift_id",
              approved.getBody().path("shift_id").asString(),
              "work_date",
              "2999-06-02",
              "start_time",
              "19:00",
              "end_time",
              "23:00");
      path += "/changes";
      entity = new HttpEntity<>(requestedChange, bearer);
      if (operation.endsWith("APPROVE")) {
        var change = rest.postForEntity(path, entity, JsonNode.class);
        assertThat(change.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        pendingId = change.getBody().path("id").asString();
      }
    }
    if (operation.endsWith("APPROVE")) {
      path = "/store/shift-requests/" + pendingId + "/approval";
      entity = new HttpEntity<>(storeHeaders(STORE_A));
    }
    int before =
        rest.exchange(
                "/platform/me/shift-requests",
                HttpMethod.GET,
                new HttpEntity<>(bearer),
                JsonNode.class)
            .getBody()
            .path("content")
            .size();
    CountDownLatch held = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    var withdrawal =
        CompletableFuture.runAsync(
            () -> {
              storeContext.setStoreId(STORE_A);
              try {
                new TransactionTemplate(transactions)
                    .executeWithoutResult(
                        tx -> {
                          lifecycle.withdraw(id, "yamada.jiro@kizuna.test");
                          held.countDown();
                          try {
                            assertThat(release.await(30, TimeUnit.SECONDS)).isTrue();
                          } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(ex);
                          }
                        });
              } finally {
                storeContext.clear();
              }
            });
    try {
      assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
      final String url = path;
      final HttpEntity<?> body = entity;
      var competing =
          CompletableFuture.supplyAsync(() -> rest.postForEntity(url, body, JsonNode.class));
      boolean waiting = false;
      for (int attempt = 0; attempt < 100; attempt++) {
        if (jdbc.queryForObject(
                "select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock' and query like '%t_cast_enrollments%'",
                Integer.class)
            > 0) {
          waiting = true;
          break;
        }
        if (competing.isDone()) break;
        TimeUnit.MILLISECONDS.sleep(50);
      }
      assertThat(waiting).as("希望操作が在籍行のロック解放を待つこと").isTrue();
      release.countDown();
      withdrawal.get(10, TimeUnit.SECONDS);
      assertThat(competing.get(10, TimeUnit.SECONDS).getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
      var after =
          rest.exchange(
                  "/platform/me/shift-requests",
                  HttpMethod.GET,
                  new HttpEntity<>(bearer),
                  JsonNode.class)
              .getBody()
              .path("content");
      assertThat(after.size()).isEqualTo(before);
      if (operation.endsWith("APPROVE")) {
        String targetId = pendingId;
        boolean found = false;
        for (JsonNode row : after)
          if (row.path("id").asString().equals(targetId)) {
            found = true;
            assertThat(row.path("status").asString()).isEqualTo("PENDING");
          }
        assertThat(found).isTrue();
      }
    } finally {
      release.countDown();
      withdrawal.get(10, TimeUnit.SECONDS);
    }
  }
}
