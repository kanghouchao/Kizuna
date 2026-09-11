package com.kizuna.cast;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentStatus;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

class PlatformCastQueryIT extends CrossStoreTestSupport {
  @Autowired PlatformUserRepository users;
  @Autowired CastRepository people;
  @Autowired RoleRepository roles;
  @Autowired PermissionRepository permissions;
  @Autowired PasswordEncoder encoder;

  @Test
  void platformReadsPersonAndAllEnrollmentEpisodes() {
    Cast person = person("本人照会" + UUID.randomUUID(), "山田花子");
    var old = enrollment(person, STORE_A, CastEnrollmentStatus.WITHDRAWN, "旧源氏名");
    var current = enrollment(person, STORE_A, CastEnrollmentStatus.ENROLLED, "現在源氏名");
    var other = enrollment(person, STORE_B, CastEnrollmentStatus.SUSPENDED, "他店源氏名");
    HttpHeaders hq = hq();
    var detail = get("/platform/casts/" + person.getId(), hq);
    assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(detail.getBody().path("real_name").asString()).isEqualTo("山田花子");
    assertThat(detail.getBody().path("birth_date").asString()).isEqualTo("1995-04-03");
    var page = get("/platform/casts/" + person.getId() + "/enrollments?size=2", hq);
    assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(page.getBody().path("total_elements").asInt()).isEqualTo(3);
    assertThat(page.getBody().path("content").get(0).path("id").asString())
        .isEqualTo(other.getId());
    assertThat(page.getBody().path("content").get(1).path("id").asString())
        .isEqualTo(current.getId());
    var last = get("/platform/casts/" + person.getId() + "/enrollments?size=2&page=1", hq);
    assertThat(last.getBody().path("content").get(0).path("id").asString()).isEqualTo(old.getId());
    assertThat(last.getBody().path("content").get(0).path("ended_at").isNull()).isFalse();
    assertThat(page.getBody().toString())
        .doesNotContain("custom_fields", "birth_date", "platform_user_id");
    var store = get("/store/casts/" + current.getId(), storeHeaders(STORE_A));
    assertThat(store.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(store.getBody().toString())
        .doesNotContain("山田花子", "birth_date", "platform_user_id", "他店源氏名");
    assertThat(get("/store/casts/" + other.getId(), storeHeaders(STORE_A)).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void searchIsLiteralCaseInsensitiveAndPagedWithoutBirthDate() {
    String prefix = "Query" + UUID.randomUUID();
    Cast first = person(prefix + "%_A", null);
    Cast second = person(prefix + "%_B", null);
    person(prefix + "other", "別人");
    HttpHeaders headers = hq();
    var response =
        rest.exchange(
            "/platform/casts?search={search}&size=1",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class,
            "  " + prefix.toLowerCase() + "%_  ");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().path("total_elements").asInt()).isEqualTo(2);
    assertThat(response.getBody().path("content").get(0).path("id").asLong())
        .isEqualTo(first.getId());
    assertThat(response.getBody().path("content").get(0).has("real_name")).isTrue();
    assertThat(response.getBody().path("content").get(0).path("real_name").isNull()).isTrue();
    assertThat(response.getBody().toString()).doesNotContain("birth_date", "platform_user_id");
    var next =
        rest.exchange(
            "/platform/casts?search={search}&size=1&page=1",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class,
            prefix + "%_");
    assertThat(next.getBody().path("content").get(0).path("id").asLong()).isEqualTo(second.getId());
    String realName = "本名" + UUID.randomUUID();
    Cast named = person("表示" + UUID.randomUUID(), realName);
    var byName =
        rest.exchange(
            "/platform/casts?search={search}",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class,
            realName);
    assertThat(byName.getBody().path("content").get(0).path("id").asLong())
        .isEqualTo(named.getId());
    assertThat(get("/platform/casts", headers).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
            get("/platform/casts/" + first.getId() + "/enrollments", headers)
                .getBody()
                .path("content")
                .isEmpty())
        .isTrue();
  }

  @Test
  void rejectsMissingPermissionAndInvalidRequests() {
    HttpHeaders headers = hq();
    for (String path : new String[] {"", "/999999999", "/999999999/enrollments"}) {
      assertThat(get("/platform/casts" + path, new HttpHeaders()).getStatusCode())
          .isEqualTo(HttpStatus.UNAUTHORIZED);
      assertThat(get("/platform/casts" + path, storeHeaders(STORE_A)).getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
      HttpHeaders forged = storeHeaders(STORE_A);
      forged.set("X-Role", "platform");
      assertThat(get("/platform/casts" + path, forged).getStatusCode())
          .isEqualTo(HttpStatus.FORBIDDEN);
    }
    assertThat(get("/platform/casts/999999999", headers).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get("/platform/casts/999999999/enrollments", headers).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    for (String query : new String[] {"page=-1", "size=0", "size=101", "page=abc"}) {
      assertThat(get("/platform/casts?" + query, headers).getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(get("/platform/casts/999999999/enrollments?" + query, headers).getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Test
  void platformPermissionReadsBeyondAssignedStoresAndMenuStaysPlatformOnly() {
    var permission =
        permissions.findByCodeIn(Set.of(PermissionCode.CAST_PERSON_VIEW.name())).getFirst();
    var role =
        roles.save(
            Role.builder()
                .name("本人照会" + UUID.randomUUID())
                .permissionIds(Set.of(permission.getId()))
                .build());
    String email = UUID.randomUUID() + "@kizuna.test";
    users.save(
        PlatformUser.builder()
            .email(email)
            .password(encoder.encode(NEW_ACCOUNT_PASSWORD))
            .enabled(true)
            .displayName("照会者")
            .userType(UserType.STAFF)
            .roleIds(Set.of(role.getId()))
            .storeScopeType(StoreScopeType.SPECIFIC_STORES)
            .storeIds(Set.of(STORE_A))
            .build());
    Cast person = person("他店本人" + UUID.randomUUID(), null);
    var other = enrollment(person, STORE_B, CastEnrollmentStatus.ENROLLED, "他店");
    var headers = new HttpHeaders();
    headers.setBearerAuth(loginWithPassword(email, NEW_ACCOUNT_PASSWORD));
    var result = get("/platform/casts/" + person.getId() + "/enrollments", headers);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody().path("content").get(0).path("id").asString())
        .isEqualTo(other.getId());
    assertThat(get("/platform/menus/me", headers).getBody().toString()).contains("/platform/casts");
    assertThat(get("/store/menus/me", storeHeaders(STORE_A)).getBody().toString())
        .doesNotContain("/platform/casts", "キャスト在籍照会");
  }

  private Cast person(String displayName, String realName) {
    var user =
        users.save(
            PlatformUser.builder()
                .email(UUID.randomUUID() + "@kizuna.test")
                .password("unused-test-hash")
                .displayName(displayName)
                .userType(UserType.CAST)
                .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                .storeIds(Set.of(STORE_A))
                .roleIds(Set.of())
                .build());
    return people.save(
        Cast.builder()
            .platformUserId(user.getId())
            .realName(realName)
            .birthDate(LocalDate.of(1995, 4, 3))
            .build());
  }

  private CastEnrollment enrollment(
      Cast person, long store, CastEnrollmentStatus status, String name) {
    var enrollment =
        CastEnrollment.builder()
            .castId(person.getId())
            .status(status)
            .endedAt(status == CastEnrollmentStatus.WITHDRAWN ? OffsetDateTime.now() : null)
            .build();
    enrollment.setStoreId(store);
    return saveEnrollmentFixture(enrollment, name, null);
  }

  private HttpHeaders hq() {
    var headers = new HttpHeaders();
    headers.setBearerAuth(login("admin@kizuna.test"));
    return headers;
  }

  private ResponseEntity<JsonNode> get(String path, HttpHeaders headers) {
    return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
  }
}
