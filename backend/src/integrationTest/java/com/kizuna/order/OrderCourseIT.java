package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.CrossStoreTestSupport;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

class OrderCourseIT extends CrossStoreTestSupport {
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;

  @Test
  void courseSelectionRequiresOrderPermissionsAndRejectsUnconfirmedChanges() {
    var manager = managerHeaders(STORE_A);
    var course = courseFixture(STORE_A, "照会" + System.nanoTime(), 60, 12000);
    String cast =
        post("/store/casts", "{\"name\":\"確認検証\"}", manager).getBody().path("id").asString();
    String input =
        "{\"business_date\":\"2027-01-20\",\"cast_id\":\""
            + cast
            + "\",\"course_id\":\""
            + course.serviceId()
            + "\"}";
    var candidates =
        rest.exchange(
            "/store/orders/course-candidates?search=" + course.name(),
            HttpMethod.GET,
            new HttpEntity<>(storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(candidates.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(candidates.getBody().toString())
        .contains(course.serviceId())
        .doesNotContain("actor_id");
    var foreign =
        rest.exchange(
            "/store/orders/course-candidates",
            HttpMethod.GET,
            new HttpEntity<>(managerHeaders(STORE_B)),
            JsonNode.class);
    assertThat(foreign.getBody().toString()).doesNotContain(course.serviceId());
    assertThat(
            rest.exchange(
                    "/store/orders/course-candidates?size=101",
                    HttpMethod.GET,
                    new HttpEntity<>(manager),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    var preview = post("/store/orders/preview", input, manager).getBody();
    String confirmed = confirmed(input, preview);
    assertThat(post("/store/orders/preview", confirmed, manager).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            post("/store/orders", confirmed.replace("2027-01-20", "2027-01-21"), manager)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    for (String lines :
        new String[] {
          "[{\"kind\":\"BASE_COURSE\",\"amount\":1}]",
          "[{\"kind\":\"POINT_REDEMPTION\",\"amount\":1}]",
          "[{\"kind\":\"DISCOUNT\",\"name\":\"過大割引\",\"amount\":13000}]"
        }) {
      String invalid = input.substring(0, input.length() - 1) + ",\"fee_lines\":" + lines + "}";
      assertThat(post("/store/orders/preview", invalid, manager).getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_orders where course_revision_id = ?",
                Integer.class,
                course.revisionId()))
        .isZero();
    var created = post("/store/orders", confirmed, manager);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = created.getBody().path("id").asString();
    assertThat(
            rest.exchange(
                    "/store/orders/" + id + "/course-revisions",
                    HttpMethod.GET,
                    new HttpEntity<>(storeHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            rest.exchange(
                    "/store/orders/" + id + "/course-revisions",
                    HttpMethod.GET,
                    new HttpEntity<>(managerHeaders(STORE_B)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            rest.exchange(
                    "/store/orders/work-queue?statuses=CONFIRMED&sort_key=COURSE_MINUTES",
                    HttpMethod.GET,
                    new HttpEntity<>(manager),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void previewRequiresReconfirmationAfterRevisionAndExistingOrderKeepsItsCourse() {
    var headers = managerHeaders(STORE_A);
    String course =
        post(
                "/store/services",
                """
        {"kind":"COURSE","name":"基本","duration_minutes":60,"price":12000,"remuneration":7000}
        """,
                headers)
            .getBody()
            .path("id")
            .asString();
    String cast =
        post("/store/casts", "{\"name\":\"試算担当\"}", headers).getBody().path("id").asString();
    String input =
        """
        {"business_date":"2027-01-20","cast_id":"%s","course_id":"%s",
         "fee_lines":[{"kind":"DISCOUNT","name":"割引","amount":2000}]}
        """
            .formatted(cast, course);
    var preview = post("/store/orders/preview", input, headers);
    assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(preview.getBody().path("total_fee").asInt()).isEqualTo(10000);
    assertThat(preview.getBody().path("course").path("remuneration").asInt()).isEqualTo(7000);
    assertThat(post("/store/orders", input, headers).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    var revised =
        rest.exchange(
            "/store/services/" + course,
            HttpMethod.PUT,
            new HttpEntity<>(
                """
          {"name":"改定","duration_minutes":90,"price":15000,"remuneration":9000,"expected_version":1}
          """,
                headers),
            JsonNode.class);
    assertThat(revised.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(post("/store/orders", confirmed(input, preview.getBody()), headers).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    var next = post("/store/orders/preview", input, headers);
    assertThat(next.getStatusCode()).isEqualTo(HttpStatus.OK);
    var saved = post("/store/orders", confirmed(input, next.getBody()), headers);
    assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(saved.getBody().path("total_fee").asInt()).isEqualTo(13000);
    String id = saved.getBody().path("id").asString();
    assertThat(
            rest.exchange(
                    "/store/services/" + course + "?expected_version=2",
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    var detail =
        rest.exchange(
                "/store/orders/" + id, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class)
            .getBody();
    assertThat(detail.path("course").path("price").asInt()).isEqualTo(15000);
    String completion =
        "{\"expected_version\":" + detail.path("version").asLong() + ",\"fee_lines\":[]}";
    var completionPreview =
        post("/store/orders/" + id + "/completion-preview", completion, headers);
    assertThat(completionPreview.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
            post(
                    "/store/orders/" + id + "/completion",
                    confirmed(completion, completionPreview.getBody()),
                    headers)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var history =
        rest.exchange(
            "/store/orders/" + id + "/course-revisions",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class);
    assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(history.getBody().path("content").size()).isGreaterThanOrEqualTo(2);
    String oldRevision = "";
    for (var row : history.getBody().path("content")) {
      if (row.path("service_id").asString().equals(course)
          && row.path("revision_number").asInt() == 1)
        oldRevision = row.path("revision_id").asString();
    }
    assertThat(oldRevision).isNotBlank();
    var completed =
        rest.exchange(
                "/store/orders/" + id, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class)
            .getBody();
    String correction =
        "{\"expected_version\":"
            + completed.path("version").asLong()
            + ",\"course_revision_id\":\""
            + oldRevision
            + "\",\"reason\":\"提供したコースの訂正\",\"fee_lines\":[]}";
    var correctionPreview =
        post("/store/orders/" + id + "/correction-preview", correction, headers);
    assertThat(correctionPreview.getStatusCode()).isEqualTo(HttpStatus.OK);
    var corrected =
        post(
            "/store/orders/" + id + "/corrections",
            confirmed(correction, correctionPreview.getBody()),
            headers);
    assertThat(corrected.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(corrected.getBody().path("correction_id").asString()).isNotBlank();
    assertThat(corrected.getBody().path("course").path("price").asInt()).isEqualTo(12000);
    assertThat(corrected.getBody().path("previous_course").path("price").asInt()).isEqualTo(15000);
    assertThat(
            post(
                    "/store/orders/" + id + "/corrections",
                    confirmed(correction, correctionPreview.getBody()),
                    headers)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            rest.exchange(
                    "/store/orders/" + id,
                    HttpMethod.GET,
                    new HttpEntity<>(managerHeaders(STORE_B)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void concurrentSaveAndSettingChangeNeverLeaveMixedTerms() throws Exception {
    var headers = managerHeaders(STORE_A);
    for (boolean deleting : new boolean[] {false, true}) {
      String service =
          post(
                  "/store/services",
                  """
          {"kind":"COURSE","name":"競合","duration_minutes":60,"price":12000,"remuneration":7000}
          """,
                  headers)
              .getBody()
              .path("id")
              .asString();
      String cast =
          post("/store/casts", "{\"name\":\"競合担当\"}", headers).getBody().path("id").asString();
      String input =
          "{\"business_date\":\"2027-01-20\",\"cast_id\":\""
              + cast
              + "\",\"course_id\":\""
              + service
              + "\"}";
      var preview = post("/store/orders/preview", input, headers).getBody();
      String body = confirmed(input, preview);
      var ready = new CountDownLatch(2);
      var start = new CountDownLatch(1);
      try (var executor = Executors.newFixedThreadPool(2)) {
        var save =
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return post("/store/orders", body, headers);
                });
        var change =
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return rest.exchange(
                      "/store/services/" + service + (deleting ? "?expected_version=1" : ""),
                      deleting ? HttpMethod.DELETE : HttpMethod.PUT,
                      new HttpEntity<>(
                          deleting
                              ? null
                              : """
                {"name":"変更","duration_minutes":90,"price":15000,"remuneration":9000,"expected_version":1}
                """,
                          headers),
                      JsonNode.class);
                });
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        var result = save.get(30, TimeUnit.SECONDS);
        assertThat(change.get(30, TimeUnit.SECONDS).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.CONFLICT);
        long count =
            jdbc.queryForObject(
                "select count(*) from t_orders where course_revision_id = ?",
                Long.class,
                preview.path("course").path("revision_id").asString());
        assertThat(count).isEqualTo(result.getStatusCode() == HttpStatus.CREATED ? 1 : 0);
        if (result.getStatusCode() == HttpStatus.CREATED) {
          assertThat(result.getBody().path("course").path("price").asInt()).isEqualTo(12000);
          assertThat(result.getBody().path("course").path("remuneration").asInt()).isEqualTo(7000);
          assertThat(result.getBody().path("total_fee").asInt()).isEqualTo(12000);
        }
      }
    }
  }

  @Test
  void creationAndPreviewShareTheCastThenCourseLockOrder() throws Exception {
    var headers = managerHeaders(STORE_A);
    var course = courseFixture(STORE_A, 12000);
    String cast =
        post("/store/casts", "{\"name\":\"順序検証\"}", headers).getBody().path("id").asString();
    String input =
        "{\"business_date\":\"2027-01-20\",\"cast_id\":\""
            + cast
            + "\",\"course_id\":\""
            + course.serviceId()
            + "\"}";
    String saveInput = confirmed(input, post("/store/orders/preview", input, headers).getBody());
    try (var connection = dataSource.getConnection();
        var executor = Executors.newFixedThreadPool(2)) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement(
              "select id from t_cast_enrollments where id = ? for update")) {
        statement.setString(1, cast);
        statement.executeQuery().close();
      }
      try {
        var preview = executor.submit(() -> post("/store/orders/preview", input, headers));
        awaitNominationWaiters(1);
        var save = executor.submit(() -> post("/store/orders", saveInput, headers));
        awaitNominationWaiters(2);
        connection.commit();
        assertThat(preview.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(save.get(15, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.CREATED);
      } finally {
        connection.rollback();
      }
    }
  }

  private void awaitNominationWaiters(int minimum) throws InterruptedException {
    for (int attempt = 0; attempt < 100; attempt++) {
      Integer count =
          jdbc.queryForObject(
              "select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock' and (query like '%t_cast_enrollments%' or query like '%t_stores%')",
              Integer.class);
      if (count >= minimum) return;
      TimeUnit.MILLISECONDS.sleep(50);
    }
    throw new AssertionError("指名のロック待機が成立しませんでした: " + minimum);
  }

  private String confirmed(String input, JsonNode preview) {
    return input.strip().substring(0, input.strip().length() - 1)
        + ",\"confirmation_token\":\""
        + preview.path("confirmation_token").asString()
        + "\"}";
  }

  private ResponseEntity<JsonNode> post(String path, String body, HttpHeaders headers) {
    return rest.postForEntity(path, new HttpEntity<>(body, headers), JsonNode.class);
  }
}
