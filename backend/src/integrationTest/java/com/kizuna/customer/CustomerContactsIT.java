package com.kizuna.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.user.domain.Permission;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

class CustomerContactsIT extends CrossStoreTestSupport {
  @Autowired RoleRepository roles;
  @Autowired PermissionRepository permissions;
  @Autowired PlatformUserRepository users;
  @Autowired PasswordEncoder passwords;
  @Autowired JdbcTemplate jdbc;

  @Test
  void recordsPurposeSpecificPermissionWithEvidence() {
    String path =
        "/store/customers/"
            + request(HttpMethod.POST, "/store/customers", "{\"name\":\"連絡可否\"}")
                .getBody()
                .path("id")
                .asString();
    var created =
        request(
                HttpMethod.POST,
                path + "/contacts",
                "{\"type\":\"PHONE\",\"value\":\"09012345678\"}")
            .getBody();
    assertThat(created.path("business_status").asString()).isEqualTo("UNKNOWN");
    assertThat(created.path("effective_marketing_status").asString()).isEqualTo("UNKNOWN");
    String id = created.path("id").asString();
    var changed = permission(path, id, "BUSINESS", "ALLOWED");
    assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(changed.getBody().path("business_status").asString()).isEqualTo("ALLOWED");
    assertThat(changed.getBody().path("effective_business_status").asString()).isEqualTo("ALLOWED");
    assertThat(changed.getBody().path("marketing_status").asString()).isEqualTo("UNKNOWN");
    var history =
        request(HttpMethod.GET, path + "/contact-history", null).getBody().path("content");
    assertThat(history.get(0).path("action").asString()).isEqualTo("PERMISSION_CHANGE");
    assertThat(history.get(0).path("before").path("business_status").asString())
        .isEqualTo("UNKNOWN");
    assertThat(history.get(0).path("after").path("business_status").asString())
        .isEqualTo("ALLOWED");
    assertThat(history.get(0).path("source").asString()).isEqualTo("電話");
    assertThat(history.get(0).path("reason").asString()).isEqualTo("本人からの回答");
    assertThat(history.get(0).path("actor_id").asLong()).isPositive();
    assertThat(history.get(0).path("operation_id").asString()).isNotBlank();
  }

  @Test
  void duplicateRestrictionsIncludeOtherPagesAndSurviveRemoval() {
    String path =
        "/store/customers/"
            + request(HttpMethod.POST, "/store/customers", "{\"name\":\"重複制約\"}")
                .getBody()
                .path("id")
                .asString();
    String input = "{\"type\":\"PHONE\",\"value\":\"090-1234-5678\"}";
    String a = request(HttpMethod.POST, path + "/contacts", input).getBody().path("id").asString();
    String b = request(HttpMethod.POST, path + "/contacts", input).getBody().path("id").asString();
    permission(path, a, "BUSINESS", "ALLOWED");
    permission(path, b, "BUSINESS", "DENIED");
    permission(path, a, "MARKETING", "ALLOWED");
    var page = request(HttpMethod.GET, path + "/contacts?size=1", null).getBody().path("content");
    assertThat(page.get(0).path("business_status").asString()).isEqualTo("ALLOWED");
    assertThat(page.get(0).path("effective_business_status").asString()).isEqualTo("DENIED");
    assertThat(page.get(0).path("effective_marketing_status").asString()).isEqualTo("UNKNOWN");
    assertThat(request(HttpMethod.DELETE, path + "/contacts/" + b, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var remaining =
        request(HttpMethod.GET, path + "/contacts", null).getBody().path("content").get(0);
    assertThat(remaining.path("business_status").asString()).isEqualTo("DENIED");
    assertThat(remaining.path("marketing_status").asString()).isEqualTo("UNKNOWN");
    var history =
        request(HttpMethod.GET, path + "/contact-history", null).getBody().path("content");
    var inherited = history.get(1);
    assertThat(inherited.path("action").asString()).isEqualTo("RESTRICTION_INHERITANCE");
    assertThat(inherited.path("source_contact_id").asString()).isEqualTo(b);
    assertThat(inherited.path("contact_id").asString()).isEqualTo(a);
    assertThat(inherited.path("before").path("business_status").asString()).isEqualTo("ALLOWED");
    assertThat(inherited.path("after").path("business_status").asString()).isEqualTo("DENIED");
    assertThat(inherited.path("operation_id").asString())
        .isEqualTo(history.get(0).path("operation_id").asString());
    assertThat(inherited.has("source")).isFalse();
    assertThat(permission(path, a, "BUSINESS", "UNKNOWN").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            permission(path, a, "BUSINESS", "ALLOWED")
                .getBody()
                .path("effective_business_status")
                .asString())
        .isEqualTo("ALLOWED");
  }

  @ParameterizedTest
  @CsvSource({"LINE,A@example.com", "EMAIL,B@example.com"})
  void identityChangeResetsBothPurposesAndPreservesTheOldGroupRestriction(
      String type, String value) {
    String path =
        "/store/customers/"
            + request(HttpMethod.POST, "/store/customers", "{\"name\":\"宛先変更\"}")
                .getBody()
                .path("id")
                .asString();
    String input = "{\"type\":\"EMAIL\",\"value\":\"A@EXAMPLE.COM\"}";
    String a = request(HttpMethod.POST, path + "/contacts", input).getBody().path("id").asString();
    String b = request(HttpMethod.POST, path + "/contacts", input).getBody().path("id").asString();
    permission(path, a, "BUSINESS", "DENIED");
    permission(path, a, "MARKETING", "ALLOWED");
    permission(path, b, "BUSINESS", "ALLOWED");
    permission(path, b, "MARKETING", "ALLOWED");
    var unchanged =
        request(
                HttpMethod.PUT,
                path + "/contacts/" + a,
                "{\"type\":\"EMAIL\",\"value\":\" A@example.com \"}")
            .getBody();
    assertThat(unchanged.path("business_status").asString()).isEqualTo("DENIED");
    assertThat(unchanged.path("marketing_status").asString()).isEqualTo("ALLOWED");
    request(HttpMethod.PUT, path + "/contact-preferences/EMAIL", "{\"contact_id\":\"" + a + "\"}");
    var changed =
        request(
            HttpMethod.PUT,
            path + "/contacts/" + a,
            "{\"type\":\"" + type + "\",\"value\":\"" + value + "\"}");
    assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(changed.getBody().path("business_status").asString()).isEqualTo("UNKNOWN");
    assertThat(changed.getBody().path("marketing_status").asString()).isEqualTo("UNKNOWN");
    var rows = request(HttpMethod.GET, path + "/contacts", null).getBody().path("content");
    assertThat(rows.get(1).path("business_status").asString()).isEqualTo("DENIED");
    assertThat(rows.get(1).path("marketing_status").asString()).isEqualTo("ALLOWED");
    var history =
        request(HttpMethod.GET, path + "/contact-history", null).getBody().path("content");
    assertThat(history.get(0).path("action").asString()).isEqualTo("UPDATE");
    assertThat(history.get(1).path("action").asString()).isEqualTo("RESTRICTION_INHERITANCE");
    assertThat(history.get(1).path("operation_id").asString())
        .isEqualTo(history.get(0).path("operation_id").asString());
    request(HttpMethod.DELETE, path + "/contacts/" + a, null);
    request(HttpMethod.DELETE, path + "/contacts/" + b, null);
    var recreated = request(HttpMethod.POST, path + "/contacts", input).getBody();
    assertThat(recreated.path("id").asString()).isNotEqualTo(a).isNotEqualTo(b);
    assertThat(recreated.path("effective_business_status").asString()).isEqualTo("UNKNOWN");
    assertThat(recreated.path("effective_marketing_status").asString()).isEqualTo("UNKNOWN");
    assertThat(request(HttpMethod.DELETE, path, null).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void permissionChangesRequireEvidenceManagementAuthorityAndStoreOwnership() {
    String path =
        "/store/customers/"
            + request(HttpMethod.POST, "/store/customers", "{\"name\":\"可否権限\"}")
                .getBody()
                .path("id")
                .asString();
    String id =
        request(HttpMethod.POST, path + "/contacts", "{\"type\":\"LINE\",\"value\":\"contact\"}")
            .getBody()
            .path("id")
            .asString();
    String endpoint = path + "/contacts/" + id + "/permissions/BUSINESS";
    for (String invalid :
        List.of(
            "{}",
            "{\"status\":\"ALLOWED\"}",
            "{\"status\":\"ALLOWED\",\"source\":\" \" ,\"reason\":\"根拠\"}",
            "{\"status\":\"ALLOWED\",\"source\":\"電話\",\"reason\":\" \"}")) {
      assertThat(request(HttpMethod.PUT, endpoint, invalid).getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    var input = Map.of("status", "ALLOWED", "source", " 電話 ", "reason", " 本人回答 ");
    assertThat(
            rest.exchange(
                    endpoint,
                    HttpMethod.PUT,
                    new HttpEntity<>(input, managerHeaders(STORE_B)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    for (var granted :
        List.of(
            Set.of("CUSTOMER_MANAGE"),
            Set.of("CUSTOMER_MERGE"),
            Set.of("CUSTOMER_MANAGE", "CUSTOMER_MERGE"),
            Set.of("ORDER_MANAGE"))) {
      var response =
          rest.exchange(
              endpoint,
              HttpMethod.PUT,
              new HttpEntity<>(input, customHeaders(granted)),
              JsonNode.class);
      assertThat(response.getStatusCode())
          .isEqualTo(granted.contains("CUSTOMER_MANAGE") ? HttpStatus.OK : HttpStatus.FORBIDDEN);
    }
    var history =
        request(HttpMethod.GET, path + "/contact-history", null).getBody().path("content");
    assertThat(history.size()).isEqualTo(3);
    assertThat(history.get(0).path("source").asString()).isEqualTo("電話");
    assertThat(history.get(0).path("reason").asString()).isEqualTo("本人回答");
    request(HttpMethod.DELETE, path + "/contacts/" + id, null);
    assertThat(
            request(
                    HttpMethod.PUT,
                    endpoint,
                    "{\"status\":\"ALLOWED\",\"source\":\"電話\",\"reason\":\"本人回答\"}")
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(request(HttpMethod.DELETE, path, null).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void failureWhileWritingInheritanceRollsBackContactAndAllHistory() {
    String path =
        "/store/customers/"
            + request(HttpMethod.POST, "/store/customers", "{\"name\":\"継承巻戻し\"}")
                .getBody()
                .path("id")
                .asString();
    String input = "{\"type\":\"LINE\",\"value\":\"rollback\"}";
    String a = request(HttpMethod.POST, path + "/contacts", input).getBody().path("id").asString();
    String b = request(HttpMethod.POST, path + "/contacts", input).getBody().path("id").asString();
    permission(path, a, "BUSINESS", "DENIED");
    permission(path, b, "BUSINESS", "ALLOWED");
    var beforeRows = request(HttpMethod.GET, path + "/contacts", null).getBody();
    var beforeHistory = request(HttpMethod.GET, path + "/contact-history", null).getBody();
    jdbc.execute(
        "CREATE FUNCTION test_contact_history_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test failure'; END; $$");
    try {
      jdbc.execute(
          "CREATE TRIGGER test_contact_history_failure BEFORE INSERT ON t_customer_contact_history FOR EACH ROW WHEN (NEW.source_contact_id = '"
              + a
              + "') EXECUTE FUNCTION test_contact_history_failure()");
      assertThat(request(HttpMethod.DELETE, path + "/contacts/" + a, null).getStatusCode())
          .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(request(HttpMethod.GET, path + "/contacts", null).getBody()).isEqualTo(beforeRows);
      assertThat(request(HttpMethod.GET, path + "/contact-history", null).getBody())
          .isEqualTo(beforeHistory);
      assertThat(
              request(
                      HttpMethod.PUT,
                      path + "/contacts/" + a,
                      "{\"type\":\"LINE\",\"value\":\"new-value\"}")
                  .getStatusCode())
          .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(request(HttpMethod.GET, path + "/contacts", null).getBody()).isEqualTo(beforeRows);
      assertThat(request(HttpMethod.GET, path + "/contact-history", null).getBody())
          .isEqualTo(beforeHistory);
    } finally {
      jdbc.execute(
          "DROP TRIGGER IF EXISTS test_contact_history_failure ON t_customer_contact_history");
      jdbc.execute("DROP FUNCTION test_contact_history_failure()");
    }
  }

  @Test
  void concurrentRemovalsCannotLoseTheRestriction() throws Exception {
    String path =
        "/store/customers/"
            + request(HttpMethod.POST, "/store/customers", "{\"name\":\"並行継承\"}")
                .getBody()
                .path("id")
                .asString();
    String input = "{\"type\":\"LINE\",\"value\":\"concurrent\"}";
    String a = request(HttpMethod.POST, path + "/contacts", input).getBody().path("id").asString();
    String b = request(HttpMethod.POST, path + "/contacts", input).getBody().path("id").asString();
    String c = request(HttpMethod.POST, path + "/contacts", input).getBody().path("id").asString();
    permission(path, a, "BUSINESS", "DENIED");
    permission(path, b, "BUSINESS", "ALLOWED");
    permission(path, c, "BUSINESS", "ALLOWED");
    var ready = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                ready.await();
                return request(HttpMethod.DELETE, path + "/contacts/" + a, null);
              });
      var second =
          pool.submit(
              () -> {
                ready.await();
                return request(HttpMethod.DELETE, path + "/contacts/" + b, null);
              });
      ready.countDown();
      assertThat(first.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
      assertThat(second.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
    var rows = request(HttpMethod.GET, path + "/contacts", null).getBody().path("content");
    assertThat(rows.size()).isEqualTo(1);
    assertThat(rows.get(0).path("id").asString()).isEqualTo(c);
    assertThat(rows.get(0).path("business_status").asString()).isEqualTo("DENIED");
    assertThat(rows.get(0).path("effective_business_status").asString()).isEqualTo("DENIED");
    assertThat(
            request(HttpMethod.GET, path + "/contact-history", null)
                .getBody()
                .path("content")
                .size())
        .isEqualTo(11);
  }

  private ResponseEntity<JsonNode> permission(
      String path, String id, String purpose, String status) {
    return request(
        HttpMethod.PUT,
        path + "/contacts/" + id + "/permissions/" + purpose,
        "{\"status\":\"" + status + "\",\"source\":\"電話\",\"reason\":\"本人からの回答\"}");
  }

  @Test
  void searchesDomesticPhoneFragmentsWithoutChangingNamesOrLineIds() {
    String customerId =
        request(HttpMethod.POST, "/store/customers", "{\"name\":\"電話検索検証\"}")
            .getBody()
            .path("id")
            .asString();
    String path = "/store/customers/" + customerId;
    String contactId =
        request(
                HttpMethod.POST,
                path + "/contacts",
                "{\"type\":\"PHONE\",\"value\":\"090-2345-6789\"}")
            .getBody()
            .path("id")
            .asString();
    String namedId =
        request(HttpMethod.POST, "/store/customers", "{\"name\":\"090\"}")
            .getBody()
            .path("id")
            .asString();
    for (String endpoint : List.of("/store/customers", "/store/orders/customer-candidates")) {
      for (String query :
          List.of("090", "090-234", "(090)2345", "2345-678", "09023456789", "010819023456789")) {
        var response = request(HttpMethod.GET, endpoint + "?size=100&search=" + query, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("content"))
            .anySatisfy(row -> assertThat(row.path("id").asString()).isEqualTo(customerId));
      }
      assertThat(
              request(HttpMethod.GET, endpoint + "?size=100&search=090", null)
                  .getBody()
                  .path("content"))
          .anySatisfy(row -> assertThat(row.path("id").asString()).isEqualTo(namedId));
    }
    request(HttpMethod.DELETE, path + "/contacts/" + contactId, null);
    for (String endpoint : List.of("/store/customers", "/store/orders/customer-candidates")) {
      assertThat(
              request(HttpMethod.GET, endpoint + "?size=100&search=090", null)
                  .getBody()
                  .path("content"))
          .noneSatisfy(row -> assertThat(row.path("id").asString()).isEqualTo(customerId));
    }
    request(HttpMethod.POST, path + "/contacts", "{\"type\":\"LINE\",\"value\":\"090Line\"}");
    for (String endpoint : List.of("/store/customers", "/store/orders/customer-candidates")) {
      assertThat(
              request(HttpMethod.GET, endpoint + "?size=100&search=090", null)
                  .getBody()
                  .path("content"))
          .anySatisfy(row -> assertThat(row.path("id").asString()).isEqualTo(customerId));
    }
  }

  @Test
  void recordsNormalizedContactAndRetainsHistoryAfterDeletion() {
    var customer = request(HttpMethod.POST, "/store/customers", "{\"name\":\"連絡先検証\"}");
    assertThat(customer.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String path = "/store/customers/" + customer.getBody().path("id").asString();
    var created =
        request(
            HttpMethod.POST,
            path + "/contacts",
            "{\"type\":\"PHONE\",\"value\":\"090-1234-5678\"}");
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().path("value").asString()).isEqualTo("+819012345678");
    String contactId = created.getBody().path("id").asString();
    assertThat(request(HttpMethod.DELETE, path + "/contacts/" + contactId, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(request(HttpMethod.GET, path + "/contacts", null).getBody().path("content").size())
        .isZero();
    var history =
        request(HttpMethod.GET, path + "/contact-history", null).getBody().path("content");
    assertThat(history.size()).isEqualTo(2);
    assertThat(history.get(0).path("action").asString()).isEqualTo("DELETE");
    assertThat(history.get(0).path("before").path("value").asString()).isEqualTo("+819012345678");
    assertThat(history.get(0).path("after").path("deleted").asBoolean()).isTrue();
    assertThat(history.get(0).path("actor_id").asLong()).isPositive();
    assertThat(request(HttpMethod.DELETE, path, null).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            request(HttpMethod.GET, path + "/contact-history", null)
                .getBody()
                .path("content")
                .size())
        .isEqualTo(2);
  }

  @Test
  void preferencesAndMergePreserveIdsAndHistory() {
    String a =
        request(HttpMethod.POST, "/store/customers", "{\"name\":\"存続\"}")
            .getBody()
            .path("id")
            .asString();
    String b =
        request(HttpMethod.POST, "/store/customers", "{\"name\":\"移動\"}")
            .getBody()
            .path("id")
            .asString();
    String pathA = "/store/customers/" + a;
    String pathB = "/store/customers/" + b;
    String body = "{\"type\":\"EMAIL\",\"value\":\" A+tag@EXAMPLE.COM \"}";
    String first =
        request(HttpMethod.POST, pathA + "/contacts", body).getBody().path("id").asString();
    String second =
        request(HttpMethod.POST, pathB + "/contacts", body).getBody().path("id").asString();
    assertThat(
            request(
                    HttpMethod.PUT,
                    pathA + "/contact-preferences/EMAIL",
                    "{\"contact_id\":\"" + first + "\"}")
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    request(
        HttpMethod.PUT,
        pathB + "/contact-preferences/EMAIL",
        "{\"contact_id\":\"" + second + "\"}");
    permission(pathA, first, "BUSINESS", "ALLOWED");
    permission(pathB, second, "BUSINESS", "DENIED");

    assertThat(request(HttpMethod.PUT, pathB + "/contact-preferences/EMAIL", "{}").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            rest.postForEntity(
                    pathA + "/merges",
                    mergeFixtureRequest(a, b, managerHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    String preference = "{\"phone\":null,\"email\":\"" + first + "\",\"line\":null}";
    var preview =
        request(
            HttpMethod.POST,
            pathA + "/merge-preview",
            "{\"merged_customer_id\":\"" + b + "\",\"preferred_contacts\":" + preference + "}");
    assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
    String merge =
        "{\"merged_customer_id\":\""
            + b
            + "\",\"preferred_contacts\":"
            + preference
            + ",\"preview_token\":\""
            + preview.getBody().path("preview_token").asString()
            + "\",\"warnings_acknowledged\":true,\"operation_reason\":\"本人確認済み\"}";
    assertThat(request(HttpMethod.POST, pathA + "/merges", merge).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var contacts = request(HttpMethod.GET, pathA + "/contacts", null).getBody().path("content");
    assertThat(contacts.size()).isEqualTo(2);
    assertThat(contacts.get(0).path("business_status").asString()).isEqualTo("ALLOWED");
    assertThat(contacts.get(0).path("effective_business_status").asString()).isEqualTo("DENIED");
    assertThat(contacts.get(1).path("id").asString()).isEqualTo(second);
    assertThat(contacts.get(1).path("origin_customer_id").asString()).isEqualTo(b);
    assertThat(contacts.get(1).path("value").asString()).isEqualTo("A+tag@example.com");
    assertThat(contacts.get(0).path("preferred").asBoolean()).isTrue();
    assertThat(contacts.get(1).path("preferred").asBoolean()).isFalse();
    assertThat(request(HttpMethod.DELETE, pathA + "/contacts/" + second, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var remaining = request(HttpMethod.GET, pathA + "/contacts", null).getBody().path("content");
    assertThat(remaining.size()).isEqualTo(1);
    assertThat(remaining.get(0).path("business_status").asString()).isEqualTo("DENIED");
  }

  @Test
  void normalizationPaginationAndAtomicCreation() {
    String path =
        "/store/customers/"
            + request(HttpMethod.POST, "/store/customers", "{\"name\":\"正規化\"}")
                .getBody()
                .path("id")
                .asString();
    assertThat(
            request(
                    HttpMethod.POST,
                    path + "/contacts",
                    "{\"type\":\"PHONE\",\"value\":\"+12025550123\"}")
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            request(
                    HttpMethod.POST,
                    path + "/contacts",
                    "{\"type\":\"EMAIL\",\"value\":\"invalid\"}")
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    var line =
        request(
                HttpMethod.POST,
                path + "/contacts",
                "{\"type\":\"LINE\",\"value\":\" Mixed.Case \"}")
            .getBody();
    assertThat(line.path("value").asString()).isEqualTo("Mixed.Case");
    assertThat(
            request(HttpMethod.GET, "/store/customers?search=Mixed.Case", null)
                .getBody()
                .path("content"))
        .isNotEmpty();
    var phone =
        request(
                HttpMethod.POST,
                path + "/contacts",
                "{\"type\":\"PHONE\",\"value\":\"09012345678\"}")
            .getBody();
    String phoneId = phone.path("id").asString();
    var page = request(HttpMethod.GET, path + "/contacts?size=1", null).getBody();
    assertThat(page.path("content").size()).isEqualTo(1);
    var next =
        request(
                HttpMethod.GET,
                path + "/contacts?size=1&cursor=" + page.path("next_cursor").asString(),
                null)
            .getBody();
    assertThat(next.path("content").get(0).path("id").asString()).isEqualTo(phoneId);
    assertThat(next.has("next_cursor")).isFalse();
    assertThat(request(HttpMethod.GET, path + "/contact-history?cursor=bad", null).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    var history = request(HttpMethod.GET, path + "/contact-history?size=1", null).getBody();
    var older =
        request(
                HttpMethod.GET,
                path + "/contact-history?size=1&cursor=" + history.path("next_cursor").asString(),
                null)
            .getBody();
    assertThat(older.path("content").get(0).path("contact_id").asString())
        .isEqualTo(line.path("id").asString());
    var foreign =
        rest.exchange(
            path + "/contacts",
            HttpMethod.GET,
            new HttpEntity<>(managerHeaders(STORE_B)),
            JsonNode.class);
    assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            rest.exchange(
                    path + "/contact-history",
                    HttpMethod.GET,
                    new HttpEntity<>(managerHeaders(STORE_B)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    request(
        HttpMethod.PUT,
        path + "/contact-preferences/PHONE",
        "{\"contact_id\":\"" + phoneId + "\"}");
    request(
        HttpMethod.PUT,
        path + "/contact-preferences/LINE",
        "{\"contact_id\":\"" + line.path("id").asString() + "\"}");
    var conflict =
        request(
            HttpMethod.PUT,
            path + "/contacts/" + phoneId,
            "{\"type\":\"LINE\",\"value\":\"changed\"}");
    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            request(HttpMethod.GET, path + "/contacts", null)
                .getBody()
                .path("content")
                .get(1)
                .path("value")
                .asString())
        .isEqualTo("+819012345678");
    assertThat(
            request(HttpMethod.GET, path + "/contact-history", null)
                .getBody()
                .path("content")
                .size())
        .isEqualTo(4);
    String name = "原子性" + System.nanoTime();
    var invalid =
        request(
            HttpMethod.POST,
            "/store/customers",
            "{\"name\":\""
                + name
                + "\",\"contacts\":[{\"type\":\"LINE\",\"value\":\"valid\"},{\"type\":\"PHONE\",\"value\":\"bad\"}]}");
    assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            request(HttpMethod.GET, "/store/customers?search=" + name, null)
                .getBody()
                .path("total_elements")
                .asInt())
        .isZero();
  }

  @Test
  void switchesPreferenceAndChangesValueWithoutReusingDeletedRows() {
    String path =
        "/store/customers/"
            + request(
                    HttpMethod.POST,
                    "/store/customers",
                    "{\"name\":\"切替\",\"contacts\":[{\"type\":\"LINE\",\"value\":\"same\"},{\"type\":\"LINE\",\"value\":\"same\"}]}")
                .getBody()
                .path("id")
                .asString();
    var rows = request(HttpMethod.GET, path + "/contacts", null).getBody().path("content");
    String a = rows.get(0).path("id").asString(), b = rows.get(1).path("id").asString();
    request(HttpMethod.PUT, path + "/contact-preferences/LINE", "{\"contact_id\":\"" + a + "\"}");
    request(HttpMethod.PUT, path + "/contact-preferences/LINE", "{\"contact_id\":\"" + b + "\"}");
    var preferred = request(HttpMethod.GET, path, null).getBody().path("preferred_contacts");
    assertThat(preferred.size()).isEqualTo(1);
    assertThat(preferred.get(0).path("id").asString()).isEqualTo(b);
    assertThat(
            request(
                    HttpMethod.PUT,
                    path + "/contacts/" + b,
                    "{\"type\":\"EMAIL\",\"value\":\"Case+tag@EXAMPLE.COM\"}")
                .getBody()
                .path("value")
                .asString())
        .isEqualTo("Case+tag@example.com");
    request(HttpMethod.DELETE, path + "/contacts/" + b, null);
    assertThat(request(HttpMethod.GET, path, null).getBody().path("preferred_contacts").size())
        .isZero();
    assertThat(
            request(
                    HttpMethod.PUT,
                    path + "/contacts/" + b,
                    "{\"type\":\"LINE\",\"value\":\"restored\"}")
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void concurrentCustomerDeletionNeverLosesSuccessfulContactHistory() throws Exception {
    String path =
        "/store/customers/"
            + request(HttpMethod.POST, "/store/customers", "{\"name\":\"並行削除\"}")
                .getBody()
                .path("id")
                .asString();
    var ready = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var adding =
          pool.submit(
              () -> {
                ready.await();
                return request(
                    HttpMethod.POST,
                    path + "/contacts",
                    "{\"type\":\"LINE\",\"value\":\"retained\"}");
              });
      var deleting =
          pool.submit(
              () -> {
                ready.await();
                return request(HttpMethod.DELETE, path, null);
              });
      ready.countDown();
      var added = adding.get(20, TimeUnit.SECONDS);
      var deleted = deleting.get(20, TimeUnit.SECONDS);
      if (added.getStatusCode() == HttpStatus.CREATED) {
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(
                request(HttpMethod.GET, path + "/contact-history", null)
                    .getBody()
                    .path("content")
                    .size())
            .isEqualTo(1);
      } else {
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
      }
    }
  }

  @Test
  void separatesContactAndMergePermissions() {
    String path =
        "/store/customers/"
            + request(HttpMethod.POST, "/store/customers", "{\"name\":\"権限\"}")
                .getBody()
                .path("id")
                .asString();
    for (var granted :
        List.of(
            Set.of("CUSTOMER_MANAGE"),
            Set.of("CUSTOMER_MERGE"),
            Set.of("CUSTOMER_MANAGE", "CUSTOMER_MERGE"),
            Set.of("ORDER_MANAGE"))) {
      var headers = customHeaders(granted);
      var contacts =
          rest.exchange(
              path + "/contacts", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
      var history =
          rest.exchange(
              path + "/contact-history", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
      var created =
          rest.postForEntity(
              path + "/contacts",
              new HttpEntity<>(Map.of("type", "LINE", "value", "permission"), headers),
              JsonNode.class);
      assertThat(contacts.getStatusCode())
          .isEqualTo(granted.contains("CUSTOMER_MANAGE") ? HttpStatus.OK : HttpStatus.FORBIDDEN);
      assertThat(history.getStatusCode())
          .isEqualTo(granted.contains("CUSTOMER_MANAGE") ? HttpStatus.OK : HttpStatus.FORBIDDEN);
      assertThat(created.getStatusCode())
          .isEqualTo(
              granted.contains("CUSTOMER_MANAGE") ? HttpStatus.CREATED : HttpStatus.FORBIDDEN);
      for (String endpoint :
          List.of(
              "/store/customers/duplicates",
              "/store/customers/duplicates/customers?type=LINE&value=x")) {
        assertThat(
                rest.exchange(endpoint, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class)
                    .getStatusCode())
            .isEqualTo(
                granted.containsAll(Set.of("CUSTOMER_MANAGE", "CUSTOMER_MERGE"))
                    ? HttpStatus.OK
                    : HttpStatus.FORBIDDEN);
      }
      var preview =
          rest.postForEntity(
              path + "/merge-preview",
              new HttpEntity<>(Map.of("merged_customer_id", "missing"), headers),
              JsonNode.class);
      assertThat(preview.getStatusCode())
          .isEqualTo(
              granted.containsAll(Set.of("CUSTOMER_MANAGE", "CUSTOMER_MERGE"))
                  ? HttpStatus.NOT_FOUND
                  : HttpStatus.FORBIDDEN);
      var audit =
          rest.exchange(
              path + "/merges/missing", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
      assertThat(audit.getStatusCode())
          .isEqualTo(
              granted.contains("CUSTOMER_MERGE") ? HttpStatus.NOT_FOUND : HttpStatus.FORBIDDEN);
      var merge =
          rest.postForEntity(
              path + "/merges",
              mergeFixtureRequest(path.substring("/store/customers/".length()), "missing", headers),
              JsonNode.class);
      assertThat(merge.getStatusCode())
          .isEqualTo(
              granted.containsAll(Set.of("CUSTOMER_MANAGE", "CUSTOMER_MERGE"))
                  ? HttpStatus.NOT_FOUND
                  : HttpStatus.FORBIDDEN);
    }
  }

  private HttpHeaders customHeaders(Set<String> codes) {
    String nonce = UUID.randomUUID().toString();
    var role =
        roles.save(
            Role.builder()
                .name("連絡先検証" + nonce)
                .permissionIds(
                    permissions.findByCodeIn(codes).stream()
                        .map(Permission::getId)
                        .collect(Collectors.toSet()))
                .build());
    String email = "contacts-" + nonce + "@kizuna.test";
    String password = UUID.randomUUID().toString();
    users.save(
        PlatformUser.builder()
            .email(email)
            .password(passwords.encode(password))
            .displayName("連絡先担当")
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(Set.of(role.getId()))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .storeIds(Set.of())
            .build());
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    var login =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(Map.of("email", email, "password", password), headers),
            JsonNode.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    headers.setBearerAuth(login.getBody().path("token").asString());
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(STORE_A));
    return headers;
  }

  private ResponseEntity<JsonNode> request(HttpMethod method, String path, String body) {
    return rest.exchange(
        path, method, new HttpEntity<>(body, managerHeaders(STORE_A)), JsonNode.class);
  }
}
