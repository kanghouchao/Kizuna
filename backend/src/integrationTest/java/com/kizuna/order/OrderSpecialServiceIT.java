package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.service.api.dto.OwnConsentRequest;
import com.kizuna.service.application.OwnServiceConditionService;
import com.kizuna.service.application.SpecialServiceRejectionHandler;
import com.kizuna.service.domain.ConsentDecision;
import com.kizuna.service.domain.ServiceRevisionRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shared.exception.ServiceException;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class OrderSpecialServiceIT extends CrossStoreTestSupport {
  @MockitoSpyBean SpecialServiceRejectionHandler rejectionHandler;
  @MockitoSpyBean ServiceRevisionRepository serviceRevisions;
  @MockitoSpyBean OrderRepository orders;
  @MockitoSpyBean CastEnrollmentRepository enrollments;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;
  @Autowired OwnServiceConditionService conditions;
  @Autowired StoreContext storeContext;
  @Autowired PlatformUserRepository users;
  @Autowired PasswordEncoder passwords;

  @Test
  void candidatePageKeepsEligibilityContentAndCountInOneSnapshot() {
    var owner = owner();
    var manager = managerHeaders(STORE_A);
    String first = special(manager);
    String second = special(manager);
    assertThat(decide(owner, first, 1, 0, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(decide(owner, second, 1, 0, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    String path = "/store/orders/special-service-candidates?cast_id=" + owner.cast() + "&size=1";
    var before = call(HttpMethod.GET, path, null, manager);
    assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(before.getBody().required("total_elements").asInt()).isEqualTo(2);
    var committed = new AtomicBoolean();
    doAnswer(
            invocation -> {
              var result =
                  mockingDetails(enrollments)
                      .getMockCreationSettings()
                      .getDefaultAnswer()
                      .answer(invocation);
              if (committed.compareAndSet(false, true)) {
                assertThat(decide(owner, first, 1, 1, "REJECTED").getStatusCode())
                    .isEqualTo(HttpStatus.OK);
              }
              return result;
            })
        .when(enrollments)
        .findById(owner.cast());
    assertThat(call(HttpMethod.GET, path, null, manager).getBody()).isEqualTo(before.getBody());
    assertThat(committed).isTrue();
    assertThat(
            call(HttpMethod.GET, path, null, manager).getBody().required("total_elements").asInt())
        .isEqualTo(1);
  }

  @Test
  void historicalCandidatesKeepRevisionAndDeletionStateInOneSnapshot() {
    var owner = owner();
    var manager = managerHeaders(STORE_A);
    String course = courseFixture(STORE_A, "歴史候補の同時更新", 60, 12000).serviceId();
    String service = special(manager);
    assertThat(decide(owner, service, 1, 0, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    var input = createInput(owner.cast(), course, service);
    var preview = call(HttpMethod.POST, "/store/orders/preview", input, manager);
    assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
    input.put("confirmation_token", preview.getBody().path("confirmation_token").asString());
    var created = call(HttpMethod.POST, "/store/orders", input, manager);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String path =
        "/store/orders/" + created.getBody().path("id").asString() + "/special-service-revisions";
    var before = call(HttpMethod.GET, path, null, manager);
    assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);
    var committed = new AtomicBoolean();
    doAnswer(
            invocation -> {
              var result =
                  mockingDetails(serviceRevisions)
                      .getMockCreationSettings()
                      .getDefaultAnswer()
                      .answer(invocation);
              if (committed.compareAndSet(false, true)) {
                assertThat(
                        call(
                                HttpMethod.DELETE,
                                "/store/services/" + service + "?expected_version=1",
                                null,
                                manager)
                            .getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);
              }
              return result;
            })
        .when(serviceRevisions)
        .findHistoricalSpecials(any(), any(), any(), any(Pageable.class));
    assertThat(call(HttpMethod.GET, path, null, manager).getBody()).isEqualTo(before.getBody());
    assertThat(committed).isTrue();
    assertThat(call(HttpMethod.GET, path, null, manager).getBody().toString())
        .contains("\"service_deleted\":true");
  }

  @ParameterizedTest
  @ValueSource(strings = {"CONFIRMED", "IN_SERVICE", "CANCELLED"})
  void startWithUnresolvedRejectionPreservesStateErrorsAndRollsBack(String state) {
    var owner = owner();
    var manager = managerHeaders(STORE_A);
    String course = courseFixture(STORE_A, "開始状態の検証", 60, 12000).serviceId();
    String service = special(manager);
    assertThat(decide(owner, service, 1, 0, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    var input = createInput(owner.cast(), course, service);
    var preview = call(HttpMethod.POST, "/store/orders/preview", input, manager);
    assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
    input.put("confirmation_token", preview.getBody().path("confirmation_token").asString());
    var created = call(HttpMethod.POST, "/store/orders", input, manager);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = created.getBody().path("id").asString();
    if (state.equals("IN_SERVICE")) {
      assertThat(
              call(
                      HttpMethod.POST,
                      "/store/orders/" + id + "/start",
                      Map.of(
                          "expected_version",
                          created.getBody().path("version").asLong(),
                          "reason",
                          "提供開始"),
                      manager)
                  .getStatusCode())
          .isEqualTo(HttpStatus.OK);
    }
    assertThat(decide(owner, service, 1, 1, "REJECTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    if (state.equals("CANCELLED")) {
      assertThat(
              call(
                      HttpMethod.POST,
                      "/store/orders/" + id + "/cancellation",
                      Map.of("reason", "取消"),
                      manager)
                  .getStatusCode())
          .isEqualTo(HttpStatus.NO_CONTENT);
    }
    var before = get(id, manager);
    var result =
        call(
            HttpMethod.POST,
            "/store/orders/" + id + "/start",
            Map.of("expected_version", before.path("version").asLong(), "reason", "開始の再試行"),
            manager);
    assertThat(result.getStatusCode())
        .isEqualTo(state.equals("CONFIRMED") ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST);
    assertThat(result.getBody().path("details").has("special_services"))
        .isEqualTo(state.equals("CONFIRMED"));
    assertThat(get(id, manager)).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void detailKeepsOneSnapshotWhenRejectionOrRepairCommitsDuringAssembly(boolean repairing) {
    var owner = owner();
    var manager = managerHeaders(STORE_A);
    String course = courseFixture(STORE_A, "詳細の同時更新", 60, 12000).serviceId();
    String service = special(manager);
    assertThat(decide(owner, service, 1, 0, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    var input = createInput(owner.cast(), course, service);
    var preview = call(HttpMethod.POST, "/store/orders/preview", input, manager);
    assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
    input.put("confirmation_token", preview.getBody().path("confirmation_token").asString());
    var created = call(HttpMethod.POST, "/store/orders", input, manager);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = created.getBody().path("id").asString();
    if (repairing) {
      assertThat(decide(owner, service, 1, 1, "REJECTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    var before = get(id, manager);
    var committed = new AtomicBoolean();
    doAnswer(
            invocation -> {
              var result =
                  mockingDetails(orders)
                      .getMockCreationSettings()
                      .getDefaultAnswer()
                      .answer(invocation);
              if (committed.compareAndSet(false, true)) {
                if (repairing) {
                  var edit =
                      json.createObjectNode()
                          .put("expected_version", before.path("version").asLong())
                          .put("cast_id", owner.cast())
                          .put("receptionist_id", before.path("receptionist_id").asLong());
                  edit.putArray("special_service_ids");
                  var repair =
                      call(HttpMethod.POST, "/store/orders/" + id + "/preview", edit, manager);
                  assertThat(repair.getStatusCode()).isEqualTo(HttpStatus.OK);
                  edit.put(
                      "confirmation_token", repair.getBody().path("confirmation_token").asString());
                  assertThat(
                          call(HttpMethod.PUT, "/store/orders/" + id, edit, manager)
                              .getStatusCode())
                      .isEqualTo(HttpStatus.OK);
                } else {
                  assertThat(decide(owner, service, 1, 1, "REJECTED").getStatusCode())
                      .isEqualTo(HttpStatus.OK);
                }
              }
              return result;
            })
        .when(orders)
        .findViewById(id);

    assertThat(get(id, manager)).isEqualTo(before);
    assertThat(committed).isTrue();
    var after = get(id, manager);
    assertThat(after.path("requires_attention").asBoolean()).isEqualTo(!repairing);
    assertThat(after.path("unresolved_special_service_count").asInt()).isEqualTo(repairing ? 0 : 1);
    assertThat(after.path("special_services").size()).isEqualTo(repairing ? 0 : 1);
    assertThat(after.path("total_fee").asInt()).isEqualTo(repairing ? 12000 : 14000);
    assertThat(after.path("version").asLong()).isGreaterThan(before.path("version").asLong());
  }

  @Test
  void candidateEvidenceRemainsAcceptedWhenRejectionCommitsAfterSelection() {
    var owner = owner();
    var manager = managerHeaders(STORE_A);
    String service = special(manager);
    assertThat(decide(owner, service, 1, 0, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    String acceptedEvent =
        jdbc.queryForObject(
            "select e.id from t_service_consent_events e join t_service_consents c on c.id = e.consent_id where c.enrollment_id = ? and c.service_id = ? and e.decision = 'ACCEPTED'",
            String.class,
            owner.cast(),
            service);
    doAnswer(
            invocation -> {
              var result =
                  mockingDetails(serviceRevisions)
                      .getMockCreationSettings()
                      .getDefaultAnswer()
                      .answer(invocation);
              assertThat(decide(owner, service, 1, 1, "REJECTED").getStatusCode())
                  .isEqualTo(HttpStatus.OK);
              return result;
            })
        .when(serviceRevisions)
        .findAcceptedSpecials(eq(owner.cast()), any(), any(Pageable.class));
    var candidate = candidates(owner.cast(), manager).path("content").get(0);
    assertThat(candidate.path("consent_event_id").asString()).isEqualTo(acceptedEvent);
    assertThat(candidate.path("consent_version").asLong()).isEqualTo(1);
  }

  @Test
  void invalidInitialSelectionKeepsInputErrorsEvenWithUnrelatedPreviewToken() {
    var owner = owner();
    var manager = managerHeaders(STORE_A);
    String course = courseFixture(STORE_A, "入力検証", 60, 12000).serviceId();
    String accepted = special(manager);
    String unaccepted = special(manager);
    assertThat(decide(owner, accepted, 1, 0, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    var valid = createInput(owner.cast(), course, accepted);
    var preview = call(HttpMethod.POST, "/store/orders/preview", valid, manager);
    assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
    for (String token :
        List.of("", "invalid", preview.getBody().path("confirmation_token").asString())) {
      for (String id : List.of("missing-special", unaccepted)) {
        var input = createInput(owner.cast(), course, id);
        if (!token.isEmpty()) input.put("confirmation_token", token);
        var result = call(HttpMethod.POST, "/store/orders", input, manager);
        assertThat(result.getStatusCode())
            .isEqualTo(id.equals(unaccepted) ? HttpStatus.BAD_REQUEST : HttpStatus.NOT_FOUND);
        assertThat(result.getBody().path("details").has("confirmation_token")).isFalse();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUSPENDED", "WITHDRAWN"})
  void inactiveEnrollmentInvalidatesCurrentConsentButKeepsAdoptedTerms(String status) {
    var owner = owner();
    var manager = managerHeaders(STORE_A);
    String course = courseFixture(STORE_A, "在籍失効", 60, 12000).serviceId();
    String service = special(manager);
    assertThat(decide(owner, service, 1, 0, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    var input = createInput(owner.cast(), course, service);
    var preview = call(HttpMethod.POST, "/store/orders/preview", input, manager);
    input.put("confirmation_token", preview.getBody().path("confirmation_token").asString());
    var created = call(HttpMethod.POST, "/store/orders", input, manager);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var adopted = created.getBody().path("special_services").get(0);
    jdbc.update("update t_cast_enrollments set status = ? where id = ?", status, owner.cast());
    var detail =
        get(created.getBody().path("id").asString(), manager).path("special_services").get(0);
    assertThat(detail.path("current_consent_status").asString()).isEqualTo("NOT_ACCEPTED");
    assertThat(detail.path("consent_event_id")).isEqualTo(adopted.path("consent_event_id"));
    assertThat(detail.path("price")).isEqualTo(adopted.path("price"));
    assertThat(detail.path("revision_id")).isEqualTo(adopted.path("revision_id"));
  }

  @Test
  void acceptedSnapshotSurvivesPriceRevisionAndRejectionRequiresExplicitRepair() {
    var owner = owner();
    var manager = managerHeaders(STORE_A);
    String course = courseFixture(STORE_A, "特殊サービス検証", 60, 12000).serviceId();
    String service =
        call(
                HttpMethod.POST,
                "/store/services",
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
                manager)
            .getBody()
            .path("id")
            .asString();
    assertThat(candidates(owner.cast(), manager).required("total_elements").asInt()).isZero();
    assertThat(decide(owner, service, 1, 0, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(candidates(owner.cast(), manager).toString()).contains(service);
    var input =
        json.createObjectNode()
            .put("business_date", "2027-01-20")
            .put("cast_id", owner.cast())
            .put("course_id", course);
    input.putArray("special_service_ids").add(service);
    var preview = call(HttpMethod.POST, "/store/orders/preview", input, manager);
    assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(preview.getBody().path("total_fee").asInt()).isEqualTo(14000);
    input.put("confirmation_token", preview.getBody().path("confirmation_token").asString());
    var created = call(HttpMethod.POST, "/store/orders", input, manager);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = created.getBody().path("id").asString();
    assertThat(
            created.getBody().path("special_services").get(0).path("consent_event_id").asString())
        .isNotBlank();
    assertThat(
            call(
                    HttpMethod.PUT,
                    "/store/services/" + service,
                    Map.of(
                        "name",
                        "改定",
                        "charge_type",
                        "PAID",
                        "price",
                        3000,
                        "remuneration",
                        1500,
                        "expected_version",
                        1),
                    manager)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var old = get(id, manager);
    assertThat(old.path("special_services").get(0).path("price").asInt()).isEqualTo(2000);
    assertThat(old.path("special_services").get(0).path("current_consent_status").asString())
        .isEqualTo("RECONFIRMATION_REQUIRED");
    assertThat(old.path("requires_attention").asBoolean()).isFalse();
    assertThat(candidates(owner.cast(), manager).toString()).doesNotContain(service);
    assertThat(
            call(
                    HttpMethod.POST,
                    "/store/orders/" + id + "/start",
                    Map.of("expected_version", old.path("version").asLong(), "reason", "提供開始"),
                    manager)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(decide(owner, service, 2, 1, "REJECTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    var rejected = get(id, manager);
    assertThat(rejected.path("status").asString()).isEqualTo("IN_SERVICE");
    assertThat(rejected.path("requires_attention").asBoolean()).isTrue();
    assertThat(rejected.path("total_fee").asInt()).isEqualTo(14000);
    assertThat(
            call(
                    HttpMethod.POST,
                    "/store/orders/" + id + "/completion-preview",
                    Map.of(
                        "expected_version",
                        rejected.path("version").asLong(),
                        "fee_lines",
                        List.of()),
                    manager)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    var edit =
        json.createObjectNode()
            .put("expected_version", rejected.path("version").asLong())
            .put("cast_id", owner.cast())
            .put("receptionist_id", rejected.path("receptionist_id").asLong());
    edit.putArray("special_service_ids");
    var repairedPreview = call(HttpMethod.POST, "/store/orders/" + id + "/preview", edit, manager);
    assertThat(repairedPreview.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(repairedPreview.getBody().path("total_fee").asInt()).isEqualTo(12000);
    edit.put("confirmation_token", repairedPreview.getBody().path("confirmation_token").asString());
    assertThat(call(HttpMethod.PUT, "/store/orders/" + id, edit, manager).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var repaired = get(id, manager);
    assertThat(repaired.path("requires_attention").asBoolean()).isFalse();
    var completion =
        json.createObjectNode().put("expected_version", repaired.path("version").asLong());
    completion.putArray("fee_lines");
    var completionPreview =
        call(HttpMethod.POST, "/store/orders/" + id + "/completion-preview", completion, manager);
    assertThat(completionPreview.getStatusCode()).isEqualTo(HttpStatus.OK);
    completion.put(
        "confirmation_token", completionPreview.getBody().path("confirmation_token").asString());
    assertThat(
            call(HttpMethod.POST, "/store/orders/" + id + "/completion", completion, manager)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var completed = get(id, manager);
    var correction =
        json.createObjectNode()
            .put("expected_version", completed.path("version").asLong())
            .put("reason", "提供済みの旧約定を訂正");
    correction
        .putArray("special_service_revision_ids")
        .add(old.path("special_services").get(0).path("revision_id").asString());
    correction.putArray("fee_lines");
    var correctionPreview =
        call(HttpMethod.POST, "/store/orders/" + id + "/correction-preview", correction, manager);
    assertThat(correctionPreview.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(correctionPreview.getBody().path("total_fee").asInt()).isEqualTo(14000);
    correction.put(
        "confirmation_token", correctionPreview.getBody().path("confirmation_token").asString());
    var corrected =
        call(HttpMethod.POST, "/store/orders/" + id + "/corrections", correction, manager);
    assertThat(corrected.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(
            corrected.getBody().path("special_services").get(0).path("adoption_basis").asString())
        .isEqualTo("HISTORICAL_CORRECTION");
    assertThat(decide(owner, service, 2, 2, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(decide(owner, service, 2, 3, "REJECTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(get(id, manager).path("requires_attention").asBoolean()).isFalse();
    var history =
        call(
                HttpMethod.GET,
                "/store/orders/" + id + "/special-service-events?size=1",
                null,
                manager)
            .getBody();
    assertThat(history.path("content").get(0).path("kind").asString()).isEqualTo("RESOLVED");
    assertThat(history.path("next_cursor").asString()).isNotBlank();
    assertThat(
            call(
                    HttpMethod.GET,
                    "/store/orders/" + id + "/special-service-events",
                    null,
                    managerHeaders(STORE_B))
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void invalidSelectionAndStaleAcceptanceCannotWriteAnOrder() {
    var owner = owner();
    var manager = managerHeaders(STORE_A);
    String course = courseFixture(STORE_A, "保存再検証", 60, 12000).serviceId();
    String service = special(manager);
    var input = createInput(owner.cast(), course, service);
    assertThat(call(HttpMethod.POST, "/store/orders/preview", input, manager).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(decide(owner, service, 1, 0, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    var preview = call(HttpMethod.POST, "/store/orders/preview", input, manager);
    assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
    input.put("confirmation_token", preview.getBody().path("confirmation_token").asString());
    assertThat(decide(owner, service, 1, 1, "REJECTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(call(HttpMethod.POST, "/store/orders", input, manager).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_orders where cast_id = ?", Integer.class, owner.cast()))
        .isZero();
    input.remove("confirmation_token");
    input.putArray("special_service_ids").add(service).add(service);
    assertThat(call(HttpMethod.POST, "/store/orders/preview", input, manager).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    input.putArray("special_service_ids");
    input
        .putArray("fee_lines")
        .addObject()
        .put("kind", "SPECIAL_SERVICE")
        .put("name", "直書き")
        .put("amount", 1);
    assertThat(call(HttpMethod.POST, "/store/orders/preview", input, manager).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    input
        .putArray("fee_lines")
        .addObject()
        .put("kind", "OPTION")
        .put("name", "直書き")
        .put("amount", 1);
    assertThat(call(HttpMethod.POST, "/store/orders/preview", input, manager).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            call(
                    HttpMethod.GET,
                    "/store/orders/special-service-candidates?cast_id="
                        + owner.cast()
                        + "&size=101",
                    null,
                    manager)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            call(
                    HttpMethod.GET,
                    "/store/orders/special-service-candidates?cast_id=" + owner.cast(),
                    null,
                    managerHeaders(STORE_B))
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            call(
                    HttpMethod.GET,
                    "/store/orders/special-service-candidates?cast_id=" + owner.cast(),
                    null,
                    owner.headers())
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @ParameterizedTest
  @ValueSource(strings = {"SAVE", "START", "COMPLETE"})
  void refusalSerializesWithSavingAndProgress(String operation) throws Exception {
    var owner = owner();
    var manager = managerHeaders(STORE_A);
    String service = special(manager);
    String course = courseFixture(STORE_A, "競合検証" + operation, 60, 12000).serviceId();
    assertThat(decide(owner, service, 1, 0, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    ObjectNode input = createInput(owner.cast(), course, service);
    var preview = call(HttpMethod.POST, "/store/orders/preview", input, manager);
    assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
    input.put("confirmation_token", preview.getBody().path("confirmation_token").asString());
    String path = "/store/orders";
    if (!operation.equals("SAVE")) {
      var created = call(HttpMethod.POST, path, input, manager);
      assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      String id = created.getBody().path("id").asString();
      input =
          json.createObjectNode()
              .put("expected_version", created.getBody().path("version").asLong());
      if (operation.equals("START")) {
        input.put("reason", "提供開始");
        path += "/" + id + "/start";
      } else {
        input.putArray("fee_lines");
        var completion =
            call(HttpMethod.POST, path + "/" + id + "/completion-preview", input, manager);
        assertThat(completion.getStatusCode()).isEqualTo(HttpStatus.OK);
        input.put("confirmation_token", completion.getBody().path("confirmation_token").asString());
        path += "/" + id + "/completion";
      }
    }
    var held = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var rejection =
        CompletableFuture.runAsync(
            () -> {
              storeContext.setStoreId(STORE_A);
              try {
                new TransactionTemplate(transactions)
                    .executeWithoutResult(
                        tx -> {
                          conditions.decide(
                              owner.email(),
                              service,
                              new OwnConsentRequest(1L, 1L, ConsentDecision.REJECTED));
                          held.countDown();
                          try {
                            assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
                          } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(ex);
                          }
                        });
              } finally {
                storeContext.clear();
              }
            });
    final ObjectNode body = input;
    final String endpoint = path;
    try {
      assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
      var competing =
          CompletableFuture.supplyAsync(() -> call(HttpMethod.POST, endpoint, body, manager));
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
      assertThat(waiting).as("受注操作が本人拒否のコミットを待つ").isTrue();
      release.countDown();
      rejection.get(10, TimeUnit.SECONDS);
      assertThat(competing.get(10, TimeUnit.SECONDS).getStatusCode())
          .isEqualTo(HttpStatus.CONFLICT);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from t_orders where cast_id = ? and status <> 'CONFIRMED'",
                  Integer.class,
                  owner.cast()))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from t_order_special_service_events where enrollment_id = ?",
                  Integer.class,
                  owner.cast()))
          .isEqualTo(operation.equals("SAVE") ? 0 : 1);
    } finally {
      release.countDown();
      rejection.get(10, TimeUnit.SECONDS);
    }
  }

  @Test
  void lostEnrollmentAfterPreviewIsAConfirmationConflict() {
    var owner = owner();
    var manager = managerHeaders(STORE_A);
    String service = special(manager);
    String course = courseFixture(STORE_A, "在籍競合", 60, 12000).serviceId();
    assertThat(decide(owner, service, 1, 0, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    var input = createInput(owner.cast(), course, service);
    var preview = call(HttpMethod.POST, "/store/orders/preview", input, manager);
    assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
    input.put("confirmation_token", preview.getBody().path("confirmation_token").asString());
    jdbc.update("update t_cast_enrollments set status = 'SUSPENDED' where id = ?", owner.cast());
    var saved = call(HttpMethod.POST, "/store/orders", input, manager);
    assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(saved.getBody().path("details").has("confirmation_token")).isTrue();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_orders where cast_id = ?", Integer.class, owner.cast()))
        .isZero();
  }

  @Test
  void multipleRefusalsKeepOtherUnresolvedItemsInEveryHistorySnapshot() {
    var owner = owner();
    var manager = managerHeaders(STORE_A);
    String first = special(manager);
    String second = special(manager);
    String course = courseFixture(STORE_A, "複数拒否", 60, 12000).serviceId();
    assertThat(decide(owner, first, 1, 0, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(decide(owner, second, 1, 0, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    var input = createInput(owner.cast(), course, first);
    input.withArray("special_service_ids").add(second);
    var preview = call(HttpMethod.POST, "/store/orders/preview", input, manager);
    assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
    input.put("confirmation_token", preview.getBody().path("confirmation_token").asString());
    var created = call(HttpMethod.POST, "/store/orders", input, manager);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = created.getBody().path("id").asString();
    assertThat(decide(owner, first, 1, 1, "REJECTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(decide(owner, second, 1, 1, "REJECTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    var events =
        call(HttpMethod.GET, "/store/orders/" + id + "/special-service-events", null, manager)
            .getBody()
            .path("content");
    var latest = events.get(0);
    for (var item : latest.path("before"))
      assertThat(item.path("requires_attention").asBoolean())
          .isEqualTo(item.path("service_id").asString().equals(first));
    for (var item : latest.path("after"))
      assertThat(item.path("requires_attention").asBoolean()).isTrue();
    assertThat(decide(owner, first, 1, 2, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(decide(owner, first, 1, 3, "REJECTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    var current = get(id, manager);
    assertThat(current.required("unresolved_special_service_count").asInt()).isEqualTo(2);
    var platform = new HttpHeaders();
    platform.setBearerAuth(login("admin@kizuna.test"));
    for (var entry :
        Map.of(
                "/store/orders?size=100&sort=createdAt,desc", manager,
                "/store/orders/work-queue?statuses=CONFIRMED&business_date=2027-01-20&size=100",
                    manager,
                "/platform/orders?size=100&sort=createdAt,desc", platform)
            .entrySet()) {
      var list = call(HttpMethod.GET, entry.getKey(), null, entry.getValue());
      assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(list.getBody().required("content"))
          .anySatisfy(
              row -> {
                assertThat(row.required("id").asString()).isEqualTo(id);
                assertThat(row.required("unresolved_special_service_count").asInt()).isEqualTo(2);
              });
    }
    var edit =
        json.createObjectNode()
            .put("expected_version", current.path("version").asLong())
            .put("cast_id", owner.cast())
            .put("receptionist_id", current.path("receptionist_id").asLong());
    edit.putArray("special_service_ids").add(first);
    var repair = call(HttpMethod.POST, "/store/orders/" + id + "/preview", edit, manager);
    assertThat(repair.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(repair.getBody().required("unresolved_special_service_count").asInt()).isEqualTo(1);
    edit.put("confirmation_token", repair.getBody().path("confirmation_token").asString());
    assertThat(call(HttpMethod.PUT, "/store/orders/" + id, edit, manager).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var resolved =
        call(HttpMethod.GET, "/store/orders/" + id + "/special-service-events", null, manager)
            .getBody()
            .path("content")
            .get(0);
    for (var item : resolved.path("before"))
      assertThat(item.path("requires_attention").asBoolean()).isTrue();
    assertThat(resolved.path("after").size()).isEqualTo(1);
    assertThat(resolved.path("after").get(0).path("requires_attention").asBoolean()).isTrue();
    assertThat(get(id, manager).path("unresolved_special_service_count").asInt()).isEqualTo(1);
  }

  @Test
  void failedOrderRejectionRollsBackConsentAndBothHistories() {
    var owner = owner();
    var manager = managerHeaders(STORE_A);
    String service = special(manager);
    String course = courseFixture(STORE_A, "拒否の原子性", 60, 12000).serviceId();
    assertThat(decide(owner, service, 1, 0, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    var input = createInput(owner.cast(), course, service);
    var preview = call(HttpMethod.POST, "/store/orders/preview", input, manager);
    assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
    input.put("confirmation_token", preview.getBody().path("confirmation_token").asString());
    var created = call(HttpMethod.POST, "/store/orders", input, manager);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = created.getBody().path("id").asString();
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new ServiceException("受注反映失敗の検証");
            })
        .when(
            AopTestUtils.<SpecialServiceRejectionHandler>getUltimateTargetObject(rejectionHandler))
        .applyRejection(any());
    assertThat(decide(owner, service, 1, 1, "REJECTED").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(candidates(owner.cast(), manager).toString()).contains(service);
    assertThat(get(id, manager).path("requires_attention").asBoolean()).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_service_consent_events e join t_service_consents c on c.id = e.consent_id where c.enrollment_id = ?",
                Integer.class,
                owner.cast()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_order_special_service_events where order_id = ?",
                Integer.class,
                id))
        .isZero();
  }

  @Test
  void refusalRepairPreservesExtensionSurchargeAndDiscountTerms() {
    var owner = owner();
    var manager = managerHeaders(STORE_A);
    var course = courseFixture(STORE_A, "複合明細", 60, 12000);
    String service = special(manager);
    assertThat(decide(owner, service, 1, 0, "ACCEPTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    var surcharge =
        call(
            HttpMethod.POST,
            "/store/services",
            Map.of("kind", "SURCHARGE", "name", "指名加算", "price", 1000, "remuneration", 500),
            manager);
    assertThat(surcharge.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var input = createInput(owner.cast(), course.serviceId(), service);
    var lines = input.putArray("fee_lines");
    lines
        .addObject()
        .put("kind", "EXTENSION")
        .put("name", "延長")
        .put("duration_minutes", 30)
        .put("amount", 3000)
        .put("remuneration", 2000);
    lines
        .addObject()
        .put("kind", "SURCHARGE")
        .put("service_id", surcharge.getBody().path("id").asString());
    lines.addObject().put("kind", "DISCOUNT").put("name", "割引").put("amount", 500);
    var preview = call(HttpMethod.POST, "/store/orders/preview", input, manager);
    assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(preview.getBody().path("total_fee").asInt()).isEqualTo(17500);
    assertThat(preview.getBody().path("total_duration_minutes").asInt()).isEqualTo(90);
    assertThat(preview.getBody().path("total_remuneration").asInt())
        .isEqualTo(course.remuneration() + 4000);
    input.put("confirmation_token", preview.getBody().path("confirmation_token").asString());
    var created = call(HttpMethod.POST, "/store/orders", input, manager);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = created.getBody().path("id").asString();
    assertThat(decide(owner, service, 1, 1, "REJECTED").getStatusCode()).isEqualTo(HttpStatus.OK);
    var before = get(id, manager);
    var edit =
        json.createObjectNode()
            .put("expected_version", before.path("version").asLong())
            .put("cast_id", owner.cast())
            .put("receptionist_id", before.path("receptionist_id").asLong());
    edit.putArray("special_service_ids");
    var repair = call(HttpMethod.POST, "/store/orders/" + id + "/preview", edit, manager);
    assertThat(repair.getStatusCode()).isEqualTo(HttpStatus.OK);
    edit.put("confirmation_token", repair.getBody().path("confirmation_token").asString());
    assertThat(call(HttpMethod.PUT, "/store/orders/" + id, edit, manager).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var after = get(id, manager);
    assertThat(after.path("requires_attention").asBoolean()).isFalse();
    assertThat(after.path("total_fee").asInt()).isEqualTo(15500);
    assertThat(after.path("total_duration_minutes").asInt()).isEqualTo(90);
    assertThat(after.path("total_remuneration").asInt()).isEqualTo(course.remuneration() + 2500);
    for (var line : before.path("fee_lines")) {
      if (Set.of("EXTENSION", "SURCHARGE", "DISCOUNT").contains(line.path("kind").asString())) {
        assertThat(after.path("fee_lines"))
            .anySatisfy(retained -> assertThat(retained).isEqualTo(line));
      }
    }
  }

  private String special(HttpHeaders manager) {
    var response =
        call(
            HttpMethod.POST,
            "/store/services",
            Map.of(
                "kind",
                "SPECIAL_SERVICE",
                "name",
                "競合項目" + System.nanoTime(),
                "charge_type",
                "PAID",
                "price",
                2000,
                "remuneration",
                1500),
            manager);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody().path("id").asString();
  }

  private ObjectNode createInput(String cast, String course, String service) {
    var input =
        json.createObjectNode()
            .put("business_date", "2027-01-20")
            .put("cast_id", cast)
            .put("course_id", course);
    input.putArray("special_service_ids").add(service);
    return input;
  }

  private JsonNode get(String id, HttpHeaders headers) {
    return call(HttpMethod.GET, "/store/orders/" + id, null, headers).getBody();
  }

  private JsonNode candidates(String cast, HttpHeaders headers) {
    return call(
            HttpMethod.GET,
            "/store/orders/special-service-candidates?cast_id=" + cast,
            null,
            headers)
        .getBody();
  }

  private ResponseEntity<JsonNode> decide(
      Owner owner, String id, long terms, long version, String decision) {
    return call(
        HttpMethod.PUT,
        "/platform/me/service-conditions/" + id + "/consent?store_id=" + STORE_A,
        Map.of("terms_version", terms, "consent_version", version, "decision", decision),
        owner.headers());
  }

  private ResponseEntity<JsonNode> call(
      HttpMethod method, String path, Object body, HttpHeaders headers) {
    return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
  }

  private record Owner(HttpHeaders headers, String cast, String email) {}

  private Owner owner() {
    String email = "special-" + System.nanoTime() + "@kizuna.test";
    var user =
        users.save(
            PlatformUser.builder()
                .email(email)
                .password(passwords.encode(NEW_ACCOUNT_PASSWORD))
                .displayName("本人")
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
    return new Owner(headers, id, email);
  }
}
