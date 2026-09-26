package com.kizuna.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.member.domain.MemberRepository;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.domain.ReceptionRoute;
import com.kizuna.point.domain.PointAllocation;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.shared.CrossStoreTestSupport;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

class CustomerListIT extends CrossStoreTestSupport {
  @Autowired private OrderRepository orders;
  @Autowired private MemberRepository members;
  @Autowired private PointEntryRepository entries;

  @Test
  void landmarkCanBeReadUpdatedAndClearedWithoutChangingOnOmission() {
    var created =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>(Map.of("name", "目印検証", "landmark", "駅の北口"), storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String path = "/store/customers/" + created.getBody().path("id").asString();
    assertThat(get(path).path("landmark").asString()).isEqualTo("駅の北口");
    assertThat(update(path, Map.of("landmark", "郵便局の隣")).path("landmark").asString())
        .isEqualTo("郵便局の隣");
    update(path, Map.of("name", "目印保持"));
    update(path, Collections.singletonMap("landmark", null));
    assertThat(get(path).path("landmark").asString()).isEqualTo("郵便局の隣");
    update(path, Map.of("landmark", ""));
    assertThat(get(path).path("landmark").asString()).isEmpty();
    assertThat(
            rest.exchange(
                    path,
                    HttpMethod.PUT,
                    new HttpEntity<>(Map.of("landmark", "あ".repeat(256)), storeHeaders(STORE_A)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            rest.exchange(
                    path,
                    HttpMethod.PUT,
                    new HttpEntity<>(Map.of("landmark", "越境"), managerHeaders(STORE_B)),
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            rest.exchange(
                    path, HttpMethod.GET, new HttpEntity<>(managerHeaders(STORE_B)), JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void visitsAndBalancesSortBeforePagingWithMissingValuesLastInBothDirections() {
    String marker = "list" + System.nanoTime();
    String low = create(marker + "甲");
    String high = create(marker + "乙");
    String tied = create(marker + "丙");
    String absent = create(marker + "丁");
    String invalidOnly = create(marker + "戊");
    var day = LocalDate.of(2026, 9, 1);
    order(low, day, OrderStatus.COMPLETED, false);
    order(high, day.plusDays(1), OrderStatus.COMPLETED, false);
    order(tied, day.plusDays(1), OrderStatus.COMPLETED, false);
    order(low, day.plusDays(8), OrderStatus.COMPLETED, true);
    order(low, day.plusDays(9), OrderStatus.CONFIRMED, false);
    order(invalidOnly, day.plusDays(10), OrderStatus.COMPLETED, true);
    link(low);
    long memberId = link(high);
    long tiedMemberId = link(tied);
    credit(memberId, 150, null, STORE_B);
    credit(tiedMemberId, 100, null, STORE_A);
    credit(memberId, 999, LocalDate.now().minusYears(1), STORE_A);
    var adjusted =
        rest.postForEntity(
            "/store/customers/" + high + "/point-adjustments",
            new HttpEntity<>(
                Map.of(
                    "delta", -50, "reason", "訂正", "idempotency_key", UUID.randomUUID().toString()),
                managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(adjusted.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(adjusted.getBody().path("balance").asLong()).isEqualTo(100);
    long releasedMember = link(absent);
    credit(releasedMember, 500, null, STORE_A);
    assertThat(releaseMemberLink(absent, managerHeaders(STORE_A)).getStatusCode().is2xxSuccessful())
        .isTrue();
    var equal = List.of(high, tied).stream().sorted().toList();
    var missing = List.of(absent, invalidOnly).stream().sorted().toList();
    for (String key : List.of("lastVisitDate", "pointBalance")) {
      assertThat(pageIds(marker, key + ",asc"))
          .containsExactly(low, equal.get(0), equal.get(1), missing.get(0), missing.get(1));
      assertThat(pageIds(marker, key + ",desc"))
          .containsExactly(equal.get(0), equal.get(1), low, missing.get(0), missing.get(1));
    }
    var content =
        get("/store/customers?search=" + marker + "&sort=lastVisitDate,asc").path("content");
    assertThat(content.get(0).path("last_visit_date").asString()).isEqualTo("2026-09-01");
    assertThat(content.get(0).path("point_balance").asLong()).isZero();
    assertThat(content.get(0).path("member_linked").asBoolean()).isTrue();
    for (int i = 3; i < 5; i++) {
      assertThat(content.get(i).has("last_visit_date")).isFalse();
      assertThat(content.get(i).has("point_balance")).isFalse();
      assertThat(content.get(i).path("member_linked").asBoolean()).isFalse();
    }
    assertThat(get("/store/customers/" + high + "/member-point-balance").path("balance").asLong())
        .isEqualTo(100);
    assertThat(
            rest.exchange(
                    "/store/customers?search=" + marker + "&sort=pointBalance,desc",
                    HttpMethod.GET,
                    new HttpEntity<>(managerHeaders(STORE_B)),
                    JsonNode.class)
                .getBody()
                .path("content"))
        .isEmpty();
  }

  @Test
  void listBalanceMatchesLedgerForExpiryTodayReversedUseAndLongTotals() {
    String marker = "parity" + System.nanoTime();
    String customer = create(marker);
    long member = link(customer);
    var today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
    var source = credit(member, 100, today, STORE_B);
    credit(member, 99, today.minusDays(1), STORE_A);
    credit(member, 1_500_000_000, null, STORE_A);
    credit(member, 1_500_000_000, null, STORE_B);
    String orderId = order(customer, today, OrderStatus.CONFIRMED, false);
    var use =
        entries.save(
            PointEntry.useForOrder(
                member,
                orderId,
                STORE_A,
                50,
                List.of(PointAllocation.of(source.getId(), 50)),
                null));
    entries.save(PointEntry.reverseUse(use, "利用取消", null));
    assertThat(
            get("/store/customers/" + customer + "/member-point-balance").path("balance").asLong())
        .isEqualTo(3_000_000_100L);
    var row =
        get("/store/customers?search=" + marker + "&sort=pointBalance,desc").path("content").get(0);
    assertThat(row.path("point_balance").asLong()).isEqualTo(3_000_000_100L);
  }

  private List<String> pageIds(String marker, String sort) {
    var ids = new ArrayList<String>();
    for (int page = 0; page < 3; page++) {
      var result =
          get("/store/customers?search=" + marker + "&size=2&page=" + page + "&sort=" + sort);
      assertThat(result.path("total_elements").asInt()).isEqualTo(5);
      for (var row : result.path("content")) ids.add(row.path("id").asString());
    }
    return ids;
  }

  private String create(String name) {
    var response =
        rest.postForEntity(
            "/store/customers",
            new HttpEntity<>(Map.of("name", name), storeHeaders(STORE_A)),
            JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody().path("id").asString();
  }

  private JsonNode get(String path) {
    var response =
        rest.exchange(
            path, HttpMethod.GET, new HttpEntity<>(storeHeaders(STORE_A)), JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  private JsonNode update(String path, Map<String, ?> body) {
    var response =
        rest.exchange(
            path, HttpMethod.PUT, new HttpEntity<>(body, storeHeaders(STORE_A)), JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  private long link(String customerId) {
    var response =
        rest.postForEntity(
            "/platform/members",
            Map.of(
                "email",
                UUID.randomUUID() + "@example.test",
                "password",
                UUID.randomUUID().toString(),
                "display_name",
                "一覧検証"),
            JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String code = response.getBody().path("member_code").asString();
    var linked =
        rest.postForEntity(
            "/store/customers/" + customerId + "/member-link",
            new HttpEntity<>(Map.of("member_code", code), managerHeaders(STORE_A)),
            JsonNode.class);
    assertThat(linked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return members.findByMemberCode(code).orElseThrow().getId();
  }

  private PointEntry credit(long memberId, int amount, LocalDate expires, long storeId) {
    return entries.save(
        PointEntry.manualAdjust(
            memberId,
            storeId,
            amount,
            "一覧検証",
            expires,
            List.of(),
            null,
            UUID.randomUUID().toString()));
  }

  private String order(String customerId, LocalDate date, OrderStatus status, boolean invalidated) {
    var order =
        Order.builder()
            .customerId(customerId)
            .businessDate(date)
            .status(OrderStatus.CONFIRMED)
            .course(courseFixture(STORE_A, 100))
            .pax(1)
            .receptionRoute(ReceptionRoute.PHONE)
            .build();
    order.setStoreId(STORE_A);
    if (status == OrderStatus.COMPLETED) order.completeWith(0, 0);
    if (invalidated) order.invalidateCompletion();
    return orders.save(order).getId();
  }
}
