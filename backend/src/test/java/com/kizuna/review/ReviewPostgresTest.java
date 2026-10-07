package com.kizuna.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCourse;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.revieworigin.ReviewOriginLookup;
import com.kizuna.review.api.store.ReviewController;
import com.kizuna.review.application.ReviewService;
import com.kizuna.review.publication.ReviewPublication;
import com.kizuna.service.domain.ServiceItem;
import com.kizuna.service.domain.ServiceItemRepository;
import com.kizuna.service.domain.ServiceKind;
import com.kizuna.service.domain.ServiceRevision;
import com.kizuna.service.domain.ServiceRevisionRepository;
import com.kizuna.service.domain.ServiceTerms;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.persistence.StoreScopeStampListener;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreFilterEnable;
import com.kizuna.user.application.BusinessAudit;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfEnvironmentVariable(named = "KIZUNA_REVIEW_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReviewPostgresTest {
  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private StoreContext store;
  private MockMvc mvc;
  private final JsonMapper json =
      JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
  private Long storeId, otherStore, actorId, roleId;
  private String email;
  private UsernamePasswordAuthenticationToken auth;

  @BeforeAll
  void connect() {
    String schema = "review_" + UUID.randomUUID().toString().replace("-", "");
    String url = System.getenv("KIZUNA_REVIEW_TEST_JDBC_URL");
    new JdbcTemplate(new DriverManagerDataSource(url, "postgres", ""))
        .execute("create schema " + schema);
    String hash = new BCryptPasswordEncoder(4).encode(UUID.randomUUID().toString());
    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                Map.ofEntries(
                    Map.entry("spring.config.name", "review-test"),
                    Map.entry("spring.datasource.url", url + "?currentSchema=" + schema),
                    Map.entry("spring.datasource.username", "postgres"),
                    Map.entry(
                        "spring.liquibase.change-log",
                        "classpath:db/changelog/db.changelog-master.yaml"),
                    Map.entry("spring.liquibase.contexts", "production"),
                    Map.entry("spring.liquibase.parameters.initialAdminPasswordHash", hash),
                    Map.entry("spring.liquibase.parameters.demoUserPasswordHash", hash),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                    Map.entry("spring.jpa.open-in-view", "false"),
                    Map.entry("logging.level.root", "WARN")))
            .run();
    jdbc = context.getBean(JdbcTemplate.class);
    store = context.getBean(StoreContext.class);
    mvc =
        MockMvcBuilders.standaloneSetup(context.getBean(ReviewController.class))
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
    if (store != null) store.clear();
  }

  @BeforeEach
  void fixtures() {
    String suffix = UUID.randomUUID().toString();
    email = suffix + "@example.invalid";
    storeId =
        jdbc.queryForObject(
            "insert into t_stores(name,domain) values ('受付検証',?) returning id",
            Long.class,
            suffix + ".invalid");
    otherStore =
        jdbc.queryForObject(
            "insert into t_stores(name,domain) values ('別店舗',?) returning id",
            Long.class,
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
    jdbc.update("insert into t_user_roles(platform_user_id,role_id) values (?,?)", actorId, roleId);
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
    store.setStoreId(storeId);
  }

  @Test
  void staffRecordsImmutableIntakeWithoutClaimingCustomerVerification() throws Exception {
    var result =
        mvc.perform(
                post("/store/reviews")
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
                    post("/store/reviews")
                        .principal(auth)
                        .contentType("application/json")
                        .content(input))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    var replay =
        json.readTree(
            mvc.perform(
                    post("/store/reviews")
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
            post("/store/reviews")
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
    var publication = context.getBean(ReviewPublication.class);
    send(
        "/" + id + "/decisions",
        """
        {"version":0,"decision":"APPROVE","reason":"確認","dedupe_key":"permission-approve"}
        """,
        200);
    assertThat(publication.current(storeId, Set.of(id))).isEmpty();
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
    var projected = publication.current(storeId, Set.of(id)).get(id);
    assertThat(projected.body()).isEqualTo("  公開を許可した原文  ");
    assertThat(json.valueToTree(projected).properties())
        .extracting(Map.Entry::getKey)
        .containsExactlyInAnyOrder("review_id", "review_version", "display_name", "body");
    assertThatThrownBy(() -> publication.current(otherStore, Set.of(id)))
        .isInstanceOf(AccessDeniedException.class);
    store.setStoreId(otherStore);
    assertThat(publication.current(otherStore, Set.of(id))).isEmpty();
    store.clear();
    assertThatThrownBy(() -> publication.current(storeId, Set.of(id)))
        .isInstanceOf(AccessDeniedException.class);
    store.setStoreId(storeId);
    var revoked =
        send(
            "/" + id + "/permission-revocations",
            """
        {"version":2,"withdrawal_received_at":"2026-01-03T00:00:00Z","reason":"本人から撤回","dedupe_key":"permission-revoke"}
        """,
            200);
    assertThat(revoked.get("review").get("status").asString()).isEqualTo("APPROVED");
    assertThat(revoked.get("review").get("permission_status").asString()).isEqualTo("REVOKED");
    assertThat(publication.current(storeId, Set.of(id))).isEmpty();
    var replay = send("/" + id + "/permissions", grant, 200);
    assertThat(replay.get("review").get("permission_status").asString()).isEqualTo("REVOKED");
    assertThat(replay.get("review").get("permission").get("revocation").get("reason").asString())
        .isEqualTo("本人から撤回");
    send(
        "/" + id + "/permissions",
        grant.replace("\"version\":1", "\"version\":3").replace("permission-grant", "regrant"),
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
    assertThat(old.get("superseded_by_id").asString()).isEqualTo(next);
    assertThat(context.getBean(ReviewPublication.class).current(storeId, Set.of(id, next)))
        .isEmpty();
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
    var earlier =
        read("/" + id + "/history?size=2&cursor=" + events.get("next_cursor").asString(), 200);
    assertThat(earlier.get("content").get(0).get("permission_record_id").asString())
        .isEqualTo(old.get("permission").get("id").asString());
    assertThat(earlier.toString()).doesNotContain("口頭確認", "元の本文");
    store.setStoreId(otherStore);
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
    store.setStoreId(otherStore);
    send("", input, 404);
    store.setStoreId(storeId);
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
            mvc.perform(get("/store/reviews").principal(auth).param("q", "%_"))
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
            "version");
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
    for (String bad :
        List.of(
            "?page=-1",
            "?size=0",
            "?size=101",
            "?sort=WRONG",
            "?status=WRONG",
            "/" + firstId + "/history?cursor=bad",
            "/" + firstId + "/history?size=101")) read(bad, 400);
    store.setStoreId(otherStore);
    assertThat(read("", 200).get("total_elements").asLong()).isZero();
    store.setStoreId(storeId);
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
    var projection = context.getBean(ReviewPublication.class);
    assertThat(projection.current(storeId, Set.of())).isEmpty();
    var tooMany =
        LongStream.rangeClosed(1, 101).mapToObj(Long::toString).collect(Collectors.toSet());
    assertThatThrownBy(() -> projection.current(storeId, tooMany))
        .isInstanceOf(IllegalArgumentException.class);
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
    Long selectedStore = storeId;
    var authentication = auth;
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var calls =
          List.of(first, second).stream()
              .map(
                  call ->
                      executor.submit(
                          () -> {
                            store.setStoreId(selectedStore);
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            try {
                              barrier.await(10, TimeUnit.SECONDS);
                              return call.call();
                            } finally {
                              store.clear();
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

  private String order() {
    return new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
        .execute(
            status -> {
              var item =
                  context
                      .getBean(ServiceItemRepository.class)
                      .saveAndFlush(
                          ServiceItem.create(
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
              return context.getBean(OrderRepository.class).saveAndFlush(row).getId();
            });
  }

  private JsonNode read(String path, int expected) throws Exception {
    return json.readTree(
        mvc.perform(get("/store/reviews" + path).principal(auth))
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode send(String path, String body, int expected) throws Exception {
    var response =
        mvc.perform(
                post("/store/reviews" + path)
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
  @EnableAspectJAutoProxy
  @EnableMethodSecurity
  @EntityScan("com.kizuna")
  @EnableJpaRepositories("com.kizuna")
  @ComponentScan(basePackageClasses = ReviewService.class)
  @Import({
    StoreContext.class,
    StoreFilterEnable.class,
    StoreScopeStampListener.class,
    AuditWriter.class,
    BusinessAudit.class,
    ReviewController.class,
    ReviewPublication.class,
    ReviewOriginLookup.class
  })
  static class Config {
    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    AppProperties properties() {
      return new AppProperties();
    }
  }
}
