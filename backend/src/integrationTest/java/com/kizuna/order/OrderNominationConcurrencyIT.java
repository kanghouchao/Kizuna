package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.cast.application.CastEnrollmentService;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.order.api.dto.CustomerSelectionRequest;
import com.kizuna.order.api.dto.OrderCreateRequest;
import com.kizuna.order.application.OrderService;
import com.kizuna.order.domain.ContactSnapshot;
import com.kizuna.order.domain.GuestContactConsent;
import com.kizuna.order.domain.OrderApplication;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftStatus;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

class OrderNominationConcurrencyIT extends CrossStoreTestSupport {
  @Autowired private CustomerRepository customers;
  @Autowired private OrderService orders;
  @Autowired private CastEnrollmentService lifecycle;
  @Autowired private StoreContext storeContext;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private OrderApplicationRepository applications;
  @Autowired private ShiftRepository shifts;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;

  @ParameterizedTest
  @ValueSource(strings = {"CREATE", "UPDATE", "CONFIRM"})
  void withdrawalSerializesWithNominationWrites(String operation) throws Exception {
    var fixture = prepare(operation);
    var held = new CompletableFuture<Integer>();
    var release = new CountDownLatch(1);
    var withdrawal =
        CompletableFuture.runAsync(
            () -> {
              storeContext.setStoreId(STORE_A);
              try {
                new TransactionTemplate(transactions)
                    .executeWithoutResult(
                        tx -> {
                          lifecycle.withdraw(fixture.castId(), "yamada.jiro@kizuna.test");
                          held.complete(
                              jdbc.queryForObject("select pg_backend_pid()", Integer.class));
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
      int blocker = held.get(10, TimeUnit.SECONDS);
      var competing = CompletableFuture.supplyAsync(() -> execute(fixture));
      assertWaitingOn(competing, blocker, "t_stores");
      release.countDown();
      withdrawal.get(10, TimeUnit.SECONDS);
      var response = competing.get(10, TimeUnit.SECONDS);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(response.getBody().path("details").has("confirmation_token")).isTrue();
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from t_orders where cast_id = ?",
                  Integer.class,
                  fixture.castId()))
          .as("退店した在籍を指す新しい受注が残らないこと")
          .isZero();
      if (operation.equals("UPDATE")) {
        assertThat(
                jdbc.queryForObject(
                    "select cast_id from t_orders where id = ?",
                    String.class,
                    fixture.resourceId()))
            .isEqualTo(fixture.originalCastId());
      }
      if (operation.equals("CONFIRM")) {
        var application = applications.findById(fixture.resourceId()).orElseThrow();
        assertThat(application.getStatus()).isEqualTo(OrderApplicationStatus.PENDING);
        assertThat(application.getOrderId()).isNull();
      }
    } finally {
      release.countDown();
      withdrawal.get(10, TimeUnit.SECONDS);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"CREATE", "UPDATE", "CONFIRM"})
  void nominationWritesLockStoreBeforeEnrollment(String operation) throws Exception {
    var fixture = prepare(operation);
    try (Connection holder = dataSource.getConnection()) {
      holder.setAutoCommit(false);
      int blocker;
      try (var statement = holder.createStatement();
          var result = statement.executeQuery("select pg_backend_pid()")) {
        assertThat(result.next()).isTrue();
        blocker = result.getInt(1);
      }
      try (var statement =
          holder.prepareStatement("select id from t_stores where id = ? for update")) {
        statement.setLong(1, STORE_A);
        assertThat(statement.executeQuery().next()).isTrue();
      }
      var competing = CompletableFuture.supplyAsync(() -> execute(fixture));
      try {
        assertWaitingOn(competing, blocker, "t_stores");
        try (var statement =
            holder.prepareStatement(
                "select id from t_cast_enrollments where id = ? for update nowait")) {
          statement.setString(1, fixture.castId());
          assertThat(statement.executeQuery().next()).as("店舗ロックを待つ指名操作が在籍行を先に押さえていないこと").isTrue();
        }
      } finally {
        holder.rollback();
      }
      assertThat(competing.get(30, TimeUnit.SECONDS).getStatusCode())
          .isEqualTo(operation.equals("UPDATE") ? HttpStatus.OK : HttpStatus.CREATED);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"CREATE", "CONFIRM"})
  void customerLockContentionRollsBackNominationAndAllowsRetry(String operation) throws Exception {
    var prepared = prepare(operation);
    Customer customer = Customer.builder().name("指名顧客ロック検証").build();
    customer.setStoreId(STORE_A);
    String customerId = customers.save(customer).getId();
    Map<String, Object> body = new HashMap<>(prepared.body());
    body.put("customer_selection", Map.of("mode", "EXISTING", "customer_id", customerId));
    var fixture =
        confirmFixture(
            new NominationOperation(
                prepared.castId(),
                prepared.path(),
                prepared.method(),
                body,
                prepared.resourceId(),
                prepared.originalCastId()));
    try (Connection holder = dataSource.getConnection()) {
      holder.setAutoCommit(false);
      try (var statement =
          holder.prepareStatement("select id from t_customers where id = ? for update")) {
        statement.setString(1, customerId);
        assertThat(statement.executeQuery().next()).isTrue();
      }
      assertThat(execute(fixture).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      holder.rollback();
      var response = execute(fixture);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getBody().path("customer_id").asString()).isEqualTo(customerId);
      assertThat(response.getBody().path("cast_id").asString()).isEqualTo(fixture.castId());
    }
  }

  @Test
  void successfulNominationKeepsEnrollmentLockedUntilCommit() throws Exception {
    String castId = createCast();
    String courseId = courseFixture(STORE_A, 100).serviceId();
    var held = new CompletableFuture<Integer>();
    var release = new CountDownLatch(1);
    var creation =
        CompletableFuture.supplyAsync(
            () -> {
              storeContext.setStoreId(STORE_A);
              SecurityContextHolder.getContext()
                  .setAuthentication(
                      new UsernamePasswordAuthenticationToken("yamada.jiro@kizuna.test", null));
              try {
                return new TransactionTemplate(transactions)
                    .execute(
                        tx -> {
                          OrderCreateRequest request = new OrderCreateRequest();
                          request.setCustomerSelection(
                              new CustomerSelectionRequest(
                                  CustomerSelectionRequest.Mode.NONE, null, null));
                          request.setCastId(castId);
                          request.setBusinessDate(LocalDate.now().plusDays(1));
                          request.setReceptionistId(3L);
                          request.setCourseId(courseId);
                          request.setConfirmationToken(
                              orders
                                  .previewCreate(request, "yamada.jiro@kizuna.test")
                                  .confirmationToken());
                          var order = orders.create(request, "yamada.jiro@kizuna.test");
                          held.complete(
                              jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                          try {
                            assertThat(release.await(30, TimeUnit.SECONDS)).isTrue();
                          } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(ex);
                          }
                          return order.getId();
                        });
              } finally {
                storeContext.clear();
                SecurityContextHolder.clearContext();
              }
            });
    try {
      int blocker = held.get(10, TimeUnit.SECONDS);
      assertThatThrownBy(
              () -> {
                try (Connection probe = dataSource.getConnection();
                    var statement =
                        probe.prepareStatement(
                            "select id from t_cast_enrollments where id = ? for update nowait")) {
                  statement.setString(1, castId);
                  statement.executeQuery();
                }
              })
          .as("受注保存後も在籍行をコミットまで保持すること")
          .isInstanceOf(SQLException.class)
          .extracting("SQLState")
          .isEqualTo("55P03");
      var withdrawal =
          CompletableFuture.supplyAsync(
              () ->
                  rest.postForEntity(
                      "/store/casts/" + castId + "/withdrawal",
                      new HttpEntity<>(storeHeaders(STORE_A)),
                      JsonNode.class));
      assertWaitingOn(withdrawal, blocker, "t_stores");
      release.countDown();
      String orderId = creation.get(10, TimeUnit.SECONDS);
      assertThat(withdrawal.get(10, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(
              jdbc.queryForObject(
                  "select cast_id from t_orders where id = ?", String.class, orderId))
          .isEqualTo(castId);
    } finally {
      release.countDown();
      creation.get(10, TimeUnit.SECONDS);
    }
  }

  private void assertWaitingOn(CompletableFuture<?> competing, int blocker, String table)
      throws InterruptedException {
    boolean waiting = false;
    for (int attempt = 0; attempt < 100; attempt++) {
      if (jdbc.queryForObject(
              "select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock' and ? = any(pg_blocking_pids(pid)) and query like ?",
              Integer.class,
              blocker,
              "%" + table + "%")
          > 0) {
        waiting = true;
        break;
      }
      if (competing.isDone()) break;
      TimeUnit.MILLISECONDS.sleep(50);
    }
    assertThat(waiting).as("指名操作が対象トランザクションの %s ロック解放を待つこと", table).isTrue();
  }

  private NominationOperation prepare(String operation) {
    String castId = createCast();
    LocalDate date = LocalDate.now().plusDays(1);
    Map<String, Object> body =
        new HashMap<>(
            Map.of(
                "cast_id",
                castId,
                "business_date",
                date.toString(),
                "pax",
                2,
                "receptionist_id",
                3L));
    body.put("course_id", courseFixture(STORE_A, 100).serviceId());
    body.put("customer_selection", Map.of("mode", "NONE"));
    if (operation.equals("UPDATE")) {
      String originalCastId = createCast();
      var created =
          rest.postForEntity(
              "/store/orders",
              orderFixtureRequest(
                  "{\"cast_id\":\""
                      + originalCastId
                      + "\",\"business_date\":\""
                      + date
                      + "\",\"receptionist_id\":3}",
                  storeHeaders(STORE_A)),
              JsonNode.class);
      assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      String orderId = created.getBody().path("id").asString();
      body.put("expected_version", created.getBody().path("version").asLong());
      return confirmFixture(
          new NominationOperation(
              castId, "/store/orders/" + orderId, HttpMethod.PUT, body, orderId, originalCastId));
    }
    if (operation.equals("CONFIRM")) {
      body.put("contact_imports", List.of());
      Shift shift =
          Shift.builder()
              .castId(castId)
              .workDate(date)
              .startTime(LocalTime.of(18, 0))
              .endTime(LocalTime.of(23, 0))
              .status(ShiftStatus.CONFIRMED)
              .build();
      shift.setStoreId(STORE_A);
      shifts.save(shift);
      OrderApplication application =
          OrderApplication.builder()
              .status(OrderApplicationStatus.PENDING)
              .businessDate(date)
              .pax(2)
              .castId(castId)
              .contactConsent(
                  new GuestContactConsent(
                      "1", "今回の連絡に同意", "販促に同意", true, false, OffsetDateTime.now()))
              .consentContact(ContactSnapshot.normalize("指名競合検証", "09012345678", null, null))
              .contactName("指名競合検証")
              .contactPhoneNumber("09012345678")
              .build();
      application.setStoreId(STORE_A);
      String applicationId = applications.save(application).getId();
      return confirmFixture(
          new NominationOperation(
              castId,
              "/store/order-applications/" + applicationId + "/confirmation",
              HttpMethod.POST,
              body,
              applicationId,
              null));
    }
    return confirmFixture(
        new NominationOperation(castId, "/store/orders", HttpMethod.POST, body, null, null));
  }

  private String createCast() {
    CastEnrollment enrollment = CastEnrollment.builder().build();
    enrollment.setStoreId(STORE_A);
    return saveEnrollmentFixture(enrollment, "指名競合検証", null).getId();
  }

  private NominationOperation confirmFixture(NominationOperation fixture) {
    var body = new HashMap<>(fixture.body());
    body.remove("confirmation_token");
    String previewPath =
        fixture.path().endsWith("/confirmation")
            ? fixture.path() + "-preview"
            : fixture.path() + "/preview";
    var preview =
        rest.postForEntity(
            previewPath, new HttpEntity<>(body, storeHeaders(STORE_A)), JsonNode.class);
    assertThat(preview.getStatusCode())
        .as("前提: 指名操作を試算できること %s", preview.getBody())
        .isEqualTo(HttpStatus.OK);
    body.put("confirmation_token", preview.getBody().path("confirmation_token").asString());
    return new NominationOperation(
        fixture.castId(),
        fixture.path(),
        fixture.method(),
        body,
        fixture.resourceId(),
        fixture.originalCastId());
  }

  private ResponseEntity<JsonNode> execute(NominationOperation fixture) {
    return rest.exchange(
        fixture.path(),
        fixture.method(),
        new HttpEntity<>(fixture.body(), storeHeaders(STORE_A)),
        JsonNode.class);
  }

  private record NominationOperation(
      String castId,
      String path,
      HttpMethod method,
      Map<String, Object> body,
      String resourceId,
      String originalCastId) {}
}
