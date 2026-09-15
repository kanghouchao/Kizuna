package com.kizuna.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.cast.application.CastEnrollmentService;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.service.api.dto.ServiceUpdateRequest;
import com.kizuna.service.application.ServiceSettingsService;
import com.kizuna.service.domain.ChargeType;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

class OwnServiceConditionIT extends CrossStoreTestSupport {
  @Autowired PlatformUserRepository users;
  @Autowired PasswordEncoder passwords;
  @Autowired ServiceSettingsService settings;
  @Autowired CastEnrollmentService lifecycle;
  @Autowired StoreContext storeContext;
  @Autowired PlatformTransactionManager transactions;
  @Autowired JdbcTemplate jdbc;

  @ParameterizedTest
  @ValueSource(strings = {"REVISION", "WITHDRAWAL"})
  void concurrentRevisionOrWithdrawalRejectsConsentWithoutPartialHistory(String operation)
      throws Exception {
    var owner = owner();
    String id = create(STORE_A);
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var revision =
        CompletableFuture.runAsync(
            () -> {
              storeContext.setStoreId(STORE_A);
              try {
                new TransactionTemplate(transactions)
                    .executeWithoutResult(
                        tx -> {
                          if (operation.equals("WITHDRAWAL"))
                            lifecycle.withdraw(owner.enrollmentId(), "tanaka.hanako@kizuna.test");
                          else
                            settings.update(
                                id,
                                new ServiceUpdateRequest(
                                    "競合改定", null, ChargeType.PAID, 3000, 1500, 1L),
                                "tanaka.hanako@kizuna.test");
                          held.countDown();
                          try {
                            assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
                          } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                          }
                        });
              } finally {
                storeContext.clear();
              }
            });
    try {
      assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
      var competing =
          CompletableFuture.supplyAsync(
              () -> decide(owner.headers(), STORE_A, id, 1, 0, "ACCEPTED"));
      boolean waiting = false;
      for (int attempt = 0; attempt < 100; attempt++) {
        if (jdbc.queryForObject(
                "select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock' and query like '%t_stores%'",
                Integer.class)
            > 0) {
          waiting = true;
          break;
        }
        if (competing.isDone()) break;
        TimeUnit.MILLISECONDS.sleep(50);
      }
      assertThat(waiting).as("本人操作が設定改定のコミットを待つ").isTrue();
      release.countDown();
      revision.get(10, TimeUnit.SECONDS);
      assertThat(competing.get(10, TimeUnit.SECONDS).getStatusCode())
          .isEqualTo(operation.equals("WITHDRAWAL") ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT);
      if (!operation.equals("WITHDRAWAL"))
        assertThat(row(owner.headers(), id).path("consent_status").asString())
            .isEqualTo("NOT_ACCEPTED");
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from t_service_consents where service_id = ?",
                  Integer.class,
                  id))
          .isZero();
    } finally {
      release.countDown();
      revision.get(10, TimeUnit.SECONDS);
    }
  }

  @Test
  void invalidDecisionsAndStaleVersionsLeaveNoExtraEvents() {
    var owner = owner();
    String id = create(STORE_A);
    assertThat(decide(owner.headers(), STORE_A, id, 0, 0, "ACCEPTED").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(decide(owner.headers(), STORE_A, id, 1, -1, "ACCEPTED").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(decide(owner.headers(), STORE_A, id, 1, 0, "UNKNOWN").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(decide(owner.headers(), STORE_A, id, 1, 0, "ACCEPTED").getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(decide(owner.headers(), STORE_A, id, 1, 1, "ACCEPTED").getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(decide(owner.headers(), STORE_A, id, 1, 0, "REJECTED").getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_service_consent_events e join t_service_consents c on c.id = e.consent_id where c.service_id = ?",
                Integer.class,
                id))
        .isEqualTo(1);
    assertThat(
            rest.exchange(
                    "/platform/me/service-conditions?store_id=1&page=-1",
                    HttpMethod.GET,
                    new HttpEntity<>(owner.headers()),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void currentConditionsAndDecisionsFollowRevisionsAndEnrollmentEpisodes() {
    var owner = owner();
    String id = create(STORE_A);
    assertThat(
            rest.exchange(
                    "/store/casts/" + owner.enrollmentId() + "/publication",
                    HttpMethod.PATCH,
                    new HttpEntity<>(
                        Map.of("publication_status", "UNPUBLISHED"), storeHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(row(owner.headers(), id).path("consent_status").asString())
        .isEqualTo("NOT_ACCEPTED");
    assertThat(decide(owner.headers(), STORE_A, id, 1, 0, "ACCEPTED").getStatusCode())
        .isEqualTo(HttpStatus.OK);
    update(id, "名称変更", 1, 2000, 1500, "PAID");
    var renamed = row(owner.headers(), id);
    assertThat(renamed.path("name").asString()).isEqualTo("名称変更");
    assertThat(renamed.path("consent_status").asString()).isEqualTo("ACCEPTED");
    assertThat(renamed.path("terms_version").asInt()).isEqualTo(1);
    update(id, "価格改定", 2, 3000, 1500, "PAID");
    assertThat(row(owner.headers(), id).path("consent_status").asString())
        .isEqualTo("RECONFIRMATION_REQUIRED");
    assertThat(decide(owner.headers(), STORE_A, id, 1, 1, "ACCEPTED").getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(decide(owner.headers(), STORE_A, id, 2, 1, "REJECTED").getStatusCode())
        .isEqualTo(HttpStatus.OK);
    update(id, "無料", 3, 0, 0, "FREE");
    var rejected = row(owner.headers(), id);
    assertThat(rejected.path("consent_status").asString()).isEqualTo("REJECTED");
    assertThat(rejected.path("price").asInt()).isZero();
    assertThat(rejected.path("remuneration").asInt()).isZero();
    assertThat(decide(owner.headers(), STORE_A, id, 3, 2, "ACCEPTED").getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(row(owner.headers(), id).path("consent_status").asString()).isEqualTo("ACCEPTED");
    assertThat(
            rest.postForEntity(
                    "/store/casts/" + owner.enrollmentId() + "/withdrawal",
                    new HttpEntity<>(storeHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(decide(owner.headers(), STORE_A, id, 3, 3, "ACCEPTED").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    var next = CastEnrollment.builder().build();
    next.setStoreId(STORE_A);
    saveEnrollmentFixture(next, "再入店", owner.userId());
    assertThat(row(owner.headers(), id).path("consent_status").asString())
        .isEqualTo("NOT_ACCEPTED");
    assertThat(row(owner.headers(), id).path("consent_version").asInt()).isZero();
  }

  @Test
  void authenticatedIdentityAndStoreOwnTheDecisionAndDto() {
    var first = owner();
    var second = owner();
    String id = create(STORE_A);
    String otherStore = create(STORE_B);
    decide(first.headers(), STORE_A, id, 1, 0, "ACCEPTED");
    assertThat(row(second.headers(), id).path("consent_status").asString())
        .isEqualTo("NOT_ACCEPTED");
    assertThat(decide(first.headers(), STORE_B, otherStore, 1, 0, "ACCEPTED").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(decide(first.headers(), STORE_A, otherStore, 1, 0, "ACCEPTED").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    for (var headers : new HttpHeaders[] {managerHeaders(STORE_A), storeHeaders(STORE_A), hq()}) {
      assertThat(decide(headers, STORE_A, id, 1, 0, "ACCEPTED").getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(list(headers).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    assertThat(list(new HttpHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    var visible = row(first.headers(), id);
    assertThat(visible.has("actor_id")).isFalse();
    assertThat(visible.has("cast_id")).isFalse();
    assertThat(visible.has("enrollment_id")).isFalse();
    assertThat(visible.has("duration_minutes")).isFalse();
    assertThat(visible.path("price").asInt()).isEqualTo(2000);
    assertThat(visible.path("remuneration").asInt()).isEqualTo(1500);
  }

  @Test
  void concurrentDecisionsCannotOverwriteTheOtherScreen() throws Exception {
    var owner = owner();
    String id = create(STORE_A);
    CountDownLatch start = new CountDownLatch(1);
    var first =
        CompletableFuture.supplyAsync(
            () -> {
              await(start);
              return decide(owner.headers(), STORE_A, id, 1, 0, "ACCEPTED").getStatusCode();
            });
    var second =
        CompletableFuture.supplyAsync(
            () -> {
              await(start);
              return decide(owner.headers(), STORE_A, id, 1, 0, "REJECTED").getStatusCode();
            });
    start.countDown();
    assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
        .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_service_consent_events e join t_service_consents c on c.id = e.consent_id where c.service_id = ?",
                Integer.class,
                id))
        .isEqualTo(1);
  }

  private static void await(CountDownLatch start) {
    try {
      assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private record Owner(HttpHeaders headers, String enrollmentId, Long userId) {}

  private Owner owner() {
    String email = "consent-" + System.nanoTime() + "@kizuna.test";
    var user =
        users.save(
            PlatformUser.builder()
                .email(email)
                .password(passwords.encode(NEW_ACCOUNT_PASSWORD))
                .displayName("本人条件検証")
                .userType(UserType.CAST)
                .enabled(true)
                .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of(STORE_A))
                .build());
    var enrollment = CastEnrollment.builder().build();
    enrollment.setStoreId(STORE_A);
    String id = saveEnrollmentFixture(enrollment, "本人", user.getId()).getId();
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(loginWithPassword(email, NEW_ACCOUNT_PASSWORD));
    return new Owner(headers, id, user.getId());
  }

  private HttpHeaders hq() {
    var result = new HttpHeaders();
    result.setContentType(MediaType.APPLICATION_JSON);
    result.setBearerAuth(login("admin@kizuna.test"));
    return result;
  }

  private String create(long store) {
    var result =
        rest.postForEntity(
            "/store/services",
            new HttpEntity<>(
                Map.of(
                    "kind",
                    "SPECIAL_SERVICE",
                    "name",
                    "追加",
                    "charge_type",
                    "PAID",
                    "price",
                    2000,
                    "remuneration",
                    1500),
                managerHeaders(store)),
            JsonNode.class);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return result.getBody().path("id").asString();
  }

  private void update(
      String id, String name, long version, int price, int remuneration, String charge) {
    assertThat(
            rest.exchange(
                    "/store/services/" + id,
                    HttpMethod.PUT,
                    new HttpEntity<>(
                        Map.of(
                            "name",
                            name,
                            "expected_version",
                            version,
                            "price",
                            price,
                            "remuneration",
                            remuneration,
                            "charge_type",
                            charge),
                        managerHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  private ResponseEntity<JsonNode> decide(
      HttpHeaders headers, long store, String id, long terms, long consent, String decision) {
    return rest.exchange(
        "/platform/me/service-conditions/" + id + "/consent?store_id=" + store,
        HttpMethod.PUT,
        new HttpEntity<>(
            Map.of("terms_version", terms, "consent_version", consent, "decision", decision),
            headers),
        JsonNode.class);
  }

  private ResponseEntity<JsonNode> list(HttpHeaders headers) {
    return rest.exchange(
        "/platform/me/service-conditions?store_id=" + STORE_A + "&size=2000",
        HttpMethod.GET,
        new HttpEntity<>(headers),
        JsonNode.class);
  }

  private JsonNode row(HttpHeaders headers, String id) {
    var result = list(headers);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    for (var row : result.getBody().path("content"))
      if (row.path("id").asString().equals(id)) return row;
    throw new AssertionError("サービスが一覧にありません");
  }
}
