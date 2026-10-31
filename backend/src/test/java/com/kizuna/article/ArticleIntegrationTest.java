package com.kizuna.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.article.api.ArticleController;
import com.kizuna.article.application.ArticleService;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.shared.storescope.CurrentStoreArgumentResolver;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.store.StoreShortNames;
import com.kizuna.user.application.BusinessAudit;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArticleIntegrationTest {
  private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");
  private static final String BODY = "本文の一行目\n二行目の秘密の告知";

  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private MockMvc mvc;
  private final JsonMapper json =
      JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
  private Long storeId, otherStore, currentStore;
  private Authentication auth;

  @BeforeAll
  void connect() {
    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                IntegrationTestDatabase.springDataSourceProperties(ArticleIntegrationTest.class))
            .properties(
                Map.ofEntries(
                    Map.entry("spring.config.name", "article-test"),
                    Map.entry(
                        "spring.liquibase.change-log",
                        "classpath:db/changelog/db.changelog-master.yaml"),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                    Map.entry("spring.jpa.open-in-view", "false"),
                    Map.entry("logging.level.root", "WARN")))
            .run();
    jdbc = context.getBean(JdbcTemplate.class);
    mvc =
        MockMvcBuilders.standaloneSetup(context.getBean(ArticleController.class))
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
    String email = suffix + "@example.invalid";
    storeId =
        jdbc.queryForObject(
            "insert into t_stores(name,short_name,domain) values ('記事検証',?,?) returning id",
            Long.class,
            StoreShortNames.next(),
            suffix + ".invalid");
    otherStore =
        jdbc.queryForObject(
            "insert into t_stores(name,short_name,domain) values ('別店舗',?,?) returning id",
            Long.class,
            StoreShortNames.next(),
            suffix + "-other.invalid");
    jdbc.update(
        "insert into t_users(email,display_name,user_type,store_scope_type) values (?,'記事担当','STAFF','ALL_STORES')",
        email);
    auth = principal(email, "PERM_ARTICLE_MANAGE");
    SecurityContextHolder.getContext().setAuthentication(auth);
    currentStore = storeId;
  }

  @Test
  void holderCreatesHiddenArticleThenEditsAndDeletesIt() throws Exception {
    String id = create("お知らせ", BODY);

    var fresh = call(get("/articles/" + id), 200);
    assertThat(fresh.get("title").asString()).isEqualTo("お知らせ");
    assertThat(fresh.get("body").asString()).isEqualTo(BODY);
    assertThat(fresh.get("publication").get("published").asBoolean()).isFalse();
    assertThat(fresh.get("displayed").asBoolean()).isFalse();
    assertThat(fresh.get("version").asLong()).isZero();

    var edited =
        call(
            put("/articles/" + id)
                .content(
                    """
                    {"title":"改題","body":"差し替えた本文","version":0}
                    """),
            200);
    assertThat(edited.get("title").asString()).isEqualTo("改題");
    assertThat(edited.get("body").asString()).isEqualTo("差し替えた本文");
    assertThat(edited.get("version").asLong()).isEqualTo(1);

    var list = call(get("/articles"), 200);
    assertThat(list.get("content")).hasSize(1);
    assertThat(list.get("content").get(0).has("body")).isFalse();
    assertThat(list.get("content").get(0).get("title").asString()).isEqualTo("改題");

    call(delete("/articles/" + id).param("version", "1"), 204);
    call(get("/articles/" + id), 404);
    assertThat(jdbc.queryForObject("select count(*) from t_articles where id=?", Integer.class, id))
        .isZero();
  }

  @Test
  void callerWithoutArticleManageIsRefusedOnEveryEndpoint() throws Exception {
    String id = create("権限検証", BODY);
    var other = principal(auth.getName(), "PERM_REVIEW_VIEW");
    SecurityContextHolder.getContext().setAuthentication(other);

    call(post("/articles").content("{\"title\":\"t\",\"body\":\"b\"}"), 403);
    call(get("/articles"), 403);
    call(get("/articles/" + id), 403);
    call(put("/articles/" + id).content("{\"title\":\"t\",\"body\":\"b\",\"version\":0}"), 403);
    call(
        put("/articles/" + id + "/publication").content("{\"published\":true,\"version\":0}"), 403);
    call(delete("/articles/" + id).param("version", "0"), 403);

    SecurityContextHolder.getContext().setAuthentication(auth);
    var unchanged = call(get("/articles/" + id), 200);
    assertThat(unchanged.get("version").asLong()).isZero();
    assertThat(unchanged.get("title").asString()).isEqualTo("権限検証");
  }

  @Test
  void staleVersionIsConflictOnEditPublicationAndDelete() throws Exception {
    String id = create("版検証", BODY);
    call(put("/articles/" + id).content("{\"title\":\"一回目\",\"body\":\"b\",\"version\":0}"), 200);

    call(put("/articles/" + id).content("{\"title\":\"古い\",\"body\":\"b\",\"version\":0}"), 409);
    call(
        put("/articles/" + id + "/publication").content("{\"published\":true,\"version\":0}"), 409);
    call(delete("/articles/" + id).param("version", "0"), 409);

    var current = call(get("/articles/" + id), 200);
    assertThat(current.get("title").asString()).isEqualTo("一回目");
    assertThat(current.get("publication").get("published").asBoolean()).isFalse();
    assertThat(current.get("version").asLong()).isEqualTo(1);
  }

  @Test
  void missingVersionIsBadRequest() throws Exception {
    String id = create("版なし", BODY);
    call(put("/articles/" + id).content("{\"title\":\"t\",\"body\":\"b\"}"), 400);
    call(put("/articles/" + id + "/publication").content("{\"published\":true}"), 400);
    call(delete("/articles/" + id), 400);
    call(put("/articles/" + id + "/publication").content("{\"version\":0}"), 400);
  }

  @Test
  void anotherStoresArticleIsNotFound() throws Exception {
    String id = create("自店の記事", BODY);
    currentStore = otherStore;

    call(get("/articles/" + id), 404);
    call(put("/articles/" + id).content("{\"title\":\"他店\",\"body\":\"b\",\"version\":0}"), 404);
    call(
        put("/articles/" + id + "/publication").content("{\"published\":true,\"version\":0}"), 404);
    call(delete("/articles/" + id).param("version", "0"), 404);
    assertThat(call(get("/articles"), 200).get("content")).isEmpty();

    currentStore = storeId;
    var own = call(get("/articles/" + id), 200);
    assertThat(own.get("title").asString()).isEqualTo("自店の記事");
    assertThat(own.get("version").asLong()).isZero();
  }

  @Test
  void displayedFollowsPublishedFlagAndHalfOpenPeriod() throws Exception {
    String inside = create("期間内", BODY);
    publish(inside, true, "2026-06-01T00:00:00Z", "2026-06-01T01:00:00Z");
    String ended = create("終了時刻ちょうど", BODY);
    publish(ended, true, "2026-05-31T23:00:00Z", "2026-06-01T00:00:00Z");
    String upcoming = create("開始前", BODY);
    publish(upcoming, true, "2026-06-01T00:01:00Z", null);
    String hidden = create("非公開", BODY);
    publish(hidden, false, "2026-05-31T00:00:00Z", "2026-06-02T00:00:00Z");
    String unlimited = create("無期限", BODY);
    publish(unlimited, true, null, null);

    var displayed = new HashMap<String, Boolean>();
    call(get("/articles"), 200)
        .get("content")
        .forEach(row -> displayed.put(row.get("id").asString(), row.get("displayed").asBoolean()));
    assertThat(displayed)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(inside, true, ended, false, upcoming, false, hidden, false, unlimited, true));

    var hiddenDetail = call(get("/articles/" + hidden), 200);
    assertThat(hiddenDetail.get("publication").get("publish_from").isNull()).isFalse();

    call(
        put("/articles/" + inside + "/publication")
            .content(
                """
                {"published":true,"publish_from":"2026-06-01T10:00:00+09:00",
                 "publish_until":"2026-06-01T10:00:00+09:00","version":1}
                """),
        400);
    call(
        put("/articles/" + inside + "/publication")
            .content(
                """
                {"published":true,"publish_from":"2026-06-02T00:00:00Z",
                 "publish_until":"2026-06-01T00:00:00Z","version":1}
                """),
        400);
  }

  @Test
  void listIsNewestUpdateFirstAndRejectsOutOfRangePage() throws Exception {
    String first = create("一番目", BODY);
    String second = create("二番目", BODY);
    call(put("/articles/" + first).content("{\"title\":\"更新\",\"body\":\"b\",\"version\":0}"), 200);

    var rows = call(get("/articles"), 200).get("content");
    assertThat(List.of(rows.get(0).get("id").asString(), rows.get(1).get("id").asString()))
        .containsExactly(first, second);
    call(get("/articles").param("size", "101"), 400);
    call(get("/articles").param("page", "-1"), 400);
  }

  @Test
  void lengthLimitsAreBadRequest() throws Exception {
    String title100 = "題".repeat(100);
    String body10000 = "文".repeat(10000);
    create(title100, body10000);

    call(post("/articles").content(body("題".repeat(101), "b")), 400);
    call(post("/articles").content(body("t", "文".repeat(10001))), 400);
    call(post("/articles").content(body("  ", "b")), 400);
    call(post("/articles").content(body("t", "")), 400);
    call(post("/articles").content("{\"body\":\"b\"}"), 400);
  }

  @Test
  void everyOperationIsAuditedWithoutTheBody() throws Exception {
    String id = create("監査の表題", BODY);
    call(put("/articles/" + id).content(body("監査の表題", "差し替えた秘密の本文", 0L)), 200);
    publish(id, true, "2026-05-31T00:00:00Z", null, 1);
    call(delete("/articles/" + id).param("version", "2"), 204);

    var actions =
        jdbc.queryForList(
            "select action from t_audit_events where target_type='ARTICLE' and target_id=? order by id",
            String.class,
            id);
    assertThat(actions)
        .containsExactly(
            "ARTICLE_CREATED", "ARTICLE_UPDATED", "ARTICLE_PUBLICATION_CHANGED", "ARTICLE_DELETED");
    assertThat(
            jdbc.queryForList(
                "select store_id from t_audit_events where target_id=?", Long.class, id))
        .containsOnly(storeId);

    String values =
        jdbc.queryForObject(
            "select string_agg(coalesce(before_values::text,'') || coalesce(after_values::text,''), ' ') from t_audit_events where target_id=?",
            String.class,
            id);
    assertThat(values).contains("監査の表題");
    assertThat(values).doesNotContain("秘密").doesNotContain("本文の一行目");
    assertThat(
            jdbc.queryForObject(
                "select after_values->>'redacted_fields_changed' from t_audit_events where target_id=? and action='ARTICLE_UPDATED'",
                String.class,
                id))
        .isEqualTo("body");
    assertThat(
            jdbc.queryForObject(
                "select after_values->>'published' from t_audit_events where target_id=? and action='ARTICLE_PUBLICATION_CHANGED'",
                String.class,
                id))
        .isEqualTo("true");
    assertThat(
            jdbc.queryForObject(
                "select after_values->>'exists' from t_audit_events where target_id=? and action='ARTICLE_DELETED'",
                String.class,
                id))
        .isEqualTo("false");
  }

  private String create(String title, String text) throws Exception {
    return call(post("/articles").content(body(title, text)), 201).get("id").asString();
  }

  private void publish(String id, boolean published, String from, String until) throws Exception {
    publish(id, published, from, until, 0);
  }

  private void publish(String id, boolean published, String from, String until, long version)
      throws Exception {
    var request = new LinkedHashMap<String, Object>();
    request.put("published", published);
    request.put("publish_from", from);
    request.put("publish_until", until);
    request.put("version", version);
    call(put("/articles/" + id + "/publication").content(json.writeValueAsString(request)), 200);
  }

  private String body(String title, String text) {
    return body(title, text, null);
  }

  private String body(String title, String text, Long version) {
    var request = new LinkedHashMap<String, Object>();
    request.put("title", title);
    request.put("body", text);
    if (version != null) request.put("version", version);
    return json.writeValueAsString(request);
  }

  private JsonNode call(MockHttpServletRequestBuilder request, int expected) throws Exception {
    var current = SecurityContextHolder.getContext().getAuthentication();
    RequestBuilder built = request.principal(current).contentType("application/json");
    String content =
        mvc.perform(built)
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return content.isEmpty() ? json.nullNode() : json.readTree(content);
  }

  private static Authentication principal(String email, String authority) {
    return new UsernamePasswordAuthenticationToken(
        email, "unused", List.of(new SimpleGrantedAuthority(authority)));
  }

  @Configuration
  @EnableAutoConfiguration
  @EnableMethodSecurity
  @EntityScan("com.kizuna")
  @EnableJpaRepositories("com.kizuna")
  @ComponentScan(basePackageClasses = ArticleService.class)
  @Import({AuditWriter.class, BusinessAudit.class, ArticleController.class})
  static class Config {
    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
