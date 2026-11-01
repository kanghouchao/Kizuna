package com.kizuna.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.shared.storescope.CurrentStoreArgumentResolver;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.store.StoreShortNames;
import com.kizuna.survey.api.SurveyController;
import com.kizuna.survey.api.SurveyResponseController;
import com.kizuna.user.application.BusinessAudit;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
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
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SurveyIntegrationTest {
  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private Long currentStore;
  private MockMvc mvc;
  private final JsonMapper json =
      JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
  private Long storeId, otherStore, actorId, roleId;
  private String email;
  private Authentication auth;

  @BeforeAll
  void connect() {
    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                IntegrationTestDatabase.springDataSourceProperties(SurveyIntegrationTest.class))
            .properties(
                Map.ofEntries(
                    Map.entry("spring.config.name", "survey-test"),
                    Map.entry(
                        "spring.liquibase.change-log",
                        "classpath:db/changelog/db.changelog-master.yaml"),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                    Map.entry("spring.jpa.open-in-view", "false"),
                    Map.entry("logging.level.root", "WARN")))
            .run();
    jdbc = context.getBean(JdbcTemplate.class);
    mvc =
        MockMvcBuilders.standaloneSetup(
                context.getBean(SurveyController.class),
                context.getBean(SurveyResponseController.class))
            .setCustomArgumentResolvers(
                new CurrentStoreArgumentResolver(new StoreScopeHolder(), id -> true))
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
        "insert into t_role_permissions(role_id,permission_id) select ?,id from t_permissions where code in ('SURVEY_VIEW','SURVEY_MANAGE','SURVEY_RECORD')",
        roleId);
    jdbc.update("insert into t_user_roles(user_id,role_id) values (?,?)", actorId, roleId);
    auth =
        new UsernamePasswordAuthenticationToken(
            email,
            "unused",
            List.of(
                new SimpleGrantedAuthority("PERM_SURVEY_VIEW"),
                    new SimpleGrantedAuthority("PERM_SURVEY_MANAGE"),
                new SimpleGrantedAuthority("PERM_SURVEY_RECORD"),
                    new SimpleGrantedAuthority("PERM_ORDER_MANAGE")));
    SecurityContextHolder.getContext().setAuthentication(auth);
    currentStore = storeId;
  }

  private static final String DEFINITION =
      """
      {"title":" 店舗入力の問票 ","questions":[{"question_key":"q1","type":"TEXT","prompt":"担当者が入力した設問","required":true,"options":[]}],"dedupe_key":"create"}
      """;

  @Test
  void staffCreatesOpensAndRecordsOneAnswerAgainstFrozenQuestions() throws Exception {
    var draft = send("/surveys", DEFINITION, 201).get("revision");
    String base =
        "/surveys/"
            + draft.get("survey_id").asString()
            + "/revisions/"
            + draft.get("id").asString();
    var opened =
        send(base + "/openings", "{\"version\":0,\"reason\":\"確認\",\"dedupe_key\":\"open\"}", 200);
    assertThat(opened.get("revision").get("status").asString()).isEqualTo("OPEN");
    var receipt =
        send(
            base + "/responses",
            """
        {"revision_version":1,"received_via":"PAPER","received_at":"2026-01-01T00:00:00Z","answers":[{"question_key":"q1","text":"  原回答  "}],"dedupe_key":"answer"}
        """,
            201);
    var answer = read("/survey-responses/" + receipt.get("answer").get("id").asString(), 200);
    assertThat(answer.get("answers").get(0).get("text").asString()).isEqualTo("  原回答  ");
    assertThat(read(base + "/response-counts", 200).get("active_records").asLong()).isEqualTo(1);
    assertThat(read(base, 200).get("questions").get(0).get("prompt").asString())
        .isEqualTo("担当者が入力した設問");
  }

  @Test
  void catalogueAndAnswerListsKeepSensitiveTextInDetailOnly() throws Exception {
    var draft = send("/surveys", DEFINITION, 201).get("revision");
    String sid = draft.get("survey_id").asString(), rid = draft.get("id").asString();
    var catalogue = read("/surveys", 200).get("content");
    assertThat(catalogue.size()).isEqualTo(1);
    assertThat(catalogue.get(0).get("latest_title").asString()).isEqualTo("店舗入力の問票");
    assertThat(catalogue.toString()).doesNotContain("担当者が入力した設問");
    assertThat(read("/surveys/" + sid, 200).get("draft_revision_id").asString()).isEqualTo(rid);
    String base = "/surveys/" + sid + "/revisions/" + rid;
    assertThat(read("/surveys/" + sid + "/revisions", 200).get("content").size()).isEqualTo(1);
    assertThat(read(base + "/history", 200).get("content").get(0).get("type").asString())
        .isEqualTo("DRAFT_CREATED");
    assertThat(read(base + "/responses", 200).get("content").isEmpty()).isTrue();
  }

  private String openedRevision() throws Exception {
    var draft = send("/surveys", DEFINITION, 201).get("revision");
    String base =
        "/surveys/"
            + draft.get("survey_id").asString()
            + "/revisions/"
            + draft.get("id").asString();
    send(base + "/openings", action(0, "open"), 200);
    return base;
  }

  private String action(int version, String key) {
    return "{\"version\":" + version + ",\"reason\":\"機密の操作理由\",\"dedupe_key\":\"" + key + "\"}";
  }

  private String receipt(String key) {
    return "{\"revision_version\":1,\"received_via\":\"PAPER\",\"received_at\":\"2026-01-01T00:00:00Z\",\"answers\":[{\"question_key\":\"q1\",\"text\":\"機密の原回答\"}],\"dedupe_key\":\""
        + key
        + "\"}";
  }

  private String correction(int version, String key) {
    return receipt(key)
        .replace("\"revision_version\":1", "\"version\":" + version + ",\"reason\":\"機密の訂正理由\"");
  }

  @Test
  void closedRevisionKeepsImmutableAnswersAndCorrectionChainWithHonestCounts() throws Exception {
    String base = openedRevision();
    var original = send(base + "/responses", receipt("receipt"), 201);
    String first = "/survey-responses/" + original.get("answer").get("id").asString();
    send(base + "/closures", action(1, "close"), 200);
    send(
        base + "/responses",
        receipt("new-after-close").replace("\"revision_version\":1", "\"revision_version\":2"),
        409);
    var corrected = send(first + "/corrections", correction(0, "correct"), 201);
    String next = "/survey-responses/" + corrected.get("answer").get("id").asString();
    assertThat(read(first, 200).get("status").asString()).isEqualTo("WITHDRAWN");
    assertThat(read(first, 200).get("answers").get(0).get("text").asString()).isEqualTo("機密の原回答");
    var counts = read(base + "/response-counts", 200);
    assertThat(counts.get("total_records").asLong()).isEqualTo(2);
    assertThat(counts.get("active_records").asLong()).isEqualTo(1);
    assertThat(counts.get("withdrawn_records").asLong()).isEqualTo(1);
    send(first + "/corrections", correction(1, "other-correction"), 409);
    send(next + "/withdrawals", action(0, "withdraw"), 200);
    var replay = send(first + "/corrections", correction(0, "correct"), 200);
    assertThat(replay.get("operation").get("id")).isEqualTo(corrected.get("operation").get("id"));
    assertThat(replay.get("answer").get("status").asString()).isEqualTo("WITHDRAWN");
    assertThat(
            send(base + "/responses", receipt("receipt"), 200)
                .get("answer")
                .get("status")
                .asString())
        .isEqualTo("WITHDRAWN");
    assertThat(read(base + "/response-counts", 200).get("active_records").asLong()).isZero();
    send(next + "/corrections", correction(1, "after-withdrawal"), 201);
    assertThat(read(base + "/response-counts", 200).get("active_records").asLong()).isEqualTo(1);
    assertThat(read(first + "/history", 200).get("content").get(0).get("reason").asString())
        .isEqualTo("機密の訂正理由");
    assertThat(read(base + "/responses", 200).toString())
        .doesNotContain("機密", "answers", "recorded_by");
  }

  @Test
  void revisionReplacementAndExplicitSwitchPreserveOldQuestions() throws Exception {
    var draft = send("/surveys", DEFINITION, 201).get("revision");
    String sid = draft.get("survey_id").asString(), rid = draft.get("id").asString();
    String series = "/surveys/" + sid, base = series + "/revisions/" + rid;
    String replacement =
        DEFINITION
            .replace("{\"title\"", "{\"version\":0,\"title\"")
            .replace("\"create\"", "\"replace\"")
            .replace("担当者が入力した設問", "確定前に修正した設問");
    var replaced =
        json.readTree(
            mvc.perform(
                    put(base).principal(auth).contentType("application/json").content(replacement))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(replaced.get("revision").get("version").asLong()).isEqualTo(1);
    send(base + "/openings", action(1, "open"), 200);
    mvc.perform(
            put(base)
                .principal(auth)
                .contentType("application/json")
                .content(
                    replacement
                        .replace("\"version\":0", "\"version\":2")
                        .replace("\"replace\"", "\"late\"")))
        .andExpect(status().isConflict());
    String nextInput =
        DEFINITION
            .replace("{\"title\"", "{\"based_on_revision_id\":\"" + rid + "\",\"title\"")
            .replace("\"create\"", "\"revise\"");
    var next = send(series + "/revisions", nextInput, 201).get("revision");
    String nextBase = series + "/revisions/" + next.get("id").asString();
    send(nextBase + "/openings", action(0, "next-open"), 409);
    send(series + "/revisions", nextInput.replace("\"revise\"", "\"duplicate-draft\""), 409);
    send(base + "/closures", action(2, "close"), 200);
    send(nextBase + "/openings", action(0, "next-open"), 200);
    assertThat(read(base, 200).get("questions").get(0).get("prompt").asString())
        .isEqualTo("確定前に修正した設問");
    assertThat(read(series, 200).get("open_revision_id")).isEqualTo(next.get("id"));
    assertThat(send(series + "/revisions", nextInput, 200).get("revision").get("status").asString())
        .isEqualTo("OPEN");
    send(base + "/openings", action(3, "reopen"), 409);
  }

  @Test
  void strictInputDoesNotCoerceTypesOrAcceptIdentityFieldsAndUnknownAnswers() throws Exception {
    for (String body :
        List.of(
            DEFINITION.replace("true", "\"true\""),
            DEFINITION.replace("\"TEXT\"", "1"),
            DEFINITION.replace("\"title\":\" 店舗入力の問票 \"", "\"title\":42"),
            DEFINITION.replace("\"questions\"", "\"customer_id\":\"1\",\"questions\""),
            DEFINITION.replace("\"options\":[]", "\"options\":null"))) send("/surveys", body, 400);
    String base = openedRevision();
    for (String body :
        List.of(
            receipt("bad").replace("\"q1\"", "\"missing\""),
            receipt("bad").replace("\"機密の原回答\"", "\"   \""),
            receipt("bad").replace("\"text\":\"機密の原回答\"", "\"option_key\":\"a\""),
            receipt("bad").replace("\"revision_version\":1", "\"revision_version\":\"1\""),
            receipt("bad").replace("2026-01-01T00:00:00Z", "2099-01-01T00:00:00Z"),
            receipt("bad").replace("\"answers\"", "\"member_id\":\"1\",\"answers\"")))
      send(base + "/responses", body, 400);
    assertThat(read(base + "/response-counts", 200).get("total_records").asLong()).isZero();
    read("/surveys?size=101", 400);
    read(base + "/history?page=99999999&size=100", 400);
    read(base + "/responses?sort=WRONG", 400);
  }

  @Test
  void sameKeyRacesReplayOneResultAndCloseRacesNeverAdmitAfterClose() throws Exception {
    var created =
        race(() -> send("/surveys", DEFINITION, 0), () -> send("/surveys", DEFINITION, 0));
    assertThat(created.get(0).get("operation").get("id"))
        .isEqualTo(created.get(1).get("operation").get("id"));
    var draft = created.get(0).get("revision");
    String base =
        "/surveys/"
            + draft.get("survey_id").asString()
            + "/revisions/"
            + draft.get("id").asString();
    send(base + "/openings", action(0, "open"), 200);
    var results =
        race(
            () -> send(base + "/responses", receipt("raced-answer"), -1),
            () -> send(base + "/closures", action(1, "close"), 200));
    assertThat(read(base, 200).get("status").asString()).isEqualTo("CLOSED");
    long expected = results.get(0).has("operation") ? 1 : 0;
    assertThat(read(base + "/response-counts", 200).get("total_records").asLong())
        .isEqualTo(expected);
    send(base + "/responses", receipt("after-close"), 409);
  }

  @Test
  void currentAuthorizationAndStoreIsolationApplyToEveryReadWriteAndReplay() throws Exception {
    String base = openedRevision();
    var receipt = send(base + "/responses", receipt("first"), 201);
    String answer = "/survey-responses/" + receipt.get("answer").get("id").asString();
    currentStore = otherStore;
    for (String path : List.of(base, answer, base + "/response-counts", answer + "/history"))
      read(path, 404);
    send(answer + "/withdrawals", action(0, "foreign"), 404);
    assertThat(read("/surveys", 200).get("content").isEmpty()).isTrue();
    currentStore = storeId;
    jdbc.update(
        "delete from t_role_permissions where role_id=? and permission_id=(select id from t_permissions where code='SURVEY_RECORD')",
        roleId);
    send(base + "/responses", receipt("first"), 403);
    read(answer, 200);
    jdbc.update("update t_users set enabled=false where id=?", actorId);
    for (String path :
        List.of("/surveys", base, answer, base + "/response-counts", answer + "/history"))
      read(path, 403);
    jdbc.update("update t_users set enabled=true,user_type='SERVICE' where id=?", actorId);
    read(answer, 403);
  }

  @Test
  void auditFailureRollsBackCorrectionAndCountsQueryDoesNotLoadFreeText() throws Exception {
    String base = openedRevision();
    var receipt = send(base + "/responses", receipt("first"), 201);
    String answer = "/survey-responses/" + receipt.get("answer").get("id").asString();
    jdbc.execute(
        "create function fail_survey_audit() returns trigger language plpgsql as $$ begin if NEW.action='SURVEY_CORRECTION_RECEIVED' then raise exception '検証用の監査失敗'; end if; return NEW; end $$");
    jdbc.execute(
        "create trigger fail_survey_audit before insert on t_audit_events for each row execute function fail_survey_audit()");
    try {
      send(answer + "/corrections", correction(0, "atomic"), 500);
    } finally {
      jdbc.execute("drop trigger fail_survey_audit on t_audit_events");
      jdbc.execute("drop function fail_survey_audit()");
    }
    assertThat(read(answer, 200).get("status").asString()).isEqualTo("ACTIVE");
    assertThat(read(base + "/response-counts", 200).get("total_records").asLong()).isEqualTo(1);
    send(answer + "/corrections", correction(0, "atomic"), 201);
    assertThat(
            jdbc.queryForObject(
                "select string_agg(before_values::text || after_values::text,' ') from t_audit_events where store_id=?",
                String.class,
                storeId))
        .doesNotContain("機密", "担当者が入力した設問");
    var stats =
        context.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    stats.clear();
    read(base + "/response-counts", 200);
    read(base + "/responses", 200);
    assertThat(stats.getEntityStatistics("com.kizuna.survey.domain.SurveyAnswer").getLoadCount())
        .isZero();
    assertThat(stats.getEntityStatistics("com.kizuna.survey.domain.SurveyRevision").getLoadCount())
        .isZero();
  }

  @Test
  void typedQuestionsNormalizeAnswerOrderButDoNotInferAnonymousOrRewardRules() throws Exception {
    String definition =
        """
        {"title":"任意と選択","questions":[{"question_key":"q1","type":"TEXT","prompt":"自由文","required":false,"options":[]},{"question_key":"q2","type":"SINGLE_CHOICE","prompt":"選択","required":true,"options":[{"option_key":"a","label":"一つ目"},{"option_key":"b","label":"二つ目"}]}],"dedupe_key":"definition"}
        """;
    var revision = send("/surveys", definition, 201).get("revision");
    String base =
        "/surveys/"
            + revision.get("survey_id").asString()
            + "/revisions/"
            + revision.get("id").asString();
    send(base + "/openings", action(0, "open"), 200);
    String input =
        """
        {"revision_version":1,"received_via":"EXISTING_RECORD","received_at":"2026-01-01T09:00:00+09:00","answers":[{"question_key":"q2","option_key":"a"},{"question_key":"q1","text":"  一行目\\r\\n二行目  "}],"dedupe_key":"typed-answer"}
        """;
    var result = send(base + "/responses", input, 201);
    assertThat(result.get("answer").get("answers").get(0).get("question_key").asString())
        .isEqualTo("q1");
    assertThat(result.get("answer").get("answers").get(0).get("text").asString())
        .isEqualTo("  一行目\n二行目  ");
    String equivalent =
        """
        {"revision_version":1,"received_via":"EXISTING_RECORD","received_at":"2026-01-01T00:00:00Z","answers":[{"question_key":"q1","text":"  一行目\\n二行目  ","option_key":null},{"question_key":"q2","option_key":"a","text":null}],"dedupe_key":"typed-answer"}
        """;
    assertThat(send(base + "/responses", equivalent, 200).get("operation").get("id"))
        .isEqualTo(result.get("operation").get("id"));
    send(
        base + "/responses",
        equivalent.replace("\"option_key\":\"a\"", "\"option_key\":\"b\""),
        409);
    send(
        base + "/responses",
        input
            .replace("\"option_key\":\"a\"", "\"option_key\":\"unknown\"")
            .replace("typed-answer", "bad-choice"),
        400);
    send(
        base + "/responses",
        input
            .replace("\"question_key\":\"q2\"", "\"question_key\":\"q1\"")
            .replace("typed-answer", "duplicate-question"),
        400);
    send(
        "/surveys",
        definition
            .replace("\"label\":\"二つ目\"", "\"label\":\" 一つ目 \"")
            .replace("\"definition\"", "\"duplicate-option\""),
        400);
    assertThat(result.get("answer").toString())
        .doesNotContain("anonymous", "member_id", "customer_id", "order_id", "point");
    assertThat(result.get("answer").get("created_at").asString())
        .matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,6})?Z");
  }

  @Test
  void competingRevisionCreationAndCorrectionsHaveOnlyOneSuccessor() throws Exception {
    String base = openedRevision();
    var revision = read(base, 200);
    String sid = revision.get("survey_id").asString(), rid = revision.get("id").asString();
    String body =
        DEFINITION
            .replace("{\"title\"", "{\"based_on_revision_id\":\"" + rid + "\",\"title\"")
            .replace("\"create\"", "\"revision-a\"");
    var drafts =
        race(
            () -> send("/surveys/" + sid + "/revisions", body, -1),
            () ->
                send(
                    "/surveys/" + sid + "/revisions",
                    body.replace("revision-a", "revision-b"),
                    -1));
    assertThat(drafts.stream().filter(n -> n.has("operation")).count()).isEqualTo(1);
    var answer = send(base + "/responses", receipt("original"), 201).get("answer");
    String aid = answer.get("id").asString();
    var corrected =
        race(
            () -> send("/survey-responses/" + aid + "/corrections", correction(0, "correct-a"), -1),
            () ->
                send("/survey-responses/" + aid + "/corrections", correction(0, "correct-b"), -1));
    assertThat(corrected.stream().filter(n -> n.has("operation")).count()).isEqualTo(1);
    var counts = read(base + "/response-counts", 200);
    assertThat(counts.get("total_records").asLong()).isEqualTo(2);
    assertThat(counts.get("active_records").asLong()).isEqualTo(1);
  }

  private List<JsonNode> race(Callable<JsonNode> one, Callable<JsonNode> two) throws Exception {
    var barrier = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var tasks =
          List.of(one, two).stream()
              .map(
                  task ->
                      pool.submit(
                          (Callable<JsonNode>)
                              () -> {
                                SecurityContextHolder.getContext().setAuthentication(auth);
                                try {
                                  barrier.await(10, TimeUnit.SECONDS);
                                  return task.call();
                                } finally {
                                  SecurityContextHolder.clearContext();
                                }
                              }))
              .toList();
      return List.of(
          tasks.get(0).get(30, TimeUnit.SECONDS), tasks.get(1).get(30, TimeUnit.SECONDS));
    }
  }

  private JsonNode read(String path, int expected) throws Exception {
    return json.readTree(
        mvc.perform(get(path).principal(auth))
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode send(String path, String body, int expected) throws Exception {
    var response =
        mvc.perform(post(path).principal(auth).contentType("application/json").content(body))
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
  @EnableMethodSecurity
  @EntityScan("com.kizuna")
  @EnableJpaRepositories("com.kizuna")
  @ComponentScan(basePackages = "com.kizuna.survey.application")
  @Import({
    AuditWriter.class,
    BusinessAudit.class,
    SurveyController.class,
    SurveyResponseController.class
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
