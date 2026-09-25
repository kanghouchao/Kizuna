package com.kizuna.order;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class GuestContactConsentIT extends CrossStoreTestSupport {
  @Autowired JdbcTemplate jdbc;
  @Autowired RoleRepository roles;
  @Autowired PermissionRepository permissions;
  @Autowired PlatformUserRepository users;
  @Autowired PasswordEncoder passwords;
  private final ObjectMapper json = new ObjectMapper();
  private static final String PATH = "/store/order-applications";

  private HttpHeaders guestHeaders() {
    var headers = storeHeaders(STORE_A);
    headers.setBearerAuth("broken-bearer");
    headers.set("X-Forwarded-For", UUID.randomUUID().toString());
    return headers;
  }

  private String application(boolean marketing) {
    var headers = guestHeaders();
    var text =
        rest.exchange(
            PATH + "/public/contact-consent",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class);
    assertThat(text.getStatusCode()).isEqualTo(HttpStatus.OK);
    var result =
        rest.postForEntity(
            PATH + "/public",
            new HttpEntity<>(
                Map.of(
                    "business_date", LocalDate.now().plusDays(1).toString(),
                    "contact_snapshot",
                        Map.of(
                            "name", "申請者", "email", " Guest" + UUID.randomUUID() + "@EXAMPLE.COM "),
                    "contact_consent",
                        Map.of(
                            "version",
                            text.getBody().path("version").asString(),
                            "business_allowed",
                            true,
                            "marketing_allowed",
                            marketing)),
                headers),
            JsonNode.class);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(result.getBody().size()).isEqualTo(1);
    return result.getBody().path("id").asString();
  }

  private JsonNode detail(String id) {
    var result =
        rest.exchange(
            PATH + "/" + id,
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    return result.getBody();
  }

  private ObjectNode confirmation() {
    var input = json.createObjectNode();
    input.put("business_date", LocalDate.now().plusDays(1).toString());
    input.put("course_id", courseFixture(STORE_A, 100).serviceId());
    input.putObject("customer_selection").put("mode", "NONE");
    input.putArray("contact_imports");
    return input;
  }

  private String customer() {
    var result =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>(Map.of("name", "同意試験" + UUID.randomUUID()), storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return result.getBody().path("id").asString();
  }

  private String contact(String customer, String value) {
    var result =
        rest.postForEntity(
            "/store/customers/" + customer + "/contacts",
            new HttpEntity<>(Map.of("type", "EMAIL", "value", value), storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return result.getBody().path("id").asString();
  }

  private void permission(String customer, String contact, String purpose, String status) {
    var result =
        rest.exchange(
            "/store/customers/" + customer + "/contacts/" + contact + "/permissions/" + purpose,
            HttpMethod.PUT,
            new HttpEntity<>(
                Map.of("status", status, "source", "本人申告", "reason", "試験用の明示変更"),
                storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  private ObjectNode importing(String customer, String contact, boolean marketing) {
    var input = confirmation();
    input.putObject("customer_selection").put("mode", "EXISTING").put("customer_id", customer);
    var item =
        input
            .putArray("contact_imports")
            .addObject()
            .put("type", "EMAIL")
            .put("import_marketing_consent", marketing);
    if (contact != null) item.put("contact_id", contact);
    return input;
  }

  private JsonNode contacts(String customer) {
    return rest.exchange(
            "/store/customers/" + customer + "/contacts",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class)
        .getBody()
        .path("content");
  }

  private HttpHeaders operator(Set<String> codes) {
    String nonce = UUID.randomUUID().toString();
    var role =
        roles.save(
            Role.builder()
                .name("同意権限" + nonce)
                .permissionIds(
                    permissions.findByCodeIn(codes).stream()
                        .map(Permission::getId)
                        .collect(Collectors.toSet()))
                .build());
    String email = "consent-" + nonce + "@kizuna.test";
    users.save(
        PlatformUser.builder()
            .email(email)
            .password(passwords.encode(nonce))
            .displayName("同意試験")
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(Set.of(role.getId()))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .storeIds(Set.of())
            .build());
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(STORE_A));
    headers.setBearerAuth(loginWithPassword(email, nonce));
    return headers;
  }

  @Test
  @DisplayName("受注権限だけでは取り込みできず、未取り込み確定はできる。別店舗の申請は見えない")
  void permissionsAndStoreIsolation() {
    String id = application(true), customer = customer();
    var input = importing(customer, null, true);
    var onlyOrders = operator(Set.of("ORDER_MANAGE"));
    for (var action : List.of("confirmation-preview", "confirmation")) {
      var denied =
          rest.postForEntity(
              PATH + "/" + id + "/" + action, new HttpEntity<>(input, onlyOrders), JsonNode.class);
      assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    assertThat(contacts(customer).isEmpty()).isTrue();
    var onlyCustomers = operator(Set.of("CUSTOMER_MANAGE"));
    assertThat(
            rest.exchange(
                    PATH + "/" + id,
                    HttpMethod.GET,
                    new HttpEntity<>(onlyCustomers),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    var otherStore = new HttpHeaders();
    otherStore.putAll(onlyOrders);
    otherStore.set("X-Store-ID", String.valueOf(STORE_B));
    assertThat(
            rest.exchange(
                    PATH + "/" + id, HttpMethod.GET, new HttpEntity<>(otherStore), JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            rest.postForEntity(
                    PATH + "/" + id + "/confirmation-preview",
                    new HttpEntity<>(input, otherStore),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    var noImport = confirmation();
    var confirmed =
        submitPreviewed(
            PATH + "/" + id + "/confirmation",
            HttpMethod.POST,
            PATH + "/" + id + "/confirmation-preview",
            noImport.toString(),
            onlyOrders);
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  @DisplayName("同意版と必須選択を検証し、申請と異なる宛先へ販促許可を取り込めない")
  void validatesConsentAndImportTargets() {
    var body = json.createObjectNode();
    body.put("business_date", LocalDate.now().plusDays(1).toString());
    body.putObject("contact_snapshot").put("name", "申請者").put("email", "check@example.com");
    var consent =
        body.putObject("contact_consent")
            .put("version", "obsolete")
            .put("business_allowed", true)
            .put("marketing_allowed", false);
    assertThat(
            rest.postForEntity(
                    PATH + "/public", new HttpEntity<>(body, guestHeaders()), JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    consent.put("version", "1").put("business_allowed", false);
    assertThat(
            rest.postForEntity(
                    PATH + "/public", new HttpEntity<>(body, guestHeaders()), JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    consent.put("business_allowed", true).remove("marketing_allowed");
    assertThat(
            rest.postForEntity(
                    PATH + "/public", new HttpEntity<>(body, guestHeaders()), JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    String id = application(true), customer = customer();
    var input = importing(customer, contact(customer, "other@example.com"), true);
    assertThat(
            rest.postForEntity(
                    PATH + "/" + id + "/confirmation-preview",
                    new HttpEntity<>(input, storeHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    input.putObject("customer_selection").put("mode", "NONE");
    assertThat(
            rest.postForEntity(
                    PATH + "/" + id + "/confirmation-preview",
                    new HttpEntity<>(input, storeHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    input.remove("contact_imports");
    assertThat(
            rest.postForEntity(
                    PATH + "/" + id + "/confirmation-preview",
                    new HttpEntity<>(input, storeHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("同値の販促拒否を取り込みで解除せず、試算後の拒否追加は再確認を要求する")
  void preservesDenialAndRejectsStalePreview() {
    String id = application(true), customer = customer();
    String value = detail(id).path("contact_snapshot").path("email").asString();
    String denied = contact(customer, value), target = contact(customer, value);
    var input = importing(customer, target, true);
    var preview =
        rest.postForEntity(
            PATH + "/" + id + "/confirmation-preview",
            new HttpEntity<>(input, storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(preview.getBody().path("contact_imports").get(0).path("marketing_result").asString())
        .isEqualTo("ALLOWED");
    permission(customer, denied, "MARKETING", "DENIED");
    input.put("confirmation_token", preview.getBody().path("confirmation_token").asString());
    var stale =
        rest.postForEntity(
            PATH + "/" + id + "/confirmation",
            new HttpEntity<>(input, storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(detail(id).path("status").asString()).isEqualTo("PENDING");
    input.remove("confirmation_token");
    var confirmed =
        submitPreviewed(
            PATH + "/" + id + "/confirmation",
            HttpMethod.POST,
            PATH + "/" + id + "/confirmation-preview",
            input.toString(),
            storeHeaders(STORE_A));
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(detail(id).path("contact_imports").get(0).path("marketing_result").asString())
        .isEqualTo("DENIED_PRESERVED");
    for (var row : contacts(customer))
      assertThat(row.path("marketing_status").asString()).isIn("DENIED", "UNKNOWN");
  }

  @Test
  @DisplayName("販促未選択の取り込みは既存の販促状態を変えない")
  void unselectedMarketingDoesNotChangeExistingPermission() {
    String id = application(false), customer = customer();
    String target = contact(customer, detail(id).path("contact_snapshot").path("email").asString());
    permission(customer, target, "MARKETING", "ALLOWED");
    var invalid = importing(customer, target, true);
    assertThat(
            rest.postForEntity(
                    PATH + "/" + id + "/confirmation-preview",
                    new HttpEntity<>(invalid, storeHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    var input = importing(customer, target, false);
    var confirmed =
        submitPreviewed(
            PATH + "/" + id + "/confirmation",
            HttpMethod.POST,
            PATH + "/" + id + "/confirmation-preview",
            input.toString(),
            storeHeaders(STORE_A));
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(contacts(customer).get(0).path("marketing_status").asString()).isEqualTo("ALLOWED");
    assertThat(detail(id).path("contact_imports").get(0).path("marketing_result").asString())
        .isEqualTo("NOT_REQUESTED");
  }

  @Test
  @DisplayName("別顧客の業務拒否を確認前後とも適用し、拒否解除後は再判定する")
  void respectsStoreWideBusinessDenial() {
    String id = application(true), deniedCustomer = customer(), selectedCustomer = customer();
    String target =
        contact(deniedCustomer, detail(id).path("contact_snapshot").path("email").asString());
    permission(deniedCustomer, target, "BUSINESS", "DENIED");
    assertThat(detail(id).path("business_contact_permissions").get(0).path("decision").asString())
        .isEqualTo("STORE_DENIED");
    var input = confirmation();
    input
        .putObject("customer_selection")
        .put("mode", "EXISTING")
        .put("customer_id", selectedCustomer);
    var confirmed =
        submitPreviewed(
            PATH + "/" + id + "/confirmation",
            HttpMethod.POST,
            PATH + "/" + id + "/confirmation-preview",
            input.toString(),
            storeHeaders(STORE_A));
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(
            confirmed
                .getBody()
                .path("business_contact_permissions")
                .get(0)
                .path("decision")
                .asString())
        .isEqualTo("STORE_DENIED");
    permission(deniedCustomer, target, "BUSINESS", "ALLOWED");
    assertThat(detail(id).path("business_contact_permissions").get(0).path("decision").asString())
        .isEqualTo("ALLOWED");
    var order =
        rest.exchange(
            "/store/orders/" + confirmed.getBody().path("id").asString(),
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(
            order.getBody().path("business_contact_permissions").get(0).path("decision").asString())
        .isEqualTo("ALLOWED");
    assertThat(contacts(selectedCustomer).isEmpty()).isTrue();
  }

  @Test
  @DisplayName("確認時に変更した宛先へ申請の許可を移さない")
  void changedAddressHasNoConsent() {
    String id = application(true);
    var input = confirmation();
    input.putObject("contact_snapshot").put("email", "changed@example.com");
    var confirmed =
        submitPreviewed(
            PATH + "/" + id + "/confirmation",
            HttpMethod.POST,
            PATH + "/" + id + "/confirmation-preview",
            input.toString(),
            storeHeaders(STORE_A));
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(
            confirmed
                .getBody()
                .path("business_contact_permissions")
                .get(0)
                .path("status")
                .asString())
        .isEqualTo("UNKNOWN");
    assertThat(detail(id).path("contact_snapshot").path("email").asString())
        .doesNotContain("changed");
  }

  @Test
  @DisplayName("申請確定の保存失敗で連絡先・販促許可・履歴・受注をすべて巻き戻す")
  void rollsBackLateFailure() {
    String id = application(true), customer = customer();
    var input = importing(customer, null, true);
    var headers = storeHeaders(STORE_A);
    var preview =
        rest.postForEntity(
            PATH + "/" + id + "/confirmation-preview",
            new HttpEntity<>(input, headers),
            JsonNode.class);
    assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
    input.put("confirmation_token", preview.getBody().path("confirmation_token").asString());
    jdbc.execute(
        "CREATE FUNCTION fail_guest_confirmation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.id = '"
            + id
            + "' AND NEW.status = 'CONFIRMED' THEN RAISE EXCEPTION 'test failure'; END IF; RETURN NEW; END $$");
    jdbc.execute(
        "CREATE TRIGGER fail_guest_confirmation BEFORE UPDATE ON t_order_applications FOR EACH ROW EXECUTE FUNCTION fail_guest_confirmation()");
    try {
      var failed =
          rest.postForEntity(
              PATH + "/" + id + "/confirmation", new HttpEntity<>(input, headers), JsonNode.class);
      assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    } finally {
      jdbc.execute("DROP TRIGGER fail_guest_confirmation ON t_order_applications");
      jdbc.execute("DROP FUNCTION fail_guest_confirmation()");
    }
    assertThat(detail(id).path("status").asString()).isEqualTo("PENDING");
    assertThat(detail(id).path("order_id").isMissingNode() || detail(id).path("order_id").isNull())
        .isTrue();
    assertThat(detail(id).path("contact_imports").isEmpty()).isTrue();
    assertThat(contacts(customer).isEmpty()).isTrue();
    var history =
        rest.exchange(
            "/store/customers/" + customer + "/contact-history",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class);
    assertThat(history.getBody().path("content").isEmpty()).isTrue();
    var retry =
        submitPreviewed(
            PATH + "/" + id + "/confirmation",
            HttpMethod.POST,
            PATH + "/" + id + "/confirmation-preview",
            importing(customer, null, true).toString(),
            headers);
    assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(contacts(customer).size()).isEqualTo(1);
  }

  @Test
  @DisplayName("新規顧客への明示取り込みだけが販促を許可し根拠と前後状態を残す")
  void importsMarketingAtomically() {
    String id = application(true);
    var input = confirmation();
    input
        .putObject("customer_selection")
        .put("mode", "NEW")
        .putObject("new_customer")
        .put("name", "取り込み先");
    input
        .putArray("contact_imports")
        .addObject()
        .put("type", "EMAIL")
        .put("import_marketing_consent", true);
    var confirmed =
        submitPreviewed(
            PATH + "/" + id + "/confirmation",
            HttpMethod.POST,
            PATH + "/" + id + "/confirmation-preview",
            input.toString(),
            storeHeaders(STORE_A));
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String customerId = confirmed.getBody().path("customer_id").asString();
    var contacts =
        rest.exchange(
            "/store/customers/" + customerId + "/contacts",
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(contacts.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(contacts.getBody().path("content").size()).isEqualTo(1);
    var contact = contacts.getBody().path("content").get(0);
    assertThat(contact.path("business_status").asString()).isEqualTo("UNKNOWN");
    assertThat(contact.path("marketing_status").asString()).isEqualTo("ALLOWED");
    assertThat(detail(id).path("contact_imports").get(0).path("contact_id").asString())
        .isEqualTo(contact.path("id").asString());
    var history =
        rest.exchange(
                "/store/customers/" + customerId + "/contact-history",
                HttpMethod.GET,
                new HttpEntity<>(storeHeaders(STORE_A)),
                JsonNode.class)
            .getBody()
            .path("content");
    var permission = history.get(0);
    assertThat(permission.path("application_id").asString()).isEqualTo(id);
    assertThat(permission.path("before").path("marketing_status").asString()).isEqualTo("UNKNOWN");
    assertThat(permission.path("after").path("marketing_status").asString()).isEqualTo("ALLOWED");
    assertThat(permission.path("reason").asString()).contains("取得日時", "キャンペーン");
    jdbc.update("UPDATE t_order_applications SET processed_by = NULL WHERE id = ?", id);
    var retained = detail(id).path("contact_imports").get(0);
    assertThat(retained.has("recorded_by")).isTrue();
    assertThat(retained.path("recorded_by").isNull()).isTrue();
    assertThat(retained.path("recorded_at").asString()).isNotBlank();
  }

  @Test
  @DisplayName("顧客も取り込みもない確定で同意原文と今回の許可を保持する")
  void retainsEvidenceWithoutCustomer() {
    String id = application(false);
    var original = detail(id);
    assertThat(original.path("contact_consent").path("business_allowed").asBoolean()).isTrue();
    assertThat(original.path("contact_consent").path("marketing_allowed").asBoolean()).isFalse();
    assertThat(original.path("contact_consent").path("acquired_at").asString()).isNotBlank();
    assertThat(original.path("business_contact_permissions").get(0).path("decision").asString())
        .isEqualTo("ALLOWED");
    var confirmed =
        submitPreviewed(
            PATH + "/" + id + "/confirmation",
            HttpMethod.POST,
            PATH + "/" + id + "/confirmation-preview",
            confirmation().toString(),
            storeHeaders(STORE_A));
    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(confirmed.getBody().path("customer_id").isNull()).isTrue();
    assertThat(confirmed.getBody().path("contact_snapshot").path("email").asString())
        .isEqualTo(
            original
                .path("contact_snapshot")
                .path("email")
                .asString()
                .strip()
                .replace("EXAMPLE.COM", "example.com"));
    assertThat(
            confirmed
                .getBody()
                .path("business_contact_permissions")
                .get(0)
                .path("status")
                .asString())
        .isEqualTo("ALLOWED");
    var after = detail(id);
    assertThat(after.path("contact_consent")).isEqualTo(original.path("contact_consent"));
    assertThat(after.path("contact_snapshot")).isEqualTo(original.path("contact_snapshot"));
    assertThat(after.path("contact_imports").isEmpty()).isTrue();
  }
}
