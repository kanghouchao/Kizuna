package com.kizuna.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.CrossStoreTestSupport;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

class ServiceSettingsIT extends CrossStoreTestSupport {
  private static final String COURSE =
      """
      {"kind":"COURSE","name":"基本","duration_minutes":60,"price":12000,"remuneration":7000}
      """;

  @Test
  void createUpdateDeletePreservesEveryRevision() {
    var headers = managerHeaders(STORE_A);
    var created = request(HttpMethod.POST, "", COURSE, headers);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = created.getBody().path("id").asString();
    assertThat(request(HttpMethod.GET, "/" + id, null, headers).getBody().path("version").asInt())
        .isEqualTo(1);
    String update =
        """
        {"name":"改定","duration_minutes":90,"price":15000,"remuneration":9000,"expected_version":1}
        """;
    var updated = request(HttpMethod.PUT, "/" + id, update, headers);
    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updated.getBody().path("version").asInt()).isEqualTo(2);
    assertThat(request(HttpMethod.PUT, "/" + id, update, headers).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            request(HttpMethod.DELETE, "/" + id + "?expected_version=2", null, headers)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var history = request(HttpMethod.GET, "/" + id + "/revisions?size=2", null, headers).getBody();
    assertThat(history.path("content").size()).isEqualTo(2);
    var deletion = history.path("content").get(0);
    assertThat(deletion.path("operation").asString()).isEqualTo("DELETED");
    assertThat(deletion.path("before").path("price").asInt()).isEqualTo(15000);
    assertThat(deletion.path("after").path("deleted").asBoolean()).isTrue();
    assertThat(deletion.path("actor_id").asString()).isNotBlank();
    assertThat(deletion.path("occurred_at").asString()).isNotBlank();
    var change = history.path("content").get(1);
    assertThat(change.path("before").path("price").asInt()).isEqualTo(12000);
    assertThat(change.path("after").path("price").asInt()).isEqualTo(15000);
    var last =
        request(
                HttpMethod.GET,
                "/" + id + "/revisions?size=2&cursor=" + history.path("next_cursor").asString(),
                null,
                headers)
            .getBody();
    assertThat(last.path("content").size()).isEqualTo(1);
    assertThat(last.path("content").get(0).path("version").asInt()).isEqualTo(1);
    assertThat(last.has("next_cursor")).isFalse();
    assertThat(
            request(HttpMethod.GET, "/" + id, null, headers).getBody().path("deleted").asBoolean())
        .isTrue();
  }

  @Test
  void storeIsolationCoversListsDirectIdsAndEveryMutation() {
    var a = managerHeaders(STORE_A);
    var b = managerHeaders(STORE_B);
    String id = request(HttpMethod.POST, "", COURSE, a).getBody().path("id").asString();
    assertThat(request(HttpMethod.POST, "", COURSE, b).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    for (String suffix : new String[] {"/" + id, "/" + id + "/revisions"}) {
      assertThat(request(HttpMethod.GET, suffix, null, b).getStatusCode())
          .isEqualTo(HttpStatus.NOT_FOUND);
    }
    assertThat(request(HttpMethod.PUT, "/" + id, updateBody(1, 13000), b).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            request(HttpMethod.DELETE, "/" + id + "?expected_version=1", null, b).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    var list = request(HttpMethod.GET, "?size=2000", null, b).getBody().path("content");
    for (JsonNode row : list) assertThat(row.path("id").asString()).isNotEqualTo(id);
    assertThat(request(HttpMethod.GET, "/" + id, null, a).getBody().path("version").asInt())
        .isEqualTo(1);
  }

  @Test
  void onlyServicePermissionAllowsSettingsAndCustomRoleCanDelegateIt() {
    var manager = managerHeaders(STORE_A);
    String id = request(HttpMethod.POST, "", COURSE, manager).getBody().path("id").asString();
    var hq = new HttpHeaders();
    hq.putAll(manager);
    hq.setBearerAuth(login("admin@kizuna.test"));
    for (var denied : new HttpHeaders[] {storeHeaders(STORE_A), hq}) {
      assertThat(request(HttpMethod.GET, "", null, denied).getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(request(HttpMethod.GET, "/" + id, null, denied).getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(request(HttpMethod.GET, "/" + id + "/revisions", null, denied).getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(request(HttpMethod.POST, "", COURSE, denied).getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(request(HttpMethod.PUT, "/" + id, updateBody(1, 13000), denied).getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(
              request(HttpMethod.DELETE, "/" + id + "?expected_version=1", null, denied)
                  .getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
    var role =
        rest.postForEntity(
            "/platform/roles",
            new HttpEntity<>(
                "{\"name\":\"サービス委譲-"
                    + System.nanoTime()
                    + "\",\"permissions\":[\"SERVICE_MANAGE\"]}",
                hq),
            JsonNode.class);
    assertThat(role.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String email = "services-" + System.nanoTime() + "@kizuna.test";
    var staff =
        rest.postForEntity(
            "/store/staff-members",
            new HttpEntity<>(
                """
        {"email":"%s","password":"%s","display_name":"サービス担当","role_ids":[%s],"store_scope_type":"SPECIFIC_STORES","store_ids":[1]}
        """
                    .formatted(email, NEW_ACCOUNT_PASSWORD, role.getBody().path("id").asString()),
                manager),
            JsonNode.class);
    assertThat(staff.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    var delegated = new HttpHeaders();
    delegated.putAll(manager);
    delegated.setBearerAuth(loginWithPassword(email, NEW_ACCOUNT_PASSWORD));
    assertThat(request(HttpMethod.PUT, "/" + id, updateBody(1, 13000), delegated).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(request(HttpMethod.GET, "/" + id + "/revisions", null, delegated).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var menu =
        rest.exchange(
            "/platform/menus/me", HttpMethod.GET, new HttpEntity<>(delegated), JsonNode.class);
    assertThat(menu.getBody().toString()).contains("/store/services");
    assertThat(menu.getBody().get(0).path("items").size()).isGreaterThan(0);
    var hqMenu =
        rest.exchange("/platform/menus/me", HttpMethod.GET, new HttpEntity<>(hq), JsonNode.class);
    assertThat(hqMenu.getBody().toString()).doesNotContain("/store/services");
    var anonymous = new HttpHeaders();
    anonymous.putAll(manager);
    anonymous.remove(HttpHeaders.AUTHORIZATION);
    assertThat(request(HttpMethod.GET, "", null, anonymous).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void rejectsInvalidAmountsAndPagesWithoutLeavingHistory() {
    var headers = managerHeaders(STORE_A);
    for (String body :
        new String[] {
          COURSE.replace("12000", "12000.5"),
          COURSE.replace("7000", "12001"),
          COURSE.replace("60", "0"),
          COURSE.replace("12000", "2147483648"),
          COURSE.replace("7000", "null"),
          COURSE.replace("\"COURSE\"", "\"SURCHARGE\"")
        }) {
      assertThat(request(HttpMethod.POST, "", body, headers).getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    var free =
        request(
            HttpMethod.POST,
            "",
            """
        {"kind":"SPECIAL_SERVICE","name":"無料","charge_type":"FREE","price":0,"remuneration":0}
        """,
            headers);
    assertThat(free.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = request(HttpMethod.POST, "", COURSE, headers).getBody().path("id").asString();
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/" + id,
                    COURSE.replace("\"kind\":\"COURSE\",", "\"expected_version\":1,"),
                    headers)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(HttpMethod.GET, "/" + id + "/revisions", null, headers)
                .getBody()
                .path("content")
                .size())
        .isEqualTo(1);
    for (String suffix :
        new String[] {
          "?page=-1",
          "?size=0",
          "?size=2001",
          "/" + id + "/revisions?cursor=bad!",
          "/" + id + "/revisions?cursor=MA"
        }) {
      assertThat(request(HttpMethod.GET, suffix, null, headers).getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    assertThat(
            request(HttpMethod.GET, "/" + id + "/revisions?size=9999", null, headers)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(HttpMethod.DELETE, "/" + id + "?expected_version=1", null, headers)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(
            request(HttpMethod.DELETE, "/" + id + "?expected_version=2", null, headers)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(request(HttpMethod.PUT, "/" + id, updateBody(2, 14000), headers).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    var deleted =
        request(HttpMethod.GET, "?deleted=true&kind=COURSE&size=2000", null, headers)
            .getBody()
            .path("content");
    assertThat(deleted.toString()).contains(id);
    assertThat(
            request(HttpMethod.GET, "?deleted=false&size=2000", null, headers)
                .getBody()
                .path("content")
                .toString())
        .doesNotContain(id);
  }

  @Test
  void concurrentChangesHaveOneWinnerAndNoPartialHistory() throws Exception {
    var headers = managerHeaders(STORE_A);
    String id = request(HttpMethod.POST, "", COURSE, headers).getBody().path("id").asString();
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return request(HttpMethod.PUT, "/" + id, updateBody(1, 13000), headers);
              });
      var second =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return request(HttpMethod.DELETE, "/" + id + "?expected_version=1", null, headers);
              });
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      var a = first.get(30, TimeUnit.SECONDS).getStatusCode();
      var b = second.get(30, TimeUnit.SECONDS).getStatusCode();
      assertThat((a.value() == 200 && b.value() == 409) || (a.value() == 400 && b.value() == 204))
          .as("更新と削除は片方だけが成立する")
          .isTrue();
    }
    var current = request(HttpMethod.GET, "/" + id, null, headers).getBody();
    var history =
        request(HttpMethod.GET, "/" + id + "/revisions", null, headers).getBody().path("content");
    assertThat(history.size()).isEqualTo(2);
    assertThat(current.path("version").asInt()).isEqualTo(2);
    assertThat(history.get(0).path("after").path("price")).isEqualTo(current.path("price"));
    assertThat(history.get(0).path("after").path("deleted")).isEqualTo(current.path("deleted"));
  }

  private String updateBody(long version, int price) {
    return """
      {"name":"改定","duration_minutes":90,"price":%d,"remuneration":9000,"expected_version":%d}
      """
        .formatted(price, version);
  }

  private ResponseEntity<JsonNode> request(
      HttpMethod method, String suffix, String body, HttpHeaders headers) {
    return rest.exchange(
        "/store/services" + suffix, method, new HttpEntity<>(body, headers), JsonNode.class);
  }
}
