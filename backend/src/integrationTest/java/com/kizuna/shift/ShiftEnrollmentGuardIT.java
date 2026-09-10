package com.kizuna.shift;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.cast.application.CastEnrollmentService;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shared.storescope.StoreContext;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

class ShiftEnrollmentGuardIT extends CrossStoreTestSupport {
  @Autowired CastEnrollmentService lifecycle;
  @Autowired StoreContext storeContext;
  @Autowired PlatformTransactionManager transactions;
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void withdrawnEnrollmentCannotReceiveNewOrReassignedShift(boolean reassign) {
    String target = createEnrollment(STORE_A);
    String shift = reassign ? createShift(createEnrollment(STORE_A)) : null;
    transition(target, "withdrawal");
    assertThat(assign(target, shift).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(shiftCount(target)).isZero();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void suspendedEnrollmentCanReceiveNewOrReassignedShift(boolean reassign) {
    String target = createEnrollment(STORE_A);
    String shift = reassign ? createShift(createEnrollment(STORE_A)) : null;
    transition(target, "suspension");
    var response = assign(target, shift);
    assertThat(response.getStatusCode()).isEqualTo(reassign ? HttpStatus.OK : HttpStatus.CREATED);
    assertThat(response.getBody().path("cast_id").asString()).isEqualTo(target);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void otherStoreEnrollmentIsNotFound(boolean reassign) {
    String target = createEnrollment(STORE_B);
    String shift = reassign ? createShift(createEnrollment(STORE_A)) : null;
    assertThat(assign(target, shift).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(shiftCount(target)).isZero();
  }

  @Test
  void existingWithdrawnAssignmentCanStillHaveTimeAndStatusCorrected() {
    String target = createEnrollment(STORE_A);
    String shift = createShift(target);
    transition(target, "withdrawal");
    for (var body :
        List.of(
            Map.of("cast_id", target, "start_time", "19:00", "status", "CONFIRMED"),
            Map.of("end_time", "22:00", "status", "TENTATIVE"))) {
      var response =
          rest.exchange(
              "/store/shifts/" + shift,
              HttpMethod.PUT,
              new HttpEntity<>(body, storeHeaders(STORE_A)),
              JsonNode.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().path("cast_id").asString()).isEqualTo(target);
      for (var field : body.entrySet()) {
        if (!field.getKey().endsWith("time")) {
          assertThat(response.getBody().path(field.getKey()).asString())
              .isEqualTo(field.getValue());
        } else {
          assertThat(response.getBody().path(field.getKey()).asString())
              .startsWith(field.getValue());
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void withdrawalSerializesWithShiftAssignment(boolean reassign) throws Exception {
    String target = createEnrollment(STORE_A);
    String original = createEnrollment(STORE_A);
    String shift = reassign ? createShift(original) : null;
    CountDownLatch held = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger holderPid = new AtomicInteger();
    var withdrawal =
        CompletableFuture.runAsync(
            () -> {
              storeContext.setStoreId(STORE_A);
              try {
                new TransactionTemplate(transactions)
                    .executeWithoutResult(
                        tx -> {
                          lifecycle.withdraw(target, "yamada.jiro@kizuna.test");
                          holderPid.set(
                              jdbc.queryForObject("select pg_backend_pid()", Integer.class));
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
      var assignment = CompletableFuture.supplyAsync(() -> assign(target, shift));
      awaitBlockedBy(assignment, holderPid.get());
      release.countDown();
      withdrawal.get(10, TimeUnit.SECONDS);
      assertThat(assignment.get(10, TimeUnit.SECONDS).getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(shiftCount(target)).isZero();
      if (reassign) {
        assertThat(
                jdbc.queryForObject(
                    "select cast_id from t_shifts where id = ?", String.class, shift))
            .isEqualTo(original);
      }
    } finally {
      release.countDown();
      withdrawal.get(10, TimeUnit.SECONDS);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shiftAssignmentLocksStoreBeforeEnrollment(boolean reassign) throws Exception {
    String target = createEnrollment(STORE_A);
    String shift = reassign ? createShift(createEnrollment(STORE_A)) : null;
    try (Connection holder = dataSource.getConnection()) {
      holder.setAutoCommit(false);
      int holderPid;
      try (var statement = holder.createStatement();
          var result = statement.executeQuery("select pg_backend_pid()")) {
        assertThat(result.next()).isTrue();
        holderPid = result.getInt(1);
      }
      try (var statement =
          holder.prepareStatement("select id from t_stores where id = ? for update")) {
        statement.setLong(1, STORE_A);
        assertThat(statement.executeQuery().next()).isTrue();
      }
      var assignment = CompletableFuture.supplyAsync(() -> assign(target, shift));
      try {
        awaitBlockedBy(assignment, holderPid);
        try (var statement =
            holder.prepareStatement(
                "select id from t_cast_enrollments where id = ? for update nowait")) {
          statement.setString(1, target);
          assertThat(statement.executeQuery().next()).as("店舗を待つ間は在籍をロックしないこと").isTrue();
        }
      } finally {
        holder.rollback();
      }
      assertThat(assignment.get(10, TimeUnit.SECONDS).getStatusCode())
          .isEqualTo(reassign ? HttpStatus.OK : HttpStatus.CREATED);
    }
  }

  private void awaitBlockedBy(CompletableFuture<?> request, int holderPid) throws Exception {
    for (int attempt = 0; attempt < 100; attempt++) {
      if (jdbc.queryForObject(
          "select exists(select 1 from pg_stat_activity where datname = current_database() and ? = any(pg_blocking_pids(pid)))",
          Boolean.class,
          holderPid)) {
        return;
      }
      if (request.isDone()) break;
      TimeUnit.MILLISECONDS.sleep(50);
    }
    throw new AssertionError("シフト操作が保持中の取引によるロック解放を待たなかった");
  }

  private int shiftCount(String target) {
    return jdbc.queryForObject(
        "select count(*) from t_shifts where cast_id = ?", Integer.class, target);
  }

  private String createEnrollment(long storeId) {
    CastEnrollment enrollment = CastEnrollment.builder().build();
    enrollment.setStoreId(storeId);
    return saveEnrollmentFixture(enrollment, "シフト在籍検証", null).getId();
  }

  private void transition(String target, String operation) {
    assertThat(
            rest.postForEntity(
                    "/store/casts/" + target + "/" + operation,
                    new HttpEntity<>(storeHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  private String createShift(String target) {
    var response = assign(target, null);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody().path("id").asString();
  }

  private ResponseEntity<JsonNode> assign(String target, String shift) {
    return rest.exchange(
        shift == null ? "/store/shifts" : "/store/shifts/" + shift,
        shift == null ? HttpMethod.POST : HttpMethod.PUT,
        new HttpEntity<>(
            Map.of(
                "cast_id", target,
                "work_date", "2999-06-01",
                "start_time", "18:00",
                "end_time", "23:00"),
            storeHeaders(STORE_A)),
        JsonNode.class);
  }
}
