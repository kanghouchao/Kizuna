package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
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

class SelfRemunerationIT extends CrossStoreTestSupport {
  @Autowired PlatformUserRepository users;
  @Autowired CastEnrollmentRepository enrollments;
  @Autowired PasswordEncoder passwords;

  @Test
  void withdrawnEnrollmentIsVisibleWithoutCurrentStoreAccess() {
    var owner = owner();
    var enrollment = enrollments.findById(owner.enrollmentId()).orElseThrow();
    enrollment.withdraw(OffsetDateTime.now());
    enrollments.saveAndFlush(enrollment);
    var response = get(owner.headers(), "/platform/me/remuneration-enrollments");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var rows = response.getBody().path("content");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).path("enrollment_id").asString()).isEqualTo(owner.enrollmentId());
    assertThat(rows.get(0).path("status").asString()).isEqualTo("WITHDRAWN");
    assertThat(rows.get(0).has("ended_at")).isTrue();
    assertThat(
            get(owner().headers(), "/platform/me/remuneration-enrollments")
                .getBody()
                .path("content")
                .get(0)
                .path("enrollment_id")
                .asString())
        .isNotEqualTo(owner.enrollmentId());
  }

  @Test
  void onlyTheCurrentOwnerCanReadOrdersAndPrivateFieldsAreAbsent() {
    var owner = owner();
    var other = owner();
    String id = createOrder(owner);
    var response = get(owner.headers(), "/platform/me/remunerations/" + id);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var detail = response.getBody();
    assertThat(detail.path("planned_remuneration").asInt()).isEqualTo(7000);
    assertThat(detail.path("accrued_remuneration").asInt()).isZero();
    assertThat(detail.path("items")).hasSize(1);
    assertThat(detail.toString())
        .doesNotContain("秘密顧客", "contact", "customer", "remarks", "receptionist", "paid_amount");
    assertThat(get(owner.headers(), "/platform/me/remunerations").getBody().path("content"))
        .hasSize(1);
    assertThat(get(other.headers(), "/platform/me/remunerations").getBody().path("content"))
        .isEmpty();
    assertThat(get(other.headers(), "/platform/me/remunerations/" + id).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            get(other.headers(), "/platform/me/remunerations/" + id + "/changes").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            get(other.headers(), "/platform/me/remunerations?enrollment_id=" + owner.enrollmentId())
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(storeHeaders(STORE_A), "/platform/me/remunerations").getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(get(new HttpHeaders(), "/platform/me/remunerations").getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void withdrawnOwnerSeesOriginalFactsAndImmutableChangesWithoutPagingDuplicates() {
    var owner = owner();
    String id = createOrder(owner);
    var headers = managerHeaders(STORE_A);
    assertThat(
            rest.postForEntity(
                    "/store/orders/" + id + "/completion",
                    completionFixtureRequest(id, 12000, null, headers),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var original = get(owner.headers(), "/platform/me/remunerations/" + id).getBody();
    assertThat(original.path("accrued_remuneration").asInt()).isEqualTo(7000);
    correct(id, 2000);
    correct(id, 3000);
    var first = get(owner.headers(), "/platform/me/remunerations/" + id + "/changes?size=1");
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    var row = first.getBody().path("content").get(0);
    assertThat(row.path("before").path("accrued_remuneration").asInt()).isEqualTo(9000);
    assertThat(row.path("after").path("accrued_remuneration").asInt()).isEqualTo(10000);
    String cursor = first.getBody().path("next_cursor").asString();
    correct(id, 1000);
    var next =
        get(
                owner.headers(),
                "/platform/me/remunerations/" + id + "/changes?size=1&cursor=" + cursor)
            .getBody();
    assertThat(next.path("content")).hasSize(1);
    assertThat(next.path("content").get(0).path("change_id")).isNotEqualTo(row.path("change_id"));
    assertThat(next.path("content").get(0).path("before").path("accrued_remuneration").asInt())
        .isEqualTo(7000);
    assertThat(next.has("next_cursor")).isFalse();
    assertThat(
            get(owner.headers(), "/platform/me/remunerations/" + id + "/changes?cursor=bad")
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    String otherOrder = createOrder(owner);
    assertThat(
            get(
                    owner.headers(),
                    "/platform/me/remunerations/" + otherOrder + "/changes?cursor=" + cursor)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    var latest = get(headers, "/store/orders/" + id).getBody();
    assertThat(
            rest.postForEntity(
                    "/store/orders/" + id + "/completion-invalidation",
                    new HttpEntity<>(
                        Map.of(
                            "expected_version",
                            latest.path("version").asLong(),
                            "reason",
                            "提供前の誤完了"),
                        headers),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    var enrollment = enrollments.findById(owner.enrollmentId()).orElseThrow();
    enrollment.withdraw(OffsetDateTime.now());
    enrollments.saveAndFlush(enrollment);
    var detail = get(owner.headers(), "/platform/me/remunerations/" + id).getBody();
    assertThat(detail.path("business_date")).isEqualTo(original.path("business_date"));
    assertThat(detail.path("completed_at")).isEqualTo(original.path("completed_at"));
    assertThat(detail.path("completion_invalidated").asBoolean()).isTrue();
    assertThat(detail.path("agreed_remuneration").asInt()).isEqualTo(8000);
    assertThat(detail.path("accrued_remuneration").asInt()).isZero();
    assertThat(detail.path("planned_remuneration").asInt()).isZero();
    var changes =
        get(owner.headers(), "/platform/me/remunerations/" + id + "/changes")
            .getBody()
            .path("content");
    assertThat(changes).hasSize(4);
    assertThat(changes.get(0).path("change_type").asString()).isEqualTo("COMPLETION_INVALIDATION");
    assertThat(changes.get(0).path("before").path("accrued_remuneration").asInt()).isEqualTo(8000);
    assertThat(changes.get(0).path("after").path("agreed_remuneration").asInt()).isEqualTo(8000);
    assertThat(changes.get(0).path("after").path("accrued_remuneration").asInt()).isZero();
    assertThat(changes.toString()).doesNotContain("customer", "corrected_by", "contact", "秘密顧客");
    assertThat(
            get(owner().headers(), "/platform/me/remunerations/" + id + "/changes").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void reassignmentRemovesTheFormerOwnersAccessAndCancellationNeverAccrues() {
    var owner = owner();
    var other = owner();
    String id = createOrder(owner);
    var headers = managerHeaders(STORE_A);
    var order = get(headers, "/store/orders/" + id).getBody();
    String update =
        "{\"expected_version\":%d,\"receptionist_id\":%d,\"cast_id\":\"%s\"}"
            .formatted(
                order.path("version").asLong(),
                order.path("receptionist_id").asLong(),
                other.enrollmentId());
    assertThat(
            rest.exchange(
                    "/store/orders/" + id,
                    HttpMethod.PUT,
                    confirmedRequest("/store/orders/" + id + "/preview", update, headers),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(get(owner.headers(), "/platform/me/remunerations").getBody().path("content"))
        .isEmpty();
    assertThat(get(owner.headers(), "/platform/me/remunerations/" + id).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            get(owner.headers(), "/platform/me/remunerations/" + id + "/changes").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(other.headers(), "/platform/me/remunerations/" + id).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            rest.postForEntity(
                    "/store/orders/" + id + "/cancellation",
                    new HttpEntity<>(Map.of("reason", "来店なし"), headers),
                    JsonNode.class)
                .getStatusCode()
                .is2xxSuccessful())
        .isTrue();
    var cancelled = get(other.headers(), "/platform/me/remunerations/" + id).getBody();
    assertThat(cancelled.path("status").asString()).isEqualTo("CANCELLED");
    assertThat(cancelled.path("agreed_remuneration").asInt()).isEqualTo(7000);
    assertThat(cancelled.path("planned_remuneration").asInt()).isZero();
    assertThat(cancelled.path("accrued_remuneration").asInt()).isZero();
    assertThat(cancelled.has("completed_at")).isFalse();
  }

  @Test
  void allEnrollmentEpisodesRemainReachableAndPageInputsAreBounded() {
    var owner = owner();
    String oldOrder = createOrder(owner);
    assertThat(
            rest.postForEntity(
                    "/store/casts/" + owner.enrollmentId() + "/withdrawal",
                    new HttpEntity<>(managerHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode()
                .is2xxSuccessful())
        .isTrue();
    var rejoined = CastEnrollment.builder().build();
    rejoined.setStoreId(STORE_A);
    String newEnrollment = saveEnrollmentFixture(rejoined, "再入店本人", owner.userId()).getId();
    String newOrder = createOrder(new Owner(owner.headers(), newEnrollment, owner.userId()));
    var anotherStore = CastEnrollment.builder().build();
    anotherStore.setStoreId(STORE_B);
    String anotherEnrollment =
        saveEnrollmentFixture(anotherStore, "他店在籍本人", owner.userId()).getId();
    String anotherOrder =
        createOrder(new Owner(owner.headers(), anotherEnrollment, owner.userId()), STORE_B);
    var first = get(owner.headers(), "/platform/me/remunerations?size=1").getBody();
    var second = get(owner.headers(), "/platform/me/remunerations?size=1&page=1").getBody();
    var third = get(owner.headers(), "/platform/me/remunerations?size=1&page=2").getBody();
    assertThat(
            Set.of(
                first.path("content").get(0).path("order_id").asString(),
                second.path("content").get(0).path("order_id").asString(),
                third.path("content").get(0).path("order_id").asString()))
        .containsExactlyInAnyOrder(oldOrder, newOrder, anotherOrder);
    assertThat(first.path("total_elements").asInt()).isEqualTo(3);
    assertThat(
            get(owner.headers(), "/platform/me/remunerations?enrollment_id=" + owner.enrollmentId())
                .getBody()
                .path("content")
                .get(0)
                .path("order_id")
                .asString())
        .isEqualTo(oldOrder);
    assertThat(
            get(owner.headers(), "/platform/me/remuneration-enrollments?size=1")
                .getBody()
                .path("total_elements")
                .asInt())
        .isEqualTo(3);
    assertThat(
            get(owner.headers(), "/platform/me/remunerations?size=99999")
                .getBody()
                .path("size")
                .asInt())
        .isEqualTo(2000);
    assertThat(get(owner.headers(), "/platform/me/remunerations?page=-1").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void acceptedItemsRetainTermsWhileWithdrawalRevokesConsentOnly() {
    var owner = owner();
    var headers = managerHeaders(STORE_A);
    var special =
        rest.postForEntity(
            "/store/services",
            new HttpEntity<>(
                Map.of(
                    "kind",
                    "SPECIAL_SERVICE",
                    "name",
                    "本人受諾項目",
                    "charge_type",
                    "PAID",
                    "price",
                    2000,
                    "remuneration",
                    1500),
                headers),
            JsonNode.class);
    assertThat(special.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String specialId = special.getBody().path("id").asString();
    String consentPath =
        "/platform/me/service-conditions/" + specialId + "/consent?store_id=" + STORE_A;
    var decision = Map.of("terms_version", 1, "consent_version", 0, "decision", "ACCEPTED");
    assertThat(
            rest.exchange(
                    consentPath,
                    HttpMethod.PUT,
                    new HttpEntity<>(decision, owner.headers()),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    String id = createOrder(owner);
    var order = get(headers, "/store/orders/" + id).getBody();
    String update =
        """
        {"expected_version":%d,"receptionist_id":%d,"cast_id":"%s","special_service_ids":["%s"],"fee_lines":[{"kind":"EXTENSION","name":"延長","amount":3000,"duration_minutes":30,"remuneration":2000},{"kind":"DISCOUNT","name":"通常割引","amount":2000}]}
        """
            .formatted(
                order.path("version").asLong(),
                order.path("receptionist_id").asLong(),
                owner.enrollmentId(),
                specialId);
    assertThat(
            rest.exchange(
                    "/store/orders/" + id,
                    HttpMethod.PUT,
                    confirmedRequest("/store/orders/" + id + "/preview", update, headers),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var before = get(owner.headers(), "/platform/me/remunerations/" + id).getBody();
    assertThat(before.path("planned_remuneration").asInt()).isEqualTo(10500);
    assertThat(before.path("items")).hasSize(3);
    var terms = before.path("items").get(1);
    assertThat(terms.path("kind").asString()).isEqualTo("SPECIAL_SERVICE");
    assertThat(terms.path("consent_event_id").asString()).isNotBlank();
    assertThat(terms.path("terms_version").asLong()).isEqualTo(1);
    assertThat(terms.has("enrollment_id")).isFalse();
    assertThat(before.toString()).doesNotContain("DISCOUNT", "POINT_REDEMPTION");
    assertThat(
            rest.postForEntity(
                    "/store/casts/" + owner.enrollmentId() + "/withdrawal",
                    new HttpEntity<>(headers),
                    JsonNode.class)
                .getStatusCode()
                .is2xxSuccessful())
        .isTrue();
    assertThat(
            rest.exchange(
                    consentPath,
                    HttpMethod.PUT,
                    new HttpEntity<>(decision, owner.headers()),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    var after = get(owner.headers(), "/platform/me/remunerations/" + id);
    assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(after.getBody().path("items")).isEqualTo(before.path("items"));
  }

  private void correct(String id, int remuneration) {
    var headers = managerHeaders(STORE_A);
    long version = get(headers, "/store/orders/" + id).getBody().path("version").asLong();
    String input =
        """
        {"expected_version":%d,"reason":"延長の訂正","fee_lines":[{"kind":"EXTENSION","name":"延長","amount":3000,"duration_minutes":30,"remuneration":%d}]}
        """
            .formatted(version, remuneration);
    assertThat(
            rest.postForEntity(
                    "/store/orders/" + id + "/corrections",
                    confirmedRequest("/store/orders/" + id + "/correction-preview", input, headers),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
  }

  private String createOrder(Owner owner) {
    return createOrder(owner, STORE_A);
  }

  private String createOrder(Owner owner, long storeId) {
    var headers = managerHeaders(storeId);
    var course =
        rest.postForEntity(
            "/store/services",
            new HttpEntity<>(
                Map.of(
                    "kind",
                    "COURSE",
                    "name",
                    "本人報酬コース",
                    "duration_minutes",
                    60,
                    "price",
                    12000,
                    "remuneration",
                    7000),
                headers),
            JsonNode.class);
    assertThat(course.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String body =
        """
        {"business_date":"%s","cast_id":"%s","course_id":"%s","customer_name":"秘密顧客","remarks":"非公開備考"}
        """
            .formatted(
                LocalDate.now(), owner.enrollmentId(), course.getBody().path("id").asString());
    var created =
        rest.postForEntity(
            "/store/orders",
            confirmedRequest("/store/orders/preview", body, headers),
            JsonNode.class);
    assertThat(created.getStatusCode())
        .as("受注作成: %s", created.getBody())
        .isEqualTo(HttpStatus.CREATED);
    return created.getBody().path("id").asString();
  }

  private record Owner(HttpHeaders headers, String enrollmentId, long userId) {}

  private Owner owner() {
    String email = "remuneration-" + System.nanoTime() + "@kizuna.test";
    var user =
        users.save(
            PlatformUser.builder()
                .email(email)
                .password(passwords.encode(NEW_ACCOUNT_PASSWORD))
                .displayName("報酬検証本人")
                .userType(UserType.CAST)
                .enabled(true)
                .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of(STORE_B))
                .build());
    var enrollment = CastEnrollment.builder().build();
    enrollment.setStoreId(STORE_A);
    String id = saveEnrollmentFixture(enrollment, "報酬本人", user.getId()).getId();
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(loginWithPassword(email, NEW_ACCOUNT_PASSWORD));
    return new Owner(headers, id, user.getId());
  }

  private ResponseEntity<JsonNode> get(HttpHeaders headers, String path) {
    return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
  }
}
