package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCourse;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.service.domain.ServiceItem;
import com.kizuna.service.domain.ServiceItemRepository;
import com.kizuna.service.domain.ServiceKind;
import com.kizuna.service.domain.ServiceRevision;
import com.kizuna.service.domain.ServiceRevisionRepository;
import com.kizuna.service.domain.ServiceTerms;
import com.kizuna.shared.CrossStoreTestSupport;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

/** 集合作用域の実データ非漏洩を PostgreSQL 上の一覧応答で検証する。 */
class PlatformOrderScopeIT extends CrossStoreTestSupport {

  private static final String PASSWORD = "pass";

  /** ALL_STORES の HQ 管理者シード（seed/04-platform-admin.yaml）。 */
  private static final String SEED_EMAIL = "admin@kizuna.test";

  private static final String SPECIFIC_EMAIL = "scope-manager@kizuna.test";
  private static final String CAST_EMAIL = "scope-cast@kizuna.test";

  private static final String STORE_B_DOMAIN = "platform-scope-it.kizuna.test";

  private static final String MARKER_A_REMARKS = "SCOPE_MARKER_A";
  private static final String CANARY_B_REMARKS = "SCOPE_LEAK_CANARY_B";

  /** マーカー受注が一覧の先頭ページに現れるよう、他 IT の受注より新しい営業日を使う。店舗ごとに異なる値とし受注を識別する。 */
  private static final LocalDate MARKER_A_DATE = LocalDate.of(2999, 1, 1);

  private static final LocalDate CANARY_B_DATE = LocalDate.of(2999, 1, 2);

  @Autowired private OrderRepository orderRepository;
  @Autowired private StoreRepository storeRepository;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RoleRepository roleRepository;

  /** 保存後に採番された第二店舗の実 id。 */
  private long storeBId;

  @Autowired private ServiceItemRepository services;
  @Autowired private ServiceRevisionRepository revisions;

  private OrderCourse insertCourse(long storeId) {
    var item = ServiceItem.create(new ServiceTerms(ServiceKind.COURSE, "削除検証", 60, null, 100, 0));
    item.setStoreId(storeId);
    services.saveAndFlush(item);
    var revision = ServiceRevision.record(item, null, 3L);
    revision.setStoreId(storeId);
    revisions.saveAndFlush(revision);
    return new OrderCourse(
        item.getId(),
        revision.getId(),
        1,
        "削除検証",
        60,
        100,
        0,
        "CURRENT_SETTING",
        OffsetDateTime.now());
  }

  @BeforeEach
  void prepareScopeFixture() {
    Store storeB =
        storeRepository
            .findByDomain(STORE_B_DOMAIN)
            .orElseGet(() -> storeRepository.save(new Store("集合作用域IT第二店舗", STORE_B_DOMAIN, null)));
    storeBId = storeB.getId();

    ensureMarkerOrder(STORE_A, MARKER_A_DATE, MARKER_A_REMARKS);
    ensureMarkerOrder(storeBId, CANARY_B_DATE, CANARY_B_REMARKS);

    ensurePlatformUser(
        SPECIFIC_EMAIL,
        UserType.STAFF,
        roleIdsOf("店長"),
        StoreScopeType.SPECIFIC_STORES,
        Set.of(STORE_A));
    ensurePlatformUser(CAST_EMAIL, UserType.CAST, Set.of(), StoreScopeType.ALL_STORES, Set.of());
  }

  /** リポジトリ直挿（テストスレッドは @StoreScoped を経由せず storeFilter が無効なので他店舗にも書ける）。 */
  private void ensureMarkerOrder(long storeId, LocalDate businessDate, String remarks) {
    boolean exists =
        orderRepository.findAll().stream()
            .anyMatch(
                o ->
                    o.getStoreId() != null
                        && storeId == o.getStoreId()
                        && businessDate.equals(o.getBusinessDate()));
    if (exists) {
      return;
    }
    Order order =
        Order.builder()
            .course(insertCourse(storeId))
            .remarks(remarks)
            .businessDate(businessDate)
            .status(OrderStatus.CONFIRMED)
            .build();
    order.setStoreId(storeId);
    orderRepository.save(order);
  }

  private void ensurePlatformUser(
      String email,
      UserType userType,
      Set<Long> roleIds,
      StoreScopeType scopeType,
      Set<Long> storeIds) {
    platformUserRepository
        .findByEmail(email)
        .orElseGet(
            () ->
                platformUserRepository.save(
                    PlatformUser.builder()
                        .email(email)
                        .password(passwordEncoder.encode(PASSWORD))
                        .displayName("集合作用域IT " + userType.name())
                        .enabled(true)
                        .userType(userType)
                        .roleIds(roleIds)
                        .storeScopeType(scopeType)
                        .storeIds(storeIds)
                        .build()));
  }

  /** 種子の既定束を名称で解決する（束はデータ — id を決め打ちしない）。 */
  private Set<Long> roleIdsOf(String roleName) {
    return Set.of(roleRepository.findByName(roleName).orElseThrow().getId());
  }

  private String platformToken(String email, String password) {
    ResponseEntity<JsonNode> res =
        rest.postForEntity(
            "/platform/login",
            new HttpEntity<>(
                String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, password),
                jsonHeaders()),
            JsonNode.class);
    assertThat(res.getStatusCode()).as("前提: 平台ログインが成功すること").isEqualTo(HttpStatus.OK);
    String t = res.getBody().path("token").asString();
    assertThat(t).isNotBlank();
    return t;
  }

  private static HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private static HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  private static HttpHeaders bearerJson(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private long orderCountForStore(long storeId) {
    return orderRepository.findAll().stream()
        .filter(o -> o.getStoreId() != null && storeId == o.getStoreId())
        .count();
  }

  @Test
  @DisplayName("SPECIFIC{1} の一覧は授権店舗(store_id=1)のみを返し、正向マーカーを含むこと(AC1)")
  void specificScopeListReturnsOnlyAuthorizedStores() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/orders?size=500",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(SPECIFIC_EMAIL, PASSWORD))),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode content = res.getBody().path("content");
    assertThat(content).isNotEmpty();
    content.forEach(
        node ->
            assertThat(node.path("store_id").asLong()).as("授権外の店舗が一覧に現れないこと").isEqualTo(STORE_A));

    boolean hasMarker = false;
    for (JsonNode node : content) {
      if (MARKER_A_DATE.toString().equals(node.path("business_date").asString())) {
        hasMarker = true;
        break;
      }
    }
    assertThat(hasMarker).as("授権店舗の正向マーカーが一覧に含まれること").isTrue();

    assertThat(res.getBody().path("total_elements").asLong())
        .as("total_elements が授権店舗(store A)の実件数と一致し、集合外店舗の件数を含まないこと")
        .isEqualTo(orderCountForStore(STORE_A));
  }

  @Test
  @DisplayName("集合外店舗の実データ(store_id/カナリア)が応答の生ボディに一切現れないこと(AC2)")
  void outOfSetRealDataNeverAppearsInResponse() {
    ResponseEntity<String> res =
        rest.exchange(
            "/platform/orders?size=500",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(SPECIFIC_EMAIL, PASSWORD))),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody())
        .as("授権外店舗の店舗ID・カナリア文字列が生ボディに現れないこと")
        .doesNotContain(CANARY_B_REMARKS)
        .doesNotContain("\"store_id\":" + storeBId);
  }

  @Test
  @DisplayName("ALL_STORES(seed HQ) の一覧は両店のマーカーが現れ、store_id 集合に {1, B} を含むこと(機構の正側)")
  void allStoresScopeSeesEveryStore() {
    ResponseEntity<JsonNode> res =
        rest.exchange(
            "/platform/orders?size=500",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(SEED_EMAIL, PASSWORD))),
            JsonNode.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode content = res.getBody().path("content");
    List<String> businessDates = new ArrayList<>();
    Set<Long> storeIds = new HashSet<>();
    content.forEach(
        node -> {
          businessDates.add(node.path("business_date").asString());
          storeIds.add(node.path("store_id").asLong());
        });

    assertThat(businessDates).contains(MARKER_A_DATE.toString(), CANARY_B_DATE.toString());
    assertThat(storeIds).contains(STORE_A, storeBId);
  }

  @Test
  @DisplayName("平台の受注作成入口は提供せず、受注を作成しないこと")
  void platformCreationIsUnavailable() {
    long before = orderCountForStore(STORE_A);
    var response =
        rest.postForEntity(
            "/platform/orders",
            new HttpEntity<>("{}", bearerJson(platformToken(SEED_EMAIL, PASSWORD))),
            JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(orderCountForStore(STORE_A)).isEqualTo(before);
  }

  @Test
  @DisplayName("CAST ロールで /platform/orders は 403(@PreAuthorize の役割線)")
  void castRoleIsRejectedOnPlatformOrders() {
    ResponseEntity<String> res =
        rest.exchange(
            "/platform/orders",
            HttpMethod.GET,
            new HttpEntity<>(bearer(platformToken(CAST_EMAIL, PASSWORD))),
            String.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
