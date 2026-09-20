package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class OrderContactSnapshotIT extends CrossStoreTestSupport {
  @Autowired ObjectMapper json;
  @Autowired CustomerRepository customers;
  @Autowired RoleRepository roles;
  @Autowired PermissionRepository permissions;
  @Autowired PlatformUserRepository users;
  @Autowired PasswordEncoder passwords;

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void phoneNeverSelectsOrCreatesCustomer(int matches) {
    String name = "連絡先のみ" + UUID.randomUUID();
    String phone = "0809876543" + matches;
    for (int i = 0; i < matches; i++) {
      var customer = Customer.builder().name("既存" + UUID.randomUUID()).build();
      customer.setStoreId(STORE_A);
      customers.save(customer);
      var contact =
          rest.postForEntity(
              "/store/customers/" + customer.getId() + "/contacts",
              new HttpEntity<>(Map.of("type", "PHONE", "value", phone), managerHeaders(STORE_A)),
              JsonNode.class);
      assertThat(contact.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
    ObjectNode input = input("NONE");
    input.set(
        "contact_snapshot",
        json.valueToTree(
            Map.of(
                "name",
                name,
                "phone_number",
                phone,
                "email",
                "Local@EXAMPLE.COM",
                "line_id",
                " temporary-line ")));
    JsonNode created = create(input);
    assertThat(created.path("customer_id").isNull()).isTrue();
    JsonNode snapshot = detail(created).path("contact_snapshot");
    assertThat(snapshot.path("phone_number").asString()).isEqualTo("+81" + phone.substring(1));
    assertThat(snapshot.path("email").asString()).isEqualTo("Local@example.com");
    assertThat(snapshot.path("line_id").asString()).isEqualTo("temporary-line");
    var candidates = get("/store/orders/customer-candidates?search=" + name);
    assertThat(candidates.path("content").size()).isZero();
  }

  @Test
  void linkedSnapshotSurvivesLedgerEditDeletionRefusalAndMerge() {
    String source = customer("元の顧客");
    String survivor = customer("存続顧客");
    ObjectNode input = input("EXISTING");
    ((ObjectNode) input.get("customer_selection")).put("customer_id", source);
    input.set(
        "contact_snapshot",
        json.valueToTree(
            Map.of(
                "name",
                "今回の名乗り",
                "phone_number",
                "090-1234-5678",
                "email",
                "once@example.com",
                "line_id",
                "once-line")));
    JsonNode order = create(input);
    JsonNode original = detail(order).path("contact_snapshot");
    var edited =
        rest.exchange(
            "/store/customers/" + source,
            HttpMethod.PUT,
            new HttpEntity<>(Map.of("name", "台帳変更"), managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(detail(order).path("contact_snapshot")).isEqualTo(original);
    assertThat(
            rest.exchange(
                    "/store/customers/" + source,
                    HttpMethod.DELETE,
                    new HttpEntity<>(managerHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    var merged =
        rest.postForEntity(
            "/store/customers/" + survivor + "/merges",
            new HttpEntity<>(Map.of("merged_customer_id", source), managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(merged.getStatusCode()).as("%s", merged.getBody()).isEqualTo(HttpStatus.OK);
    assertThat(detail(order).path("customer_id").asString()).isEqualTo(survivor);
    assertThat(detail(order).path("contact_snapshot")).isEqualTo(original);
  }

  @Test
  void newCustomerHasNoImplicitContactsAndEditingCanClearBothIndependently() {
    ObjectNode input = input("NEW");
    ((ObjectNode) input.get("customer_selection")).putObject("new_customer").put("name", "明示的新規");
    input.putObject("contact_snapshot").put("email", "temporary@example.com");
    JsonNode order = create(input);
    JsonNode customer = get("/store/customers/" + order.path("customer_id").asString());
    assertThat(customer.path("name").asString()).isEqualTo("明示的新規");
    assertThat(customer.hasNonNull("phone_number")).isFalse();
    ObjectNode update = json.createObjectNode();
    update.put("expected_version", order.path("version").asLong());
    update.put("cast_id", order.path("cast_id").asString());
    update.put("receptionist_id", order.path("receptionist_id").asLong());
    update.putObject("contact_snapshot").put("line_id", "changed-line");
    String path = "/store/orders/" + order.path("id").asString();
    var changed =
        submitPreviewed(
            path, HttpMethod.PUT, path + "/preview", update.toString(), managerHeaders(STORE_A));
    assertThat(changed.getStatusCode()).as("%s", changed.getBody()).isEqualTo(HttpStatus.OK);
    assertThat(changed.getBody().path("contact_snapshot").path("email").isNull()).isTrue();
    assertThat(changed.getBody().path("customer_id")).isEqualTo(order.path("customer_id"));
    update.put("expected_version", changed.getBody().path("version").asLong());
    update.putObject("customer_selection").put("mode", "NONE");
    update.putObject("contact_snapshot");
    var cleared =
        submitPreviewed(
            path, HttpMethod.PUT, path + "/preview", update.toString(), managerHeaders(STORE_A));
    assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(cleared.getBody().path("customer_id").isNull()).isTrue();
    assertThat(cleared.getBody().path("contact_snapshot").path("line_id").isNull()).isTrue();
    assertThat(create(input("NONE")).path("contact_snapshot").path("phone_number").isNull())
        .isTrue();
  }

  @Test
  void guestOriginalSurvivesConfirmationWithAnExplicitCustomer() {
    HttpHeaders anonymous = new HttpHeaders();
    anonymous.set("X-Role", "store");
    anonymous.set("X-Store-ID", "1");
    anonymous.setBearerAuth("expired-token");
    anonymous.set("X-Forwarded-For", UUID.randomUUID().toString());
    String originalEmail = " Guest@EXAMPLE.COM ";
    var application =
        rest.postForEntity(
            "/store/order-applications/public",
            new HttpEntity<>(
                Map.of(
                    "business_date",
                    LocalDate.now().plusDays(1).toString(),
                    "contact_snapshot",
                    Map.of("name", "ゲスト原文", "email", originalEmail, "line_id", " original-line ")),
                anonymous),
            JsonNode.class);
    assertThat(application.getStatusCode())
        .as("%s", application.getBody())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(application.getBody().size()).isEqualTo(1);
    String appId = application.getBody().path("id").asString();
    ObjectNode confirmation = json.createObjectNode();
    confirmation.put("business_date", LocalDate.now().plusDays(1).toString());
    confirmation.put("course_id", courseFixture(STORE_A, 100).serviceId());
    confirmation
        .putObject("customer_selection")
        .put("mode", "EXISTING")
        .put("customer_id", customer("確認先"));
    String path = "/store/order-applications/" + appId;
    var confirmed =
        submitPreviewed(
            path + "/confirmation",
            HttpMethod.POST,
            path + "/confirmation-preview",
            confirmation.toString(),
            managerHeaders(STORE_A));
    assertThat(confirmed.getStatusCode())
        .as("%s", confirmed.getBody())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(confirmed.getBody().path("contact_snapshot").path("email").asString())
        .isEqualTo("Guest@example.com");
    JsonNode applications =
        get("/store/order-applications?statuses=CONFIRMED&size=2000").path("content");
    JsonNode original = null;
    for (JsonNode row : applications) if (row.path("id").asString().equals(appId)) original = row;
    assertThat(original).isNotNull();
    assertThat(original.path("contact_snapshot").path("email").asString()).isEqualTo(originalEmail);
  }

  @Test
  void rejectsInvalidChoiceAndContactBeforeSaving() {
    ObjectNode input = input("NONE");
    ((ObjectNode) input.get("customer_selection")).put("customer_id", "1");
    assertThat(preview(input).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    input.putObject("customer_selection").put("mode", "NONE");
    for (String number : new String[] {"123", "+12025550123"}) {
      input.putObject("contact_snapshot").put("phone_number", number);
      assertThat(preview(input).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    input.putObject("contact_snapshot").put("email", "invalid-email");
    assertThat(preview(input).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    input.putObject("contact_snapshot");
    input.putObject("customer_selection").put("mode", "EXISTING").put("customer_id", "missing");
    assertThat(preview(input).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void orderPermissionCanSelectCandidatesButCannotCreateCustomers() {
    String nonce = UUID.randomUUID().toString();
    var role =
        roles.save(
            Role.builder()
                .name("受注専任" + nonce)
                .permissionIds(
                    permissions.findByCodeIn(Set.of("ORDER_MANAGE")).stream()
                        .map(Permission::getId)
                        .collect(Collectors.toSet()))
                .build());
    String email = "contact-" + nonce + "@kizuna.test";
    users.save(
        PlatformUser.builder()
            .email(email)
            .password(passwords.encode(NEW_ACCOUNT_PASSWORD))
            .displayName("受注専任")
            .enabled(true)
            .userType(UserType.STAFF)
            .roleIds(Set.of(role.getId()))
            .storeScopeType(StoreScopeType.ALL_STORES)
            .storeIds(Set.of())
            .build());
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Role", "store");
    headers.set("X-Store-ID", String.valueOf(STORE_A));
    headers.setBearerAuth(loginWithPassword(email, NEW_ACCOUNT_PASSWORD));
    String prefix = "候補" + nonce;
    String first = customer(prefix + "甲");
    String second = customer(prefix + "乙");
    var foreign = Customer.builder().name(prefix + "他店舗").build();
    foreign.setStoreId(STORE_B);
    customers.save(foreign);
    var page =
        rest.exchange(
            "/store/orders/customer-candidates?search=" + prefix + "&size=1",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class);
    assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
    var row = page.getBody().path("content").get(0);
    assertThat(row.size()).isEqualTo(3);
    String cursor = page.getBody().path("next_cursor").asString();
    var next =
        rest.exchange(
            "/store/orders/customer-candidates?search=" + prefix + "&size=1&cursor=" + cursor,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class);
    assertThat(next.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(next.getBody().path("content").size()).isEqualTo(1);
    assertThat(
            Set.of(
                row.path("id").asString(),
                next.getBody().path("content").get(0).path("id").asString()))
        .containsExactlyInAnyOrder(first, second);
    assertThat(next.getBody().hasNonNull("next_cursor")).isFalse();
    ObjectNode input = input("EXISTING");
    ((ObjectNode) input.get("customer_selection")).put("customer_id", foreign.getId());
    assertThat(
            rest.postForEntity(
                    "/store/orders/preview",
                    new HttpEntity<>(input.toString(), headers),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    ((ObjectNode) input.get("customer_selection")).put("customer_id", first);
    var saved =
        submitPreviewed(
            "/store/orders", HttpMethod.POST, "/store/orders/preview", input.toString(), headers);
    assertThat(saved.getStatusCode()).as("%s", saved.getBody()).isEqualTo(HttpStatus.CREATED);
    input
        .putObject("customer_selection")
        .put("mode", "NEW")
        .putObject("new_customer")
        .put("name", "禁止" + nonce);
    assertThat(
            rest.postForEntity(
                    "/store/orders/preview",
                    new HttpEntity<>(input.toString(), headers),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    input.put("confirmation_token", "invalid");
    assertThat(
            rest.postForEntity(
                    "/store/orders", new HttpEntity<>(input.toString(), headers), JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(get("/store/orders/customer-candidates?search=禁止" + nonce).path("content").size())
        .isZero();
  }

  @Test
  void failedConfirmationRollsBackNewCustomer() {
    String name = "巻戻" + UUID.randomUUID();
    ObjectNode input = input("NEW");
    ((ObjectNode) input.get("customer_selection")).putObject("new_customer").put("name", name);
    var preview = preview(input);
    assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
    input.put("confirmation_token", preview.getBody().path("confirmation_token").asString());
    input.put("remarks", "試算後に変更");
    var rejected =
        rest.postForEntity(
            "/store/orders",
            new HttpEntity<>(input.toString(), managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(rejected.getStatusCode())
        .as("%s", rejected.getBody())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(get("/store/orders/customer-candidates?search=" + name).path("content").size())
        .isZero();
  }

  private ObjectNode input(String mode) {
    var cast =
        rest.postForEntity(
            "/store/casts",
            new HttpEntity<>(Map.of("name", "連絡先検証" + UUID.randomUUID()), managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(cast.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    ObjectNode input = json.createObjectNode();
    input.put("business_date", LocalDate.now().plusDays(1).toString());
    input.put("cast_id", cast.getBody().path("id").asString());
    input.put("course_id", courseFixture(STORE_A, 100).serviceId());
    input.putObject("customer_selection").put("mode", mode);
    return input;
  }

  private ResponseEntity<JsonNode> preview(ObjectNode input) {
    return rest.postForEntity(
        "/store/orders/preview",
        new HttpEntity<>(input.toString(), managerHeaders(STORE_A)),
        JsonNode.class);
  }

  private JsonNode create(ObjectNode input) {
    var result =
        submitPreviewed(
            "/store/orders",
            HttpMethod.POST,
            "/store/orders/preview",
            input.toString(),
            managerHeaders(STORE_A));
    assertThat(result.getStatusCode()).as("%s", result.getBody()).isEqualTo(HttpStatus.CREATED);
    return result.getBody();
  }

  private String customer(String name) {
    var result =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>(
                Map.of(
                    "name",
                    name,
                    "contacts",
                    List.of(Map.of("type", "PHONE", "value", "09012345678"))),
                managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return result.getBody().path("id").asString();
  }

  private JsonNode detail(JsonNode order) {
    return get("/store/orders/" + order.path("id").asString());
  }

  private JsonNode get(String path) {
    var result =
        rest.exchange(
            path, HttpMethod.GET, new HttpEntity<>(managerHeaders(STORE_A)), JsonNode.class);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    return result.getBody();
  }
}
