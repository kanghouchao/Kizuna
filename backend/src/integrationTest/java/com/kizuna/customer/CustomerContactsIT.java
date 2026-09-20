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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

class CustomerContactsIT extends CrossStoreTestSupport {
  @Autowired RoleRepository roles;
  @Autowired PermissionRepository permissions;
  @Autowired PlatformUserRepository users;
  @Autowired PasswordEncoder passwords;

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
    String merge = "{\"merged_customer_id\":\"" + b + "\"}";
    assertThat(request(HttpMethod.PUT, pathB + "/contact-preferences/EMAIL", "{}").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(request(HttpMethod.POST, pathA + "/merges", merge).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    request(HttpMethod.PUT, pathB + "/contact-preferences/EMAIL", "{\"contact_id\":null}");
    assertThat(request(HttpMethod.POST, pathA + "/merges", merge).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var contacts = request(HttpMethod.GET, pathA + "/contacts", null).getBody().path("content");
    assertThat(contacts.size()).isEqualTo(2);
    assertThat(contacts.get(1).path("id").asString()).isEqualTo(second);
    assertThat(contacts.get(1).path("origin_customer_id").asString()).isEqualTo(b);
    assertThat(contacts.get(1).path("value").asString()).isEqualTo("A+tag@example.com");
    assertThat(
            request(HttpMethod.GET, pathA + "/contact-history", null)
                .getBody()
                .path("content")
                .size())
        .isEqualTo(6);
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
            request(HttpMethod.GET, "/store/customers?search=mixed.case", null)
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
      var merge =
          rest.postForEntity(
              path + "/merges",
              new HttpEntity<>(Map.of("merged_customer_id", "missing"), headers),
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
