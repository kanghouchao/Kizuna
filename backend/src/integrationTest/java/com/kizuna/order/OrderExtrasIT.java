package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.customer.application.CustomerMergeService;
import com.kizuna.order.result.OrderCompletionResults;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.shared.storescope.StoreContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

class OrderExtrasIT extends CrossStoreTestSupport {
  @Autowired OrderCompletionResults results;
  @Autowired StoreContext storeContext;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;
  @Autowired CustomerMergeService customerMergeService;

  @ParameterizedTest
  @ValueSource(strings = {"update", "completion", "completion-preview"})
  void customerMergeContentionReturnsConflictAndReleasesTheOrder(String operation)
      throws Exception {
    var headers = managerHeaders(STORE_A);
    String customer = linkedCustomer(headers);
    String survivor =
        post("/store/customers", Map.of("name", "統合先"), headers).getBody().path("id").asString();
    var input = input(headers);
    input.put("customer_id", customer);
    var saved =
        save(
                "/store/orders",
                input,
                post("/store/orders/preview", input, headers).getBody(),
                headers)
            .getBody();
    String path = "/store/orders/" + saved.path("id").asString();
    var change = new HashMap<String, Object>();
    change.put("expected_version", saved.path("version").asLong());
    change.put("fee_lines", List.of());
    if (operation.equals("update")) {
      change.put("cast_id", input.get("cast_id"));
      change.put("receptionist_id", saved.path("receptionist_id").asLong());
    }
    var quote =
        post(
            path + (operation.equals("update") ? "/preview" : "/completion-preview"),
            change,
            headers);
    assertThat(quote.getStatusCode()).as("%s", quote.getBody()).isEqualTo(HttpStatus.OK);
    if (!operation.equals("completion-preview"))
      change.put("confirmation_token", quote.getBody().path("confirmation_token").asString());

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      storeContext.setStoreId(STORE_A);
      try {
        new TransactionTemplate(transactions)
            .executeWithoutResult(
                tx -> {
                  jdbc.queryForObject(
                      "select id from t_customers where id = ? for update", String.class, customer);
                  var request =
                      executor.submit(
                          () ->
                              rest.exchange(
                                  operation.equals("update") ? path : path + "/" + operation,
                                  operation.equals("update") ? HttpMethod.PUT : HttpMethod.POST,
                                  new HttpEntity<>(change, headers),
                                  JsonNode.class));
                  try {
                    var response = request.get(10, TimeUnit.SECONDS);
                    assertThat(response.getStatusCode())
                        .as("%s", response.getBody())
                        .isEqualTo(HttpStatus.CONFLICT);
                    assertThat(response.getBody().path("error").asString()).contains("顧客情報が変更中");
                    assertThat(response.getBody().path("details").has("customer_lock")).isTrue();
                  } catch (Exception ex) {
                    throw new AssertionError("顧客ロックを待たずに競合を返すこと", ex);
                  }
                  customerMergeService.merge(survivor, customer, "tanaka.hanako@kizuna.test");
                });
      } finally {
        storeContext.clear();
      }
    }
    var unchanged = get(path, headers).getBody();
    assertThat(unchanged.path("customer_id").asString()).isEqualTo(survivor);
    assertThat(unchanged.path("status").asString()).isEqualTo("CONFIRMED");
    assertThat(unchanged.has("completed_at")).isFalse();
    assertThat(unchanged.path("accrued_remuneration").asInt()).isZero();
  }

  @Test
  void deletedMemberHasNoMemberCodeInAnyOrderPreview() {
    var headers = managerHeaders(STORE_A);
    String customer = linkedCustomer(headers);
    jdbc.update(
        "delete from t_members where id = (select member_id from t_customer_member_links where customer_id = ?)",
        customer);
    var input = input(headers);
    input.put("customer_id", customer);
    var preview = post("/store/orders/preview", input, headers);
    assertNoMember(preview);
    var saved = save("/store/orders", input, preview.getBody(), headers).getBody();
    String path = "/store/orders/" + saved.path("id").asString();
    var change =
        Map.of(
            "expected_version",
            saved.path("version").asLong(),
            "cast_id",
            input.get("cast_id"),
            "receptionist_id",
            saved.path("receptionist_id").asLong());
    assertNoMember(post(path + "/preview", change, headers));
    assertNoMember(
        post(
            path + "/completion-preview",
            Map.of("expected_version", saved.path("version").asLong(), "fee_lines", List.of()),
            headers));
  }

  private void assertNoMember(ResponseEntity<JsonNode> response) {
    assertThat(response.getStatusCode()).as("%s", response.getBody()).isEqualTo(HttpStatus.OK);
    var points = response.getBody().path("points");
    assertThat(points.path("member_linked").asBoolean()).isFalse();
    assertThat(points.path("redemption_eligible").asBoolean()).isFalse();
    assertThat(points.has("member_code")).isFalse();
    assertThat(points.has("point_balance")).isFalse();
  }

  @Test
  void extensionsSurchargeAndDiscountAreSavedWithIndependentTimeAndRemuneration() {
    var headers = managerHeaders(STORE_A);
    var input = input(headers);
    input.put("business_date", "2026-09-14");
    var surcharge = service(headers, "SURCHARGE", 1000, 500);
    input.put(
        "fee_lines",
        List.of(
            Map.of(
                "kind",
                "EXTENSION",
                "name",
                "延長1",
                "duration_minutes",
                30,
                "amount",
                3000,
                "remuneration",
                2000),
            Map.of(
                "kind",
                "EXTENSION",
                "name",
                "無料延長",
                "duration_minutes",
                15,
                "amount",
                0,
                "remuneration",
                0),
            Map.of("kind", "SURCHARGE", "service_id", surcharge.path("id").asString()),
            Map.of("kind", "DISCOUNT", "name", "優待", "amount", 15000)));
    var preview = post("/store/orders/preview", input, headers);
    assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(preview.getBody().path("total_fee").asInt()).isEqualTo(1000);
    assertThat(preview.getBody().path("total_remuneration").asInt()).isEqualTo(9500);
    assertThat(preview.getBody().path("total_duration_minutes").asInt()).isEqualTo(105);
    var saved = save("/store/orders", input, preview.getBody(), headers);
    assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = saved.getBody().path("id").asString();
    var detail = get("/store/orders/" + id, headers).getBody();
    assertThat(detail.path("extension_minutes").asInt()).isEqualTo(45);
    assertThat(detail.path("total_remuneration").asInt()).isEqualTo(9500);
    assertThat(detail.path("fee_lines")).hasSize(5);
    for (var line : detail.path("fee_lines"))
      assertThat(line.path("line_id").asString()).isNotBlank();

    var completion = new HashMap<String, Object>();
    completion.put("expected_version", detail.path("version").asLong());
    completion.put("fee_lines", retained(detail));
    var quote = post("/store/orders/" + id + "/completion-preview", completion, headers);
    assertThat(quote.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
            save("/store/orders/" + id + "/completion", completion, quote.getBody(), headers)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var completed = get("/store/orders/" + id, headers).getBody();
    assertThat(completed.path("total_remuneration").asInt()).isEqualTo(9500);
    assertThat(completed.path("accrued_remuneration").asInt()).isEqualTo(9500);
    try {
      storeContext.setStoreId(STORE_A);
      var result = results.find(id).orElseThrow();
      assertThat(result.accruedRemuneration()).isEqualTo(9500);
      assertThat(result.businessDate().toString()).isEqualTo("2026-09-14");
      assertThat(result.completedAt()).isNotNull();
      assertThat(result.items()).hasSize(5);
      assertThat(result.items().getFirst().revisionId()).isNotBlank();
      storeContext.setStoreId(STORE_B);
      assertThat(results.find(id)).isEmpty();
    } finally {
      storeContext.clear();
    }
    assertThat(completed.path("completed_at").asString()).isNotBlank();
    assertThat(completed.path("business_date").asString()).isEqualTo("2026-09-14");
    assertThat(get("/store/orders/" + id, managerHeaders(STORE_B)).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void surchargeRevisionRequiresReconfirmationAndRetainedLinesKeepTheirAdoption() {
    var headers = managerHeaders(STORE_A);
    var input = input(headers);
    var surcharge = service(headers, "SURCHARGE", 1000, 500);
    var selection = Map.of("kind", "SURCHARGE", "service_id", surcharge.path("id").asString());
    input.put("fee_lines", List.of(selection));
    var first = post("/store/orders/preview", input, headers).getBody();
    revise(surcharge, 2000, headers);
    assertThat(save("/store/orders", input, first, headers).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    var second = post("/store/orders/preview", input, headers).getBody();
    var saved = save("/store/orders", input, second, headers).getBody();
    String id = saved.path("id").asString();
    var before = get("/store/orders/" + id, headers).getBody();
    var surchargeLine = before.path("fee_lines").get(1);
    assertThat(surchargeLine.path("revision_number").asInt()).isEqualTo(2);
    var current = get("/store/services/" + surcharge.path("id").asString(), headers).getBody();
    revise(current, 3000, headers);
    var update = new HashMap<String, Object>();
    update.put("expected_version", before.path("version").asLong());
    update.put("cast_id", input.get("cast_id"));
    update.put("receptionist_id", before.path("receptionist_id").asLong());
    update.put("fee_lines", retained(before));
    var quote = post("/store/orders/" + id + "/preview", update, headers);
    assertThat(quote.getStatusCode()).isEqualTo(HttpStatus.OK);
    update.put("confirmation_token", quote.getBody().path("confirmation_token").asString());
    assertThat(
            rest.exchange(
                    "/store/orders/" + id,
                    HttpMethod.PUT,
                    new HttpEntity<>(update, headers),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var after = get("/store/orders/" + id, headers).getBody();
    assertThat(after.path("fee_lines").get(1)).isEqualTo(surchargeLine);
    assertThat(after.path("total_fee").asInt()).isEqualTo(14000);
    update.remove("confirmation_token");
    update.put("expected_version", after.path("version").asLong());
    update.put("fee_lines", List.of(selection));
    var reselection = post("/store/orders/" + id + "/preview", update, headers);
    assertThat(reselection.getStatusCode()).isEqualTo(HttpStatus.OK);
    update.put("confirmation_token", reselection.getBody().path("confirmation_token").asString());
    var replaced =
        rest.exchange(
            "/store/orders/" + id,
            HttpMethod.PUT,
            new HttpEntity<>(update, headers),
            JsonNode.class);
    assertThat(replaced.getStatusCode()).as("%s", replaced.getBody()).isEqualTo(HttpStatus.OK);
    var finalOrder = get("/store/orders/" + id, headers).getBody();
    assertThat(finalOrder.path("total_fee").asInt()).isEqualTo(15000);
    assertThat(finalOrder.path("fee_lines").get(1).path("line_id"))
        .isEqualTo(surchargeLine.path("line_id"));
    assertThat(finalOrder.path("fee_lines").get(1).path("revision_number").asInt()).isEqualTo(3);
  }

  @Test
  void invalidInputsAndForeignSelectionsCannotBypassTheContract() {
    var headers = managerHeaders(STORE_A);
    var input = input(headers);
    var surcharge = service(headers, "SURCHARGE", 1000, 0);
    var selection = Map.of("kind", "SURCHARGE", "service_id", surcharge.path("id").asString());
    input.put("fee_lines", List.of(selection, selection));
    assertThat(post("/store/orders/preview", input, headers).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    for (var invalid :
        List.of(
            Map.of("kind", "MANUAL_ADJUST", "name", "任意調整", "amount", 1),
            Map.of(
                "kind",
                "EXTENSION",
                "name",
                "小数",
                "duration_minutes",
                1.5,
                "amount",
                0,
                "remuneration",
                0),
            Map.of(
                "kind",
                "EXTENSION",
                "name",
                "無料",
                "duration_minutes",
                15,
                "amount",
                0,
                "remuneration",
                1),
            Map.of("kind", "DISCOUNT", "name", "過大", "amount", 12001),
            Map.of("kind", "DISCOUNT", "name", "小数円", "amount", 1.5),
            Map.of(
                "kind", "SURCHARGE", "service_id", surcharge.path("id").asString(), "amount", 1))) {
      input.put("fee_lines", List.of(invalid));
      assertThat(post("/store/orders/preview", input, headers).getStatusCode())
          .as("%s", invalid)
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    var foreign = service(managerHeaders(STORE_B), "SURCHARGE", 1000, 0);
    input.put(
        "fee_lines",
        List.of(Map.of("kind", "SURCHARGE", "service_id", foreign.path("id").asString())));
    assertThat(post("/store/orders/preview", input, headers).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get("/store/orders/surcharge-candidates", headers).getBody().toString())
        .doesNotContain(foreign.path("id").asString())
        .doesNotContain("actor_id");
    assertThat(get("/store/orders/surcharge-candidates?size=101", headers).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void completedCorrectionUsesDeletedHistoricalSurchargeAndKeepsPreviousRemuneration() {
    var headers = managerHeaders(STORE_A);
    var input = input(headers);
    var surcharge = service(headers, "SURCHARGE", 1000, 500);
    var quote = post("/store/orders/preview", input, headers).getBody();
    var saved = save("/store/orders", input, quote, headers).getBody();
    String id = saved.path("id").asString();
    var completion = new HashMap<String, Object>();
    completion.put("expected_version", saved.path("version").asLong());
    completion.put("fee_lines", List.of());
    var completionQuote =
        post("/store/orders/" + id + "/completion-preview", completion, headers).getBody();
    assertThat(
            save("/store/orders/" + id + "/completion", completion, completionQuote, headers)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var history = get("/store/orders/" + id + "/surcharge-revisions", headers).getBody();
    String revision = null;
    for (var row : history.path("content"))
      if (row.path("service_id").asString().equals(surcharge.path("id").asString()))
        revision = row.path("revision_id").asString();
    assertThat(revision).isNotNull();
    assertThat(
            rest.exchange(
                    "/store/services/" + surcharge.path("id").asString() + "?expected_version=1",
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var correction = new HashMap<String, Object>();
    correction.put(
        "expected_version", get("/store/orders/" + id, headers).getBody().path("version").asLong());
    correction.put("reason", "提供済み加算の記録漏れ");
    correction.put("fee_lines", List.of(Map.of("kind", "SURCHARGE", "revision_id", revision)));
    assertThat(
            post("/store/orders/" + id + "/correction-preview", correction, storeHeaders(STORE_A))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    var correctionQuote = post("/store/orders/" + id + "/correction-preview", correction, headers);
    assertThat(correctionQuote.getStatusCode()).isEqualTo(HttpStatus.OK);
    var corrected =
        save(
            "/store/orders/" + id + "/corrections", correction, correctionQuote.getBody(), headers);
    assertThat(corrected.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(corrected.getBody().path("previous_total_remuneration").asInt()).isEqualTo(7000);
    assertThat(corrected.getBody().path("total_remuneration").asInt()).isEqualTo(7500);
    assertThat(corrected.getBody().path("fee_lines").get(1).path("adoption_basis").asString())
        .isEqualTo("HISTORICAL_CORRECTION");
    correction.put(
        "fee_lines", List.of(Map.of("kind", "MANUAL_ADJUST", "name", "迂回", "amount", 1)));
    assertThat(
            post("/store/orders/" + id + "/correction-preview", correction, headers)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void completionKeepsPointBasisAndAcceptsSufficientBalanceChanges() {
    var headers = managerHeaders(STORE_A);
    String customer = linkedCustomer(headers);
    adjustPoints(customer, 5000, headers);
    var input = input(headers);
    input.put("customer_id", customer);
    input.put("business_date", "2026-09-14");
    var surcharge = service(headers, "SURCHARGE", 3000, 0);
    input.put(
        "fee_lines",
        List.of(
            Map.of(
                "kind",
                "EXTENSION",
                "name",
                "延長",
                "duration_minutes",
                30,
                "amount",
                3000,
                "remuneration",
                2000),
            Map.of("kind", "SURCHARGE", "service_id", surcharge.path("id").asString()),
            Map.of("kind", "DISCOUNT", "name", "割引", "amount", 2000)));
    var preview = post("/store/orders/preview", input, headers).getBody();
    assertThat(preview.path("points").path("member_linked").asBoolean()).isTrue();
    assertThat(preview.path("point_basis_amount").asInt()).isEqualTo(16000);
    var saved = save("/store/orders", input, preview, headers).getBody();
    String path = "/store/orders/" + saved.path("id").asString();
    var completion = new HashMap<String, Object>();
    completion.put("expected_version", saved.path("version").asLong());
    completion.put("fee_lines", retained(saved));
    completion.put("use_points", 3000);
    var quote = post(path + "/completion-preview", completion, headers);
    assertThat(quote.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(quote.getBody().path("point_basis_amount").asInt()).isEqualTo(16000);
    assertThat(quote.getBody().path("total_fee").asInt()).isEqualTo(13000);
    assertThat(quote.getBody().path("points").path("grant_points").asInt()).isEqualTo(160);
    adjustPoints(customer, 100, headers);
    assertThat(save(path + "/completion", completion, quote.getBody(), headers).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var completed = get(path, headers).getBody();
    assertThat(completed.path("accrued_remuneration").asInt()).isEqualTo(9000);
    assertThat(completed.path("total_fee").asInt()).isEqualTo(13000);
    assertThat(completed.path("business_date").asString()).isEqualTo("2026-09-14");
    assertThat(completed.path("completed_at").asString()).isNotBlank();
    var archive = get("/store/orders/archive?statuses=COMPLETED&business_date=2026-09-14", headers);
    assertThat(archive.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(archive.getBody().path("content").toString()).contains(saved.path("id").asString());
  }

  @Test
  void changedMembershipRejectsOrdinarySaveAndCompletionWithoutPartialResults() {
    var headers = managerHeaders(STORE_A);
    String customer = linkedCustomer(headers);
    var input = input(headers);
    input.put("customer_id", customer);
    var first = post("/store/orders/preview", input, headers).getBody();
    assertThat(
            rest.exchange(
                    "/store/customers/" + customer + "/member-link",
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(save("/store/orders", input, first, headers).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    var saved =
        save(
                "/store/orders",
                input,
                post("/store/orders/preview", input, headers).getBody(),
                headers)
            .getBody();
    String path = "/store/orders/" + saved.path("id").asString();
    var completion = new HashMap<String, Object>();
    completion.put("expected_version", saved.path("version").asLong());
    completion.put("fee_lines", retained(saved));
    var quote = post(path + "/completion-preview", completion, headers).getBody();
    assertThat(
            post(
                    "/store/customers/" + customer + "/member-link",
                    Map.of("member_code", first.path("points").path("member_code").asString()),
                    headers)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(save(path + "/completion", completion, quote, headers).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    var unchanged = get(path, headers).getBody();
    assertThat(unchanged.path("status").asString()).isEqualTo("CONFIRMED");
    assertThat(unchanged.has("completed_at")).isFalse();
    assertThat(unchanged.path("accrued_remuneration").asInt()).isZero();
    assertThat(unchanged.path("version").asLong()).isEqualTo(saved.path("version").asLong());
  }

  @Test
  void insufficientBalanceAfterConfirmationDoesNotReduceRequestedPoints() {
    var headers = managerHeaders(STORE_A);
    String customer = linkedCustomer(headers);
    adjustPoints(customer, 3000, headers);
    var input = input(headers);
    input.put("customer_id", customer);
    var saved =
        save(
                "/store/orders",
                input,
                post("/store/orders/preview", input, headers).getBody(),
                headers)
            .getBody();
    String path = "/store/orders/" + saved.path("id").asString();
    var completion = new HashMap<String, Object>();
    completion.put("expected_version", saved.path("version").asLong());
    completion.put("fee_lines", retained(saved));
    completion.put("use_points", 3000);
    var quote = post(path + "/completion-preview", completion, headers).getBody();
    adjustPoints(customer, -100, headers);
    var rejected = save(path + "/completion", completion, quote, headers);
    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(rejected.getBody().path("details").has("use_points")).isTrue();
    var unchanged = get(path, headers).getBody();
    assertThat(unchanged.path("status").asString()).isEqualTo("CONFIRMED");
    assertThat(unchanged.path("fee_lines").toString()).doesNotContain("POINT_REDEMPTION");
    assertThat(unchanged.path("accrued_remuneration").asInt()).isZero();
  }

  @Test
  void competingCompletionsCommitExactlyOneResult() throws Exception {
    var headers = managerHeaders(STORE_A);
    String customer = linkedCustomer(headers);
    adjustPoints(customer, 5000, headers);
    var input = input(headers);
    input.put("customer_id", customer);
    var saved =
        save(
                "/store/orders",
                input,
                post("/store/orders/preview", input, headers).getBody(),
                headers)
            .getBody();
    String path = "/store/orders/" + saved.path("id").asString();
    var completion = new HashMap<String, Object>();
    completion.put("expected_version", saved.path("version").asLong());
    completion.put("fee_lines", retained(saved));
    completion.put("use_points", 3000);
    var quote = post(path + "/completion-preview", completion, headers).getBody();
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Callable<HttpStatus> action =
          () -> {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            return HttpStatus.valueOf(
                save(path + "/completion", completion, quote, headers).getStatusCode().value());
          };
      var first = executor.submit(action);
      var second = executor.submit(action);
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
    }
    var completed = get(path, headers).getBody();
    assertThat(completed.path("used_points").asInt()).isEqualTo(3000);
    assertThat(completed.path("accrued_remuneration").asInt()).isEqualTo(7000);
    assertThat(completed.path("version").asLong()).isEqualTo(saved.path("version").asLong() + 1);
  }

  private String linkedCustomer(HttpHeaders headers) {
    var member =
        post(
            "/platform/members",
            Map.of(
                "email",
                "completion-" + System.nanoTime() + "@kizuna.test",
                "password",
                "password1234",
                "display_name",
                "報酬検証"),
            headers);
    assertThat(member.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String customer =
        post("/store/customers", Map.of("name", "報酬検証"), headers).getBody().path("id").asString();
    assertThat(
            post(
                    "/store/customers/" + customer + "/member-link",
                    Map.of("member_code", member.getBody().path("member_code").asString()),
                    headers)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    return customer;
  }

  private void adjustPoints(String customer, int delta, HttpHeaders headers) {
    var body = new HashMap<String, Object>();
    body.put("delta", delta);
    body.put("reason", "完了再確認の検証");
    body.put("idempotency_key", UUID.randomUUID().toString());
    if (delta > 0) body.put("expires_on", "2028-12-31");
    var response = post("/store/customers/" + customer + "/point-adjustments", body, headers);
    assertThat(response.getStatusCode()).as("%s", response.getBody()).isEqualTo(HttpStatus.OK);
  }

  private HashMap<String, Object> input(HttpHeaders headers) {
    var course = service(headers, "COURSE", 12000, 7000);
    var cast = post("/store/casts", Map.of("name", "延長担当"), headers).getBody();
    return new HashMap<>(
        Map.of(
            "business_date",
            "2027-01-20",
            "cast_id",
            cast.path("id").asString(),
            "course_id",
            course.path("id").asString()));
  }

  private JsonNode service(HttpHeaders headers, String kind, int price, int remuneration) {
    var body =
        new HashMap<String, Object>(
            Map.of(
                "kind",
                kind,
                "name",
                "検証" + System.nanoTime(),
                "price",
                price,
                "remuneration",
                remuneration));
    if (kind.equals("COURSE")) body.put("duration_minutes", 60);
    var result = post("/store/services", body, headers);
    assertThat(result.getStatusCode()).as("%s", result.getBody()).isEqualTo(HttpStatus.CREATED);
    return get("/store/services/" + result.getBody().path("id").asString(), headers).getBody();
  }

  private void revise(JsonNode service, int price, HttpHeaders headers) {
    var result =
        rest.exchange(
            "/store/services/" + service.path("id").asString(),
            HttpMethod.PUT,
            new HttpEntity<>(
                Map.of(
                    "name",
                    service.path("name").asString(),
                    "price",
                    price,
                    "remuneration",
                    500,
                    "expected_version",
                    service.path("version").asLong()),
                headers),
            JsonNode.class);
    assertThat(result.getStatusCode()).as("%s", result.getBody()).isEqualTo(HttpStatus.OK);
  }

  private List<Map<String, String>> retained(JsonNode order) {
    var lines = new ArrayList<Map<String, String>>();
    for (var line : order.path("fee_lines"))
      if (!line.path("system_owned").asBoolean()
          && !line.path("kind").asString().equals("BASE_COURSE"))
        lines.add(Map.of("line_id", line.path("line_id").asString()));
    return lines;
  }

  private ResponseEntity<JsonNode> get(String path, HttpHeaders headers) {
    return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
  }

  private ResponseEntity<JsonNode> post(String path, Object body, HttpHeaders headers) {
    return rest.postForEntity(path, new HttpEntity<>(body, headers), JsonNode.class);
  }

  private ResponseEntity<JsonNode> save(
      String path, Map<String, Object> input, JsonNode preview, HttpHeaders headers) {
    var body = new HashMap<>(input);
    body.put("confirmation_token", preview.path("confirmation_token").asString());
    return post(path, body, headers);
  }
}
