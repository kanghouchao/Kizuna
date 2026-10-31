package com.kizuna.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCourse;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.revieworigin.ReviewOriginLookup;
import com.kizuna.review.api.ReviewController;
import com.kizuna.review.application.ReviewService;
import com.kizuna.service.domain.ServiceItem;
import com.kizuna.service.domain.ServiceItemRepository;
import com.kizuna.service.domain.ServiceKind;
import com.kizuna.service.domain.ServiceRevision;
import com.kizuna.service.domain.ServiceRevisionRepository;
import com.kizuna.service.domain.ServiceTerms;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.shared.storescope.CurrentStoreArgumentResolver;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.shared.web.WebBindingConfig;
import com.kizuna.store.StoreShortNames;
import com.kizuna.user.application.BusinessAudit;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReviewIntegrationTest {
  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private Long currentStore;
  private MockMvc mvc;
  private final JsonMapper json =
      JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
  private Long storeId, otherStore, actorId, roleId;
  private String email;
  private Authentication auth;
  private SettableClock clock;

  @BeforeAll
  void connect() {
    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                IntegrationTestDatabase.springDataSourceProperties(ReviewIntegrationTest.class))
            .properties(
                Map.ofEntries(
                    Map.entry("spring.config.name", "review-test"),
                    Map.entry(
                        "spring.liquibase.change-log",
                        "classpath:db/changelog/db.changelog-master.yaml"),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                    Map.entry("spring.jpa.open-in-view", "false"),
                    Map.entry("logging.level.root", "WARN")))
            .run();
    jdbc = context.getBean(JdbcTemplate.class);
    clock = context.getBean(SettableClock.class);
    mvc =
        MockMvcBuilders.standaloneSetup(context.getBean(ReviewController.class))
            .setCustomArgumentResolvers(argumentResolvers())
            .defaultRequest(
                get("/")
                    .with(
                        request -> {
                          if (currentStore != null)
                            request.addHeader("X-Store-ID", currentStore.toString());
                          return request;
                        }))
            .setControllerAdvice(new CommonExceptionHandler())
            .setMessageConverters(new JacksonJsonHttpMessageConverter(json))
            .build();
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
    currentStore = null;
    clock.set(null);
  }

  @BeforeEach
  void fixtures() {
    String suffix = UUID.randomUUID().toString();
    email = suffix + "@example.invalid";
    storeId =
        jdbc.queryForObject(
            "insert into t_stores(name,short_name,domain) values ('受付検証',?,?) returning id",
            Long.class,
            StoreShortNames.next(),
            suffix + ".invalid");
    otherStore =
        jdbc.queryForObject(
            "insert into t_stores(name,short_name,domain) values ('別店舗',?,?) returning id",
            Long.class,
            StoreShortNames.next(),
            suffix + "-other.invalid");
    actorId =
        jdbc.queryForObject(
            "insert into t_users(email,display_name,user_type,store_scope_type) values (?,'受付担当','STAFF','ALL_STORES') returning id",
            Long.class,
            email);
    roleId =
        jdbc.queryForObject(
            "insert into t_roles(name,is_system) values (?,false) returning id",
            Long.class,
            "受付" + suffix);
    jdbc.update(
        "insert into t_role_permissions(role_id,permission_id) select ?,id from t_permissions where code in ('REVIEW_VIEW','REVIEW_MANAGE','REVIEW_MODERATE','ORDER_MANAGE')",
        roleId);
    jdbc.update("insert into t_user_roles(user_id,role_id) values (?,?)", actorId, roleId);
    auth =
        new UsernamePasswordAuthenticationToken(
            email,
            "unused",
            List.of(
                new SimpleGrantedAuthority("PERM_REVIEW_VIEW"),
                    new SimpleGrantedAuthority("PERM_REVIEW_MANAGE"),
                new SimpleGrantedAuthority("PERM_REVIEW_MODERATE"),
                    new SimpleGrantedAuthority("PERM_ORDER_MANAGE")));
    SecurityContextHolder.getContext().setAuthentication(auth);
    currentStore = storeId;
  }

  @Test
  void staffRecordsImmutableIntakeWithoutClaimingCustomerVerification() throws Exception {
    var result =
        mvc.perform(
                post("/reviews")
                    .principal(auth)
                    .contentType("application/json")
                    .content(
                        """
        {"body":"  原文\\r\\n次の行  ","display_name":"  匿名希望  ","received_via":"PAPER",
        "received_at":"2026-01-01T09:00:00+09:00","dedupe_key":"first-receipt"}
        """))
            .andExpect(status().isCreated())
            .andReturn();
    var row = json.readTree(result.getResponse().getContentAsString()).get("review");
    assertThat(row.get("intake_source").asString()).isEqualTo("STAFF_RECORDED");
    assertThat(row.get("body").asString()).isEqualTo("  原文\n次の行  ");
    assertThat(row.get("display_name").asString()).isEqualTo("匿名希望");
    assertThat(row.get("status").asString()).isEqualTo("PENDING");
    assertThat(row.get("permission_status").asString()).isEqualTo("NOT_GRANTED");
    assertThat(row.get("publication_eligible").asBoolean()).isFalse();
    assertThat(row.has("verified_customer")).isFalse();
    assertThat(row.get("publication_connection").asString()).isEqualTo("NOT_CONFIGURED");
  }

  @Test
  void sameReceiptRequestReplaysOneRecordAndRejectsDifferentStoredContent() throws Exception {
    String input =
        """
        {"body":"原文","display_name":"表示名","received_via":"VERBAL",
        "received_at":"2026-01-01T00:00:00Z","dedupe_key":"replayed-receipt"}
        """;
    var first =
        json.readTree(
            mvc.perform(
                    post("/reviews").principal(auth).contentType("application/json").content(input))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    var replay =
        json.readTree(
            mvc.perform(
                    post("/reviews")
                        .principal(auth)
                        .contentType("application/json")
                        .content(input.replace("表示名", "  表示名  ")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(replay.get("review").get("id")).isEqualTo(first.get("review").get("id"));
    assertThat(replay.get("operation").get("id")).isEqualTo(first.get("operation").get("id"));
    assertThat(replay.get("operation").get("replayed").asBoolean()).isTrue();
    mvc.perform(
            post("/reviews")
                .principal(auth)
                .contentType("application/json")
                .content(input.replace("原文", "異なる原文")))
        .andExpect(status().isConflict());
  }

  @Test
  void approvalIsIndependentOfPublicationPermissionAndReplayReflectsWithdrawal() throws Exception {
    var receipt =
        send(
            "",
            """
        {"body":"承認対象","received_via":"PAPER","received_at":"2026-01-01T00:00:00Z","dedupe_key":"approval-intake"}
        """,
            201);
    String id = receipt.get("review").get("id").asString();
    String decision =
        """
        {"version":0,"decision":"APPROVE","reason":"  内容確認  ","dedupe_key":"approval"}
        """;
    var approved = send("/" + id + "/decisions", decision, 200);
    assertThat(approved.get("review").get("status").asString()).isEqualTo("APPROVED");
    assertThat(approved.get("review").get("publication_eligible").asBoolean()).isFalse();
    assertThat(approved.get("review").get("publication_blockers").toString())
        .isEqualTo("[\"NO_PERMISSION\"]");
    send(
        "/" + id + "/withdrawals",
        """
        {"version":1,"reason":"受付取消","dedupe_key":"withdrawal"}
        """,
        200);
    var replay = send("/" + id + "/decisions", decision, 200);
    assertThat(replay.get("operation").get("id")).isEqualTo(approved.get("operation").get("id"));
    assertThat(replay.get("operation").get("committed_version").asLong()).isEqualTo(1);
    assertThat(replay.get("review").get("version").asLong()).isEqualTo(2);
    assertThat(replay.get("review").get("status").asString()).isEqualTo("WITHDRAWN");
    send("/" + id + "/decisions", decision.replace("approval\"", "another\""), 409);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_review_history where review_id=?", Integer.class, id))
        .isEqualTo(3);
    jdbc.update(
        "delete from t_role_permissions where role_id=? and permission_id=(select id from t_permissions where code='REVIEW_MODERATE')",
        roleId);
    send("/" + id + "/decisions", decision, 403);
  }

  @Test
  void permissionAndApprovalGateNarrowStoreBoundPublicationAndRevocationIsImmediate()
      throws Exception {
    var intake =
        send(
            "",
            """
        {"body":"  公開を許可した原文  ","display_name":"名前","received_via":"ELECTRONIC","received_at":"2026-01-01T00:00:00Z","dedupe_key":"permission-intake"}
        """,
            201);
    String id = intake.get("review").get("id").asString();
    send(
        "/" + id + "/decisions",
        """
        {"version":0,"decision":"APPROVE","reason":"確認","dedupe_key":"permission-approve"}
        """,
        200);
    String grant =
        """
        {"version":1,"basis_type":"WRITTEN","granted_at":"2026-01-02T09:00:00+09:00","evidence_note":"  書面の記録  ","dedupe_key":"permission-grant"}
        """;
    var granted = send("/" + id + "/permissions", grant, 201);
    assertThat(granted.get("review").get("publication_eligible").asBoolean()).isTrue();
    assertThat(granted.get("review").get("permission").get("scope").asString())
        .isEqualTo("OWN_STORE_WEBSITE");
    assertThat(granted.get("review").get("permission").get("evidence_note").asString())
        .isEqualTo("書面の記録");
    var statistics =
        context.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
    read("", 200);
    read("/" + id, 200);
    read("/" + id + "/history", 200);
    for (String entity : List.of("ReviewRecord", "ReviewPermission", "ReviewHistory"))
      assertThat(
              statistics.getEntityStatistics("com.kizuna.review.domain." + entity).getLoadCount())
          .as("読取APIは%s全体をロードしない", entity)
          .isZero();
    publish(
        id,
        """
        {"version":2,"published":true,"dedupe_key":"permission-publish"}
        """,
        200);
    var shown = publicReviews(storeId, "").get("content");
    assertThat(shown).hasSize(1);
    assertThat(shown.get(0).get("body").asString()).isEqualTo("  公開を許可した原文  ");
    assertThat(shown.get(0).get("display_name").asString()).isEqualTo("名前");
    assertThat(shown.get(0).get("received_on").asString()).isEqualTo("2026-01-01");
    assertThat(shown.get(0).properties())
        .extracting(Map.Entry::getKey)
        .containsExactlyInAnyOrder("display_name", "body", "received_on");
    assertThat(publicReviews(otherStore, "").get("content")).isEmpty();
    var revoked =
        send(
            "/" + id + "/permission-revocations",
            """
        {"version":3,"withdrawal_received_at":"2026-01-03T00:00:00Z","reason":"本人から撤回","dedupe_key":"permission-revoke"}
        """,
            200);
    assertThat(revoked.get("review").get("status").asString()).isEqualTo("APPROVED");
    assertThat(revoked.get("review").get("permission_status").asString()).isEqualTo("REVOKED");
    assertThat(publicReviews(storeId, "").get("content")).isEmpty();
    var replay = send("/" + id + "/permissions", grant, 200);
    assertThat(replay.get("review").get("permission_status").asString()).isEqualTo("REVOKED");
    assertThat(replay.get("review").get("permission").get("revocation").get("reason").asString())
        .isEqualTo("本人から撤回");
    send(
        "/" + id + "/permissions",
        grant.replace("\"version\":1", "\"version\":4").replace("permission-grant", "regrant"),
        409);
  }

  @Test
  void correctionWithdrawsOldTextAndStartsFreshWithStableReceiptAndPrivateHistory()
      throws Exception {
    var intake =
        send(
            "",
            """
        {"body":"元の本文","received_via":"PAPER","received_at":"2026-01-01T00:00:00Z","dedupe_key":"correction-intake"}
        """,
            201);
    String id = intake.get("review").get("id").asString();
    send(
        "/" + id + "/permissions",
        """
        {"version":0,"basis_type":"VERBAL","granted_at":"2026-01-01T00:00:00Z","evidence_note":"口頭確認","dedupe_key":"correction-permission"}
        """,
        201);
    send(
        "/" + id + "/decisions",
        """
        {"version":1,"decision":"APPROVE","reason":"確認済み","dedupe_key":"correction-approve"}
        """,
        200);
    String correction =
        """
        {"version":2,"reason":"誤記の訂正","body":"訂正した本文","received_via":"VERBAL","received_at":"2026-01-02T00:00:00Z","dedupe_key":"correction"}
        """;
    var corrected = send("/" + id + "/corrections", correction, 201);
    String next = corrected.get("review").get("id").asString();
    assertThat(corrected.get("review").get("supersedes_id").asString()).isEqualTo(id);
    assertThat(corrected.get("review").get("status").asString()).isEqualTo("PENDING");
    assertThat(corrected.get("review").get("permission_status").asString())
        .isEqualTo("NOT_GRANTED");
    assertThat(corrected.get("operation").get("review_id").asString()).isEqualTo(next);
    assertThat(corrected.get("operation").get("committed_version").asLong()).isZero();
    var old = read("/" + id, 200);
    assertThat(old.get("body").asString()).isEqualTo("元の本文");
    assertThat(old.get("status").asString()).isEqualTo("WITHDRAWN");
    assertThat(old.get("permission_status").asString()).isEqualTo("GRANTED");
    assertThat(
            jdbc.queryForObject(
                "select after_values->>'permission_status' from t_audit_events where target_id=? and action='REVIEW_CORRECTION_LINKED'",
                String.class,
                id))
        .isEqualTo("GRANTED");
    assertThat(old.get("superseded_by_id").asString()).isEqualTo(next);
    send(
        "/" + next + "/withdrawals",
        """
        {"version":0,"reason":"訂正版も取り下げ","dedupe_key":"withdraw-correction"}
        """,
        200);
    var replay = send("/" + id + "/corrections", correction, 200);
    assertThat(replay.get("operation").get("id")).isEqualTo(corrected.get("operation").get("id"));
    assertThat(replay.get("review").get("status").asString()).isEqualTo("WITHDRAWN");
    send(
        "/" + id + "/corrections",
        correction
            .replace("\"version\":2", "\"version\":3")
            .replace("\"dedupe_key\":\"correction\"", "\"dedupe_key\":\"another-correction\""),
        409);
    var events = read("/" + id + "/history?size=2", 200);
    assertThat(events.get("content").size()).isEqualTo(2);
    assertThat(events.get("content").get(0).get("type").asString()).isEqualTo("CORRECTION_LINKED");
    assertThat(events.get("total_pages").asInt()).isGreaterThan(1);
    var earlier = read("/" + id + "/history?size=2&page=1", 200);
    assertThat(earlier.get("content").get(0).get("permission_record_id").asString())
        .isEqualTo(old.get("permission").get("id").asString());
    assertThat(earlier.toString()).doesNotContain("口頭確認", "元の本文");
    currentStore = otherStore;
    read("/" + id, 404);
    read("/" + id + "/history", 404);
  }

  @Test
  void originRequiresCurrentOrderAccessAndEligibleSameStoreOrderButReplayDoesNotRevalidateIt()
      throws Exception {
    String orderId = order();
    String input =
        json.writeValueAsString(
            Map.of(
                "body",
                "関連受注あり",
                "received_via",
                "PAPER",
                "received_at",
                "2026-01-01T00:00:00Z",
                "origin_order_id",
                orderId,
                "dedupe_key",
                "origin-intake"));
    send("", input, 400);
    jdbc.update("update t_orders set status='COMPLETED', completed_at=now() where id=?", orderId);
    var received = send("", input, 201);
    assertThat(received.get("review").get("origin_order_version").asLong()).isZero();
    assertThat(received.get("review").get("origin_checked_at").isNull()).isFalse();
    jdbc.update("update t_orders set completion_invalidated=true where id=?", orderId);
    send("", input, 200);
    send("", input.replace("origin-intake", "new-ineligible"), 400);
    currentStore = otherStore;
    send("", input, 404);
    currentStore = storeId;
    jdbc.update(
        "delete from t_role_permissions where role_id=? and permission_id=(select id from t_permissions where code='ORDER_MANAGE')",
        roleId);
    send("", input, 403);
    read("/" + received.get("review").get("id").asString(), 200);
  }

  @Test
  void listSearchesOnlyLiteralDisplayNamesWithBoundedStablePagesAndNoPrivateDetail()
      throws Exception {
    var one =
        send(
            "",
            """
        {"body":"秘密の本文","display_name":"A%_名","received_via":"PAPER","received_at":"2026-01-01T00:00:00Z","dedupe_key":"list-one"}
        """,
            201);
    var two =
        send(
            "",
            """
        {"body":"A%_名","display_name":"AxY名","received_via":"VERBAL","received_at":"2026-01-01T00:00:00Z","dedupe_key":"list-two"}
        """,
            201);
    String firstId = one.get("review").get("id").asString();
    String secondId = two.get("review").get("id").asString();
    var searched =
        json.readTree(
            mvc.perform(get("/reviews").principal(auth).param("q", "%_"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(searched.get("total_elements").asLong()).isEqualTo(1);
    assertThat(searched.get("content").get(0).get("id").asString()).isEqualTo(firstId);
    assertThat(searched.get("content").get(0).properties())
        .extracting(Map.Entry::getKey)
        .containsExactlyInAnyOrder(
            "id",
            "intake_source",
            "display_name",
            "received_via",
            "received_at",
            "created_at",
            "status",
            "permission_status",
            "version",
            "publication",
            "displayed");
    assertThat(read("?q=秘密の本文", 200).get("total_elements").asLong()).isZero();
    assertThat(read("?sort=RECEIVED_ASC&size=1", 200).get("content").get(0).get("id").asString())
        .isEqualTo(firstId);
    assertThat(
            read("?sort=RECEIVED_ASC&size=1&page=1", 200)
                .get("content")
                .get(0)
                .get("id")
                .asString())
        .isEqualTo(secondId);
    for (String sort : List.of("RECEIVED_DESC", "CREATED_DESC")) {
      assertThat(read("?sort=" + sort, 200).get("content").get(0).get("id").asString())
          .isEqualTo(secondId);
    }
    assertThat(read("?sort=CREATED_ASC", 200).get("content").get(0).get("id").asString())
        .isEqualTo(firstId);
    assertThat(
            read("?review_id=" + firstId + "&status=APPROVED", 200).get("total_elements").asLong())
        .isZero();
    var normalized = read("?page=-1&size=0", 200);
    assertThat(normalized.get("number").asInt()).isZero();
    assertThat(normalized.get("size").asInt()).isEqualTo(20);
    for (String bad :
        List.of(
            "?page=99999999&size=100",
            "?sort=WRONG",
            "?status=WRONG",
            "/" + firstId + "/history?page=99999999&size=100")) read(bad, 400);
    currentStore = otherStore;
    assertThat(read("", 200).get("total_elements").asLong()).isZero();
    currentStore = storeId;
    jdbc.update("update t_users set enabled=false where id=?", actorId);
    read("", 403);
  }

  @Test
  void concurrentSameKeyAndCompetingCorrectionsCommitOnlyOnce() throws Exception {
    String input =
        """
        {"body":"並行受付","received_via":"PAPER","received_at":"2026-01-01T00:00:00Z","dedupe_key":"concurrent-intake"}
        """;
    var receipts = concurrent(() -> send("", input, 0), () -> send("", input, 0));
    assertThat(receipts.get(0).get("operation").get("id"))
        .isEqualTo(receipts.get(1).get("operation").get("id"));
    String id = receipts.get(0).get("review").get("id").asString();
    String correction =
        """
        {"version":0,"reason":"訂正理由","body":"訂正本文","received_via":"PAPER","received_at":"2026-01-01T00:00:00Z","dedupe_key":"race-correction"}
        """;
    var corrections =
        concurrent(
            () -> send("/" + id + "/corrections", correction, 0),
            () -> send("/" + id + "/corrections", correction, 0));
    assertThat(corrections.get(0).get("operation").get("id"))
        .isEqualTo(corrections.get(1).get("operation").get("id"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_reviews where store_id=?", Integer.class, storeId))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_review_history where store_id=?", Integer.class, storeId))
        .isEqualTo(3);
    String next = corrections.get(0).get("review").get("id").asString();
    var competing =
        concurrent(
            () ->
                send(
                    "/" + next + "/corrections",
                    correction.replace("race-correction", "race-a"),
                    -1),
            () ->
                send(
                    "/" + next + "/corrections",
                    correction.replace("race-correction", "race-b"),
                    -1));
    assertThat(competing.stream().filter(result -> result.has("operation")).count()).isEqualTo(1);
    assertThat(competing.stream().filter(result -> result.has("error")).count()).isEqualTo(1);
  }

  @Test
  void auditFailureRollsBackCorrectionAndItsKeyWithoutLeakingPrivateInputToAudit()
      throws Exception {
    var receipt =
        send(
            "",
            """
        {"body":"機密の原文","display_name":"機密の表示名","received_via":"PAPER","received_at":"2026-01-01T00:00:00Z","dedupe_key":"rollback-intake"}
        """,
            201);
    String id = receipt.get("review").get("id").asString();
    String correction =
        """
        {"version":0,"reason":"機密の訂正理由","body":"機密の新本文","received_via":"PAPER","received_at":"2026-01-01T00:00:00Z","dedupe_key":"rollback-correction"}
        """;
    jdbc.execute(
        "create function fail_review_audit() returns trigger language plpgsql as $$ begin if NEW.action='REVIEW_CORRECTION_RECEIVED' then raise exception '検証用の監査失敗'; end if; return NEW; end $$");
    jdbc.execute(
        "create trigger fail_review_audit before insert on t_audit_events for each row execute function fail_review_audit()");
    try {
      send("/" + id + "/corrections", correction, 500);
    } finally {
      jdbc.execute("drop trigger fail_review_audit on t_audit_events");
      jdbc.execute("drop function fail_review_audit()");
    }
    assertThat(read("/" + id, 200).get("status").asString()).isEqualTo("PENDING");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_reviews where store_id=?", Integer.class, storeId))
        .isEqualTo(1);
    send("/" + id + "/corrections", correction, 201);
    assertThat(
            jdbc.queryForObject(
                "select string_agg(before_values::text || after_values::text, ' ') from t_audit_events where store_id=?",
                String.class,
                storeId))
        .doesNotContain("機密", "本文", "訂正理由", "表示名");
  }

  @Test
  void rejectedAndWithdrawnRecordsCannotRegainEligibilityAndProjectionIsBounded() throws Exception {
    var receipt =
        send(
            "",
            """
        {"body":"却下対象","received_via":"PAPER","received_at":"2026-01-01T00:00:00Z","dedupe_key":"reject-intake"}
        """,
            201);
    String id = receipt.get("review").get("id").asString();
    send(
        "/" + id + "/decisions",
        """
        {"version":0,"decision":"REJECT","reason":"対象外","dedupe_key":"reject"}
        """,
        200);
    send(
        "/" + id + "/permissions",
        """
        {"version":1,"basis_type":"WRITTEN","granted_at":"2026-01-01T00:00:00Z","evidence_note":"記録","dedupe_key":"rejected-grant"}
        """,
        409);
    send(
        "/" + id + "/withdrawals",
        """
        {"version":1,"reason":"元の取り下げ理由","dedupe_key":"rejected-withdraw"}
        """,
        200);
    send(
        "/" + id + "/corrections",
        """
        {"version":2,"reason":"別の訂正理由","body":"新しい受付","received_via":"PAPER","received_at":"2026-01-01T00:00:00Z","dedupe_key":"withdrawn-correction"}
        """,
        201);
    var events = read("/" + id + "/history", 200);
    assertThat(events.get("content").get(1).get("reason").asString()).isEqualTo("元の取り下げ理由");
  }

  private List<JsonNode> concurrent(Callable<JsonNode> first, Callable<JsonNode> second)
      throws Exception {
    var barrier = new CyclicBarrier(2);
    var authentication = auth;
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var calls =
          List.of(first, second).stream()
              .map(
                  call ->
                      executor.submit(
                          () -> {
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            try {
                              barrier.await(10, TimeUnit.SECONDS);
                              return call.call();
                            } finally {
                              SecurityContextHolder.clearContext();
                            }
                          }))
              .toList();
      return List.of(
          calls.get(0).get(20, TimeUnit.SECONDS), calls.get(1).get(20, TimeUnit.SECONDS));
    }
  }

  @Test
  void versionMustBeAnIntegerWithoutJsonCoercion() throws Exception {
    var receipt =
        send(
            "",
            """
        {"body":"版を確認","received_via":"PAPER","received_at":"2026-01-01T00:00:00Z","dedupe_key":"integer-version"}
        """,
            201);
    String id = receipt.get("review").get("id").asString();
    for (String version : List.of("0.5", "\"0\"", "true", "-1", "null")) {
      send(
          "/" + id + "/decisions",
          "{\"version\":"
              + version
              + ",\"decision\":\"APPROVE\",\"reason\":\"確認\",\"dedupe_key\":\"strict-version\"}",
          400);
    }
    assertThat(read("/" + id, 200).get("status").asString()).isEqualTo("PENDING");
  }

  @Test
  void moderatorSetsPublicationIndependentlyAndDisplayedFollowsEveryPrerequisite()
      throws Exception {
    String id = intake("publication-intake");
    var hidden = read("/" + id, 200);
    assertThat(hidden.get("publication").get("published").asBoolean()).isFalse();
    assertThat(hidden.get("publication").get("publish_from").isNull()).isTrue();
    assertThat(hidden.get("displayed").asBoolean()).isFalse();
    var published =
        publish(
            id,
            """
            {"version":0,"published":true,"display_order":3,"dedupe_key":"publish"}
            """,
            200);
    assertThat(published.get("operation").get("type").asString()).isEqualTo("PUBLICATION_CHANGED");
    assertThat(published.get("review").get("publication").get("published").asBoolean()).isTrue();
    assertThat(published.get("review").get("publication").get("display_order").asInt())
        .isEqualTo(3);
    assertThat(published.get("review").get("displayed").asBoolean()).isFalse();
    send(
        "/" + id + "/decisions",
        """
        {"version":1,"decision":"APPROVE","reason":"確認","dedupe_key":"publication-approve"}
        """,
        200);
    assertThat(read("/" + id, 200).get("displayed").asBoolean()).isFalse();
    var granted =
        send(
            "/" + id + "/permissions",
            """
            {"version":2,"basis_type":"WRITTEN","granted_at":"2026-01-02T00:00:00Z","evidence_note":"書面","dedupe_key":"publication-grant"}
            """,
            201);
    assertThat(granted.get("review").get("displayed").asBoolean()).isTrue();
    assertThat(read("?review_id=" + id, 200).get("content").get(0).get("displayed").asBoolean())
        .isTrue();
    var scheduled =
        publish(
            id,
            "{\"version\":3,\"published\":true,\"publish_from\":\""
                + OffsetDateTime.now().plusDays(1)
                + "\",\"dedupe_key\":\"schedule\"}",
            200);
    assertThat(scheduled.get("review").get("displayed").asBoolean()).isFalse();
    var ended =
        publish(
            id,
            "{\"version\":4,\"published\":true,\"publish_until\":\""
                + OffsetDateTime.now().minusMinutes(2)
                + "\",\"dedupe_key\":\"ended\"}",
            200);
    assertThat(ended.get("review").get("displayed").asBoolean()).isFalse();
    publish(
        id,
        """
        {"version":5,"published":true,"display_order":3,"dedupe_key":"again"}
        """,
        200);
    var revoked =
        send(
            "/" + id + "/permission-revocations",
            """
            {"version":6,"withdrawal_received_at":"2026-01-03T00:00:00Z","reason":"撤回","dedupe_key":"publication-revoke"}
            """,
            200);
    assertThat(revoked.get("review").get("publication").get("published").asBoolean()).isTrue();
    assertThat(revoked.get("review").get("displayed").asBoolean()).isFalse();
    var latestChange = read("/" + id + "/history?size=100", 200).get("content").get(1);
    assertThat(latestChange.get("type").asString()).isEqualTo("PUBLICATION_CHANGED");
    assertThat(latestChange.get("after_publication").get("published").asBoolean()).isTrue();
    assertThat(latestChange.get("after_publication").get("display_order").asInt()).isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "select after_values->>'display_order' from t_audit_events where target_id=? and action='REVIEW_PUBLICATION_CHANGED' order by id limit 1",
                String.class,
                id))
        .isEqualTo("3");
  }

  @Test
  void withdrawnReviewIsNotDisplayedAndCorrectionStartsHidden() throws Exception {
    String id = intake("withdraw-publication-intake");
    send(
        "/" + id + "/permissions",
        """
        {"version":0,"basis_type":"VERBAL","granted_at":"2026-01-01T00:00:00Z","evidence_note":"口頭","dedupe_key":"wp-grant"}
        """,
        201);
    send(
        "/" + id + "/decisions",
        """
        {"version":1,"decision":"APPROVE","reason":"確認","dedupe_key":"wp-approve"}
        """,
        200);
    var published =
        publish(
            id,
            """
            {"version":2,"published":true,"dedupe_key":"wp-publish"}
            """,
            200);
    assertThat(published.get("review").get("displayed").asBoolean()).isTrue();
    assertThat(publicReviews(storeId, "").get("content")).hasSize(1);
    var corrected =
        send(
            "/" + id + "/corrections",
            """
            {"version":3,"reason":"訂正","body":"訂正本文","received_via":"PAPER","received_at":"2026-01-02T00:00:00Z","dedupe_key":"wp-correct"}
            """,
            201);
    assertThat(corrected.get("review").get("publication").get("published").asBoolean()).isFalse();
    var old = read("/" + id, 200);
    assertThat(old.get("status").asString()).isEqualTo("WITHDRAWN");
    assertThat(old.get("publication").get("published").asBoolean()).isTrue();
    assertThat(old.get("displayed").asBoolean()).isFalse();
    assertThat(publicReviews(storeId, "").get("content")).isEmpty();
  }

  @Test
  void publicationRejectsBadPeriodsStaleVersionsForeignStoresAndIntakeOnlyStaff() throws Exception {
    String id = intake("publication-guard-intake");
    String inverted =
        """
        {"version":0,"published":true,"publish_from":"2026-12-01T20:00:00+09:00","publish_until":"2026-12-01T20:00:00+09:00","dedupe_key":"bad-period"}
        """;
    publish(id, inverted, 400);
    publish(id, inverted.replace("\"published\":true,", ""), 400);
    String valid =
        """
        {"version":0,"published":true,"publish_from":"2026-12-01T20:00:00+09:00","publish_until":"2026-12-02T03:00:00+09:00","dedupe_key":"valid"}
        """;
    var first = publish(id, valid, 200);
    assertThat(first.get("review").get("publication").get("publish_from").asString())
        .isEqualTo("2026-12-01T11:00:00Z");
    var replay = publish(id, valid, 200);
    assertThat(replay.get("operation").get("id")).isEqualTo(first.get("operation").get("id"));
    assertThat(replay.get("operation").get("replayed").asBoolean()).isTrue();
    publish(id, valid.replace("\"published\":true", "\"published\":false"), 409);
    publish(id, valid.replace("\"valid\"", "\"stale\""), 409);
    String next = valid.replace("\"version\":0", "\"version\":1");
    currentStore = otherStore;
    publish(id, next.replace("\"valid\"", "\"foreign\""), 404);
    currentStore = storeId;
    var intakeOnly =
        new UsernamePasswordAuthenticationToken(
            email,
            "unused",
            List.of(
                new SimpleGrantedAuthority("PERM_REVIEW_VIEW"),
                new SimpleGrantedAuthority("PERM_REVIEW_MANAGE")));
    SecurityContextHolder.getContext().setAuthentication(intakeOnly);
    mvc.perform(
            put("/reviews/" + id + "/publication")
                .principal(intakeOnly)
                .contentType("application/json")
                .content(next.replace("\"valid\"", "\"intake-only\"")))
        .andExpect(status().isForbidden());
    SecurityContextHolder.getContext().setAuthentication(auth);
    jdbc.update(
        "delete from t_role_permissions where role_id=? and permission_id=(select id from t_permissions where code='REVIEW_MODERATE')",
        roleId);
    publish(id, next.replace("\"valid\"", "\"demoted\""), 403);
    assertThat(read("/" + id, 200).get("version").asLong()).isEqualTo(1);
  }

  @Test
  void publicEndpointKeepsStartExcludesEndAndOrdersTotallyWithinTheNamedStore() throws Exception {
    var now = OffsetDateTime.parse("2026-06-01T00:00:00Z");
    clock.set(now.toInstant());
    String older = now.minusDays(2).toString();
    String newer = now.minusDays(1).toString();
    List<String> publications =
        List.of(
            "\"published\":true",
            "\"published\":true,\"publish_from\":\"" + older + "\"",
            "\"published\":true,\"publish_from\":\"" + newer + "\"",
            "\"published\":true,\"display_order\":2",
            "\"published\":true,\"publish_from\":\"" + older + "\",\"display_order\":1",
            "\"published\":true,\"display_order\":1",
            "\"published\":false,\"display_order\":0",
            "\"published\":true,\"publish_from\":\"" + now + "\",\"display_order\":0",
            "\"published\":true,\"publish_until\":\"" + now + "\",\"display_order\":0",
            "\"published\":true,\"publish_from\":\""
                + now.plusMinutes(1)
                + "\",\"display_order\":0",
            "\"published\":true");
    for (int i = 0; i < publications.size(); i++)
      approveGrantAndPublish("order-" + i, publications.get(i));
    String pending = intake("pending-published");
    publish(pending, "{\"version\":0,\"published\":true,\"dedupe_key\":\"pending-publish\"}", 200);
    String withdrawn = approveGrantAndPublish("withdrawn", "\"published\":true");
    send(
        "/" + withdrawn + "/withdrawals",
        "{\"version\":3,\"reason\":\"取り下げ\",\"dedupe_key\":\"withdrawn-withdraw\"}",
        200);
    currentStore = otherStore;
    approveGrantAndPublish("foreign", "\"published\":true,\"display_order\":0");
    var all = publicReviews(storeId, "?size=100");
    assertThat(all.get("content"))
        .extracting(r -> r.get("body").asString())
        .containsExactly(
            "order-7",
            "order-4",
            "order-5",
            "order-3",
            "order-2",
            "order-1",
            "order-0",
            "order-10");
    assertThat(all.get("content").get(0).get("received_on").asString()).isEqualTo("2026-01-02");
    var second = publicReviews(storeId, "?page=1&size=3");
    assertThat(second.get("content"))
        .extracting(r -> r.get("body").asString())
        .containsExactly("order-3", "order-2", "order-1");
    assertThat(second.get("total_elements").asLong()).isEqualTo(8);
    assertThat(publicReviews(otherStore, "").get("content"))
        .extracting(r -> r.get("body").asString())
        .containsExactly("foreign");
    publicReviews(storeId, "?size=101", 400);
    publicReviews(storeId, "?page=99999999&size=100", 400);
  }

  private String intake(String key) throws Exception {
    return send(
            "",
            "{\"body\":\"公開設定の対象\",\"display_name\":\"表示名\",\"received_via\":\"PAPER\",\"received_at\":\"2026-01-01T00:00:00Z\",\"dedupe_key\":\""
                + key
                + "\"}",
            201)
        .get("review")
        .get("id")
        .asString();
  }

  private String approveGrantAndPublish(String body, String publication) throws Exception {
    String id =
        send(
                "",
                "{\"body\":\""
                    + body
                    + "\",\"display_name\":\"表示名\",\"received_via\":\"PAPER\",\"received_at\":\"2026-01-01T20:00:00Z\",\"dedupe_key\":\""
                    + body
                    + "\"}",
                201)
            .get("review")
            .get("id")
            .asString();
    send(
        "/" + id + "/decisions",
        "{\"version\":0,\"decision\":\"APPROVE\",\"reason\":\"確認\",\"dedupe_key\":\""
            + body
            + "-approve\"}",
        200);
    send(
        "/" + id + "/permissions",
        "{\"version\":1,\"basis_type\":\"WRITTEN\",\"granted_at\":\"2026-01-02T00:00:00Z\",\"evidence_note\":\"書面\",\"dedupe_key\":\""
            + body
            + "-grant\"}",
        201);
    publish(id, "{\"version\":2," + publication + ",\"dedupe_key\":\"" + body + "-publish\"}", 200);
    return id;
  }

  private JsonNode publicReviews(Long store, String query) throws Exception {
    return publicReviews(store, query, 200);
  }

  /** 公開端点は未認証で叩かれる。名乗る店舗だけを差し替え、終われば元の担当者と店舗へ戻す。 */
  private JsonNode publicReviews(Long store, String query, int expected) throws Exception {
    var previous = currentStore;
    currentStore = store;
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "public", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
    try {
      return json.readTree(
          mvc.perform(get("/reviews/public" + query))
              .andExpect(status().is(expected))
              .andReturn()
              .getResponse()
              .getContentAsString());
    } finally {
      currentStore = previous;
      SecurityContextHolder.getContext().setAuthentication(auth);
    }
  }

  private JsonNode publish(String id, String body, int expected) throws Exception {
    return json.readTree(
        mvc.perform(
                put("/reviews/" + id + "/publication")
                    .principal(auth)
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private String order() {
    return new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
        .execute(
            status -> {
              var item =
                  context
                      .getBean(ServiceItemRepository.class)
                      .saveAndFlush(
                          ServiceItem.create(
                              currentStore,
                              new ServiceTerms(ServiceKind.COURSE, "検証コース", 60, null, 1000, 500)));
              var revision =
                  context
                      .getBean(ServiceRevisionRepository.class)
                      .saveAndFlush(ServiceRevision.record(item, null, actorId));
              var row =
                  Order.builder()
                      .status(OrderStatus.CONFIRMED)
                      .businessDate(LocalDate.now())
                      .course(
                          new OrderCourse(
                              item.getId(),
                              revision.getId(),
                              1,
                              "検証コース",
                              60,
                              1000,
                              500,
                              "CURRENT_SETTING",
                              OffsetDateTime.now()))
                      .build();
              row.setStoreId(item.getStoreId());
              return context.getBean(OrderRepository.class).saveAndFlush(row).getId();
            });
  }

  /** 本番と同じ引数解決器の並び（Pageable の検査・副キー補完と現在店舗）を standalone の MockMvc へ渡す。 */
  private static HandlerMethodArgumentResolver[] argumentResolvers() {
    List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();
    new WebBindingConfig(
            new PageableHandlerMethodArgumentResolver(),
            new CurrentStoreArgumentResolver(new StoreScopeHolder(), id -> true))
        .addArgumentResolvers(resolvers);
    return resolvers.toArray(HandlerMethodArgumentResolver[]::new);
  }

  private JsonNode read(String path, int expected) throws Exception {
    return json.readTree(
        mvc.perform(get("/reviews" + path).principal(auth))
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode send(String path, String body, int expected) throws Exception {
    var response =
        mvc.perform(
                post("/reviews" + path)
                    .principal(auth)
                    .contentType("application/json")
                    .content(body))
            .andExpect(
                result -> {
                  int code = result.getResponse().getStatus();
                  if (expected > 0) assertThat(code).isEqualTo(expected);
                  else if (expected == 0) assertThat(code).isIn(200, 201);
                  else assertThat(code).isIn(201, 409);
                })
            .andReturn()
            .getResponse();
    return json.readTree(response.getContentAsString());
  }

  @Configuration
  @EnableAutoConfiguration
  @EnableMethodSecurity(jsr250Enabled = true)
  @EntityScan("com.kizuna")
  @EnableJpaRepositories("com.kizuna")
  @ComponentScan(basePackageClasses = ReviewService.class)
  @Import({
    AuditWriter.class,
    BusinessAudit.class,
    ReviewController.class,
    ReviewOriginLookup.class
  })
  static class Config {
    @Bean
    SettableClock clock() {
      return new SettableClock();
    }

    @Bean
    AppProperties properties() {
      return new AppProperties();
    }
  }

  /** 掲出の境界を分単位で固定するための時計。未設定なら現在時刻。時間帯は店舗の現地日付を確かめるため東京に置く。 */
  static class SettableClock extends Clock {
    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private volatile Instant fixed;

    void set(Instant instant) {
      fixed = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
      var at = fixed;
      return at == null ? Instant.now() : at;
    }
  }
}
