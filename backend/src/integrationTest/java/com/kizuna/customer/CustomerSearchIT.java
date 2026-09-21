package com.kizuna.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.CrossStoreTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

class CustomerSearchIT extends CrossStoreTestSupport {
  @Test
  void phoneFragmentsStartingWithZeroMatchInsideNumbers() {
    var contacts = List.of(Map.of("type", "PHONE", "value", "09012340678"));
    String first = create(STORE_A, "電話断片甲", contacts);
    String second = create(STORE_A, "電話断片乙", contacts);

    var rows = get("/store/customers?search=0678&size=2000", STORE_A).path("content");
    var matchedIds = new ArrayList<String>();
    for (var row : rows) {
      if (!List.of(first, second).contains(row.path("id").asString())) continue;
      matchedIds.add(row.path("id").asString());
      assertThat(row.path("matched_contacts").get(0).path("type").asString()).isEqualTo("PHONE");
      assertThat(row.path("matched_contacts").get(0).path("value").asString())
          .isEqualTo("+819012340678");
    }
    assertThat(matchedIds).containsExactlyInAnyOrder(first, second);

    var groups = get("/store/customers/duplicates?type=PHONE&search=0678", STORE_A).path("content");
    assertThat(groups).hasSize(1);
    assertThat(groups.get(0).path("matched_value").asString()).isEqualTo("+819012340678");
    assertThat(groups.get(0).path("total").asInt()).isEqualTo(2);
  }

  @Test
  void searchReportsNonPreferredMatchesWithoutFoldingLineOrEmailLocalCase() {
    String marker = "Search" + System.nanoTime();
    String id =
        create(
            STORE_A,
            "検索対象",
            List.of(
                Map.of("type", "PHONE", "value", "09012345678"),
                Map.of("type", "EMAIL", "value", marker + "@EXAMPLE.COM"),
                Map.of("type", "LINE", "value", marker)));
    var phone = get("/store/customers?search=090-1234-5678", STORE_A).path("content");
    JsonNode row = null;
    for (var candidate : phone) if (candidate.path("id").asString().equals(id)) row = candidate;
    assertThat(row).isNotNull();
    assertThat(row.path("matched_contacts").get(0).path("value").asString())
        .isEqualTo("+819012345678");
    assertThat(
            get("/store/customers?search=" + marker + "@EXAMPLE.COM", STORE_A)
                .path("content")
                .get(0)
                .path("matched_contacts")
                .get(0)
                .path("type")
                .asString())
        .isEqualTo("EMAIL");
    assertThat(
            get("/store/customers?search=" + marker.toLowerCase() + "@example.com", STORE_A)
                .path("content"))
        .isEmpty();
    assertThat(get("/store/customers?search=" + marker.toLowerCase(), STORE_A).path("content"))
        .isEmpty();
    var line = get("/store/customers?search=" + marker, STORE_A).path("content").get(0);
    assertThat(line.path("matched_contacts").size()).isEqualTo(2);
    assertThat(get("/store/customers?search=" + marker, STORE_B).path("content")).isEmpty();
  }

  @Test
  void duplicatesIncludeAllTypesAndCountCustomersRatherThanContactRows() {
    String value = "Dup" + System.nanoTime() + "@example.com";
    var contacts =
        List.of(
            Map.of("type", "EMAIL", "value", value),
            Map.of("type", "LINE", "value", value),
            Map.of("type", "EMAIL", "value", value));
    String first = create(STORE_A, "候補一", contacts);
    String second = create(STORE_A, "候補二", contacts);
    create(STORE_B, "別店舗", contacts);
    var page = get("/store/customers/duplicates?search=" + value + "&size=1", STORE_A);
    assertThat(page.path("content").size()).isEqualTo(1);
    var group = page.path("content").get(0);
    assertThat(group.path("matched_type").asString()).isEqualTo("EMAIL");
    assertThat(group.path("total").asInt()).isEqualTo(2);
    assertThat(group.path("customers").size()).isEqualTo(2);
    var next =
        get(
            "/store/customers/duplicates?search="
                + value
                + "&size=1&cursor="
                + page.path("next_cursor").asString(),
            STORE_A);
    assertThat(next.path("content").get(0).path("matched_type").asString()).isEqualTo("LINE");
    assertThat(next.has("next_cursor")).isFalse();
    var members =
        get("/store/customers/duplicates/customers?type=EMAIL&value=" + value + "&size=1", STORE_A);
    assertThat(members.path("content").get(0).path("id").asString()).isEqualTo(first);
    var more =
        get(
            "/store/customers/duplicates/customers?type=EMAIL&value="
                + value
                + "&size=1&cursor="
                + members.path("next_cursor").asString(),
            STORE_A);
    assertThat(more.path("content").get(0).path("id").asString()).isEqualTo(second);
    assertThat(more.has("next_cursor")).isFalse();
  }

  @Test
  void removesDeletedContactsAndMergedCustomersFromBothCandidateEndpoints() {
    String value = "removed" + System.nanoTime();
    var contacts = List.of(Map.of("type", "LINE", "value", value));
    String a = create(STORE_A, "残す顧客", contacts);
    String b = create(STORE_A, "統合する顧客", contacts);
    String c = create(STORE_A, "削除する連絡先", contacts);
    String contactId =
        get("/store/customers/" + c + "/contacts", STORE_A)
            .path("content")
            .get(0)
            .path("id")
            .asString();
    assertThat(
            rest.exchange(
                    "/store/customers/" + c + "/contacts/" + contactId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(managerHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(
            get("/store/customers/duplicates?type=LINE&search=" + value, STORE_A)
                .path("content")
                .get(0)
                .path("total")
                .asInt())
        .isEqualTo(2);
    assertThat(
            rest.postForEntity(
                    "/store/customers/" + a + "/merges",
                    new HttpEntity<>(Map.of("merged_customer_id", b), managerHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(get("/store/customers/duplicates?search=" + value, STORE_A).path("content"))
        .isEmpty();
    var rows =
        get("/store/customers/duplicates/customers?type=LINE&value=" + value, STORE_A)
            .path("content");
    assertThat(rows.size()).isEqualTo(1);
    assertThat(rows.get(0).path("id").asString()).isEqualTo(a);
    assertThat(get("/store/customers?search=" + value, STORE_A).path("total_elements").asInt())
        .isEqualTo(1);
    assertThat(
            get("/store/customers/duplicates/customers?type=LINE&value=" + value, STORE_B)
                .path("content"))
        .isEmpty();
  }

  @Test
  void largeGroupsRemainFullyReachableAndRejectInvalidCursorsAndMissingParameters() {
    String value = "large" + System.nanoTime();
    var ids = new ArrayList<String>();
    for (int i = 0; i < 21; i++)
      ids.add(create(STORE_A, "大きい組" + i, List.of(Map.of("type", "LINE", "value", value))));
    var group = get("/store/customers/duplicates?search=" + value, STORE_A).path("content").get(0);
    assertThat(group.path("total").asInt()).isEqualTo(21);
    assertThat(group.path("customers")).isEmpty();
    var first = get("/store/customers/duplicates/customers?type=LINE&value=" + value, STORE_A);
    assertThat(first.path("content").size()).isEqualTo(20);
    var last =
        get(
            "/store/customers/duplicates/customers?type=LINE&value="
                + value
                + "&cursor="
                + first.path("next_cursor").asString(),
            STORE_A);
    assertThat(last.path("content").size()).isEqualTo(1);
    assertThat(last.path("content").get(0).path("id").asString()).isEqualTo(ids.get(20));
    assertThat(last.has("next_cursor")).isFalse();
    for (String suffix :
        List.of(
            "?cursor=bad",
            "?size=0",
            "?size=2001",
            "/customers?type=LINE&value=x&size=-1",
            "/customers?type=LINE&value=x&size=2001",
            "?type=INVALID",
            "/customers?type=LINE",
            "/customers?value=x",
            "/customers?type=PHONE&value=bad",
            "/customers?type=LINE&value=x&cursor=bad")) {
      assertThat(
              rest.exchange(
                      "/store/customers/duplicates" + suffix,
                      HttpMethod.GET,
                      new HttpEntity<>(managerHeaders(STORE_A)),
                      JsonNode.class)
                  .getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Test
  void duplicatesUseNormalizedPhoneAndPreserveEmailLocalAndLineCase() {
    String phone = "0901" + String.format("%07d", System.nanoTime() % 10000000);
    String marker = "Case" + System.nanoTime();
    create(
        STORE_A,
        "表記一",
        List.of(
            Map.of("type", "PHONE", "value", phone),
            Map.of("type", "EMAIL", "value", marker + "@EXAMPLE.COM"),
            Map.of("type", "LINE", "value", marker)));
    create(
        STORE_A,
        "表記二",
        List.of(
            Map.of("type", "PHONE", "value", "+81" + phone.substring(1)),
            Map.of("type", "EMAIL", "value", marker + "@example.com"),
            Map.of("type", "LINE", "value", " " + marker + " ")));
    create(
        STORE_A,
        "別の値",
        List.of(
            Map.of("type", "EMAIL", "value", marker.toLowerCase() + "@example.com"),
            Map.of("type", "LINE", "value", marker.toLowerCase())));
    var phoneGroup =
        get("/store/customers/duplicates?search=" + phone + "&type=PHONE", STORE_A)
            .path("content")
            .get(0);
    assertThat(phoneGroup.path("matched_value").asString()).isEqualTo("+81" + phone.substring(1));
    assertThat(phoneGroup.path("total").asInt()).isEqualTo(2);
    var groups = get("/store/customers/duplicates?search=" + marker, STORE_A).path("content");
    assertThat(groups.size()).isEqualTo(2);
    for (var group : groups) assertThat(group.path("total").asInt()).isEqualTo(2);
    assertThat(
            get("/store/customers/duplicates?search=" + marker.toLowerCase(), STORE_A)
                .path("content"))
        .isEmpty();
  }

  @Test
  void emailDomainFragmentsIgnoreCaseWithoutFoldingTheLocalPart() {
    String domain = "domain" + System.nanoTime() + ".example.com";
    var contacts = List.of(Map.of("type", "EMAIL", "value", "LocalOnly@" + domain));
    create(STORE_A, "域名検索一", contacts);
    create(STORE_A, "域名検索二", contacts);
    var list = get("/store/customers?search=" + domain.toUpperCase(Locale.ROOT), STORE_A);
    assertThat(list.path("total_elements").asInt()).isEqualTo(2);
    assertThat(list.path("content").get(0).path("matched_contacts").size()).isEqualTo(1);
    var groups =
        get(
            "/store/customers/duplicates?type=EMAIL&search=" + domain.toUpperCase(Locale.ROOT),
            STORE_A);
    assertThat(groups.path("content").size()).isEqualTo(1);
    assertThat(
            get("/store/customers/duplicates?type=EMAIL&search=localonly", STORE_A).path("content"))
        .isEmpty();
    assertThat(
            get("/store/customers/duplicates?type=EMAIL&search=localonly@" + domain, STORE_A)
                .path("content"))
        .isEmpty();
  }

  private String create(long store, String name, List<Map<String, String>> contacts) {
    var response =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>(Map.of("name", name, "contacts", contacts), managerHeaders(store)),
            JsonNode.class);
    assertThat(response.getStatusCode()).as("%s", response.getBody()).isEqualTo(HttpStatus.CREATED);
    return response.getBody().path("id").asString();
  }

  private JsonNode get(String path, long store) {
    var response =
        rest.exchange(
            path, HttpMethod.GET, new HttpEntity<>(managerHeaders(store)), JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }
}
