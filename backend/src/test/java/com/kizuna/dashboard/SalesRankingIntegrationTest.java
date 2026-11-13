package com.kizuna.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.advertising.reporting.AdvertisingReportReader;
import com.kizuna.dashboard.api.SalesRankingController;
import com.kizuna.dashboard.application.SalesRankingService;
import com.kizuna.order.reporting.OperationalReportReader;
import com.kizuna.remuneration.reporting.RemunerationReportReader;
import com.kizuna.reporting.api.CrossStoreOperationalReportController;
import com.kizuna.reporting.application.OperationalReportService;
import com.kizuna.reporting.application.ReportSnapshot;
import com.kizuna.reporting.dashboard.SalesByStoreReader;
import com.kizuna.reporting.infrastructure.ReportRenderer;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.shift.remuneration.AttendanceFacts;
import com.kizuna.store.StoreShortNames;
import com.kizuna.store.dashboard.StoreDashboardReader;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/** 実 PostgreSQL で、店舗別の売上順位が呼び出し者の店舗集合の全店を、店舗別の運営集計と同じ数字で契約どおりに並べることを確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SalesRankingIntegrationTest {
  private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private SettableClock clock;
  private StoreScopeHolder scopes;
  private MockMvc mvc;
  private final JsonMapper json =
      JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
  private long staff;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    clock = context.getBean(SettableClock.class);
    scopes = context.getBean(StoreScopeHolder.class);
    mvc =
        MockMvcBuilders.standaloneSetup(
                context.getBean(SalesRankingController.class),
                context.getBean(CrossStoreOperationalReportController.class))
            .setCustomArgumentResolvers(argumentResolvers())
            .setControllerAdvice(new CommonExceptionHandler())
            .setMessageConverters(new JacksonJsonHttpMessageConverter(json))
            .build();
    staff =
        jdbc.queryForObject(
            "insert into t_users(email,display_name,user_type) values (?,'検証担当','STAFF') returning id",
            Long.class,
            UUID.randomUUID() + "@example.test");
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @BeforeEach
  void authenticateWithRegionPermission() {
    // 日付変更時刻 05:00 の 05:00 ちょうど。営業日は暦日と同じ 11/12 になる
    clock.set(Instant.parse("2026-11-11T20:00:00Z"));
    authenticate("PERM_DASHBOARD_SALES_RANKING_VIEW");
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    scopes.clear();
  }

  @Test
  @DisplayName("集合内の全店を売上の降順・同額は store_id の昇順で並べ、売上 0 の店舗も並べ、集合外の店舗は並べない")
  void ranksEveryStoreInTheSetByContract() throws Exception {
    long tiedLow = store("同額の店A", true);
    long zeroLow = store("売上なしの店A", false);
    long top = store("首位の店", true);
    long tiedHigh = store("同額の店B", false);
    long zeroHigh = store("売上なしの店B", true);
    long outside = store("集合外の店", true);
    order(top, TODAY, "COMPLETED", false, 50_000);
    order(top, TODAY.withDayOfMonth(1), "COMPLETED", false, 10_000);
    order(tiedLow, TODAY, "COMPLETED", false, 20_000);
    order(tiedHigh, TODAY.withDayOfMonth(5), "COMPLETED", false, 20_000);
    // 数えないもの: 誤完了の無効化・完了でない受注・前月・未来の営業日・集合外の店舗
    order(zeroLow, TODAY, "COMPLETED", true, 0);
    order(zeroLow, TODAY, "CONFIRMED", false, 60_000);
    order(zeroHigh, LocalDate.of(2026, 10, 31), "COMPLETED", false, 70_000);
    order(zeroHigh, TODAY.plusDays(1), "COMPLETED", false, 80_000);
    order(outside, TODAY, "COMPLETED", false, 90_000);
    scopes.install(() -> new StoreScope(Set.of(tiedLow, zeroLow, top, tiedHigh, zeroHigh)));

    JsonNode body = ranking();

    assertThat(body.get("business_date").asString()).isEqualTo("2026-11-12");
    var stores = body.get("stores");
    assertThat(stores.valueStream().map(store -> store.get("store_id").asLong()))
        .containsExactly(top, tiedLow, tiedHigh, zeroLow, zeroHigh);
    assertStore(stores.get(0), "首位の店", "ACTIVE", 60_000, 2);
    assertStore(stores.get(1), "同額の店A", "ACTIVE", 20_000, 1);
    assertStore(stores.get(2), "同額の店B", "PREPARING", 20_000, 1);
    assertStore(stores.get(3), "売上なしの店A", "PREPARING", 0, 0);
    assertStore(stores.get(4), "売上なしの店B", "ACTIVE", 0, 0);
  }

  @Test
  @DisplayName("店舗ごとの数字が、同じ期間の店舗別の運営集計（group_by=store）の料金合計・件数と一致する")
  void equalsCrossStoreReportGroupedByStore() throws Exception {
    long first = store("一号店", true);
    long second = store("二号店", true);
    long quiet = store("三号店", false);
    order(first, TODAY, "COMPLETED", false, 12_000);
    order(first, TODAY.withDayOfMonth(2), "COMPLETED", false, 8_000);
    order(first, TODAY, "COMPLETED", true, 0);
    order(second, TODAY.withDayOfMonth(1), "COMPLETED", false, 30_000);
    scopes.install(() -> new StoreScope(Set.of(first, second, quiet)));

    JsonNode ranking = ranking();
    authenticate("PERM_OPERATIONAL_REPORT_SET_VIEW");
    JsonNode report =
        body(
            get("/operational-reports/cross-store")
                .param("from", "2026-11-01")
                .param("to", "2026-11-12")
                .param("group_by", "store")
                .param("size", "100"));

    var reported = new HashMap<Long, long[]>();
    report
        .get("rows")
        .get("content")
        .forEach(
            row ->
                reported.put(
                    row.get("store_id").asLong(),
                    new long[] {row.get("total_fee").asLong(), row.get("order_count").asLong()}));
    assertThat(ranking.get("stores")).hasSize(3);
    for (var store : ranking.get("stores")) {
      var expected = reported.getOrDefault(store.get("store_id").asLong(), new long[] {0, 0});
      assertThat(store.get("sales").asLong()).isEqualTo(expected[0]);
      assertThat(store.get("order_count").asLong()).isEqualTo(expected[1]);
    }
    assertThat(reported.get(first)).containsExactly(20_000, 2);
  }

  @Test
  @DisplayName("空の店舗集合は全店と読み替えず空の一覧を返す")
  void emptySetIsAnEmptyList() throws Exception {
    long somewhere = store("どこかの店", true);
    order(somewhere, TODAY, "COMPLETED", false, 10_000);
    scopes.install(() -> new StoreScope(Set.of()));

    JsonNode body = ranking();

    assertThat(body.get("business_date").asString()).isEqualTo("2026-11-12");
    assertThat(body.get("stores")).isEmpty();
  }

  @Test
  @DisplayName("店舗集合を解決できない要求は 403")
  void unresolvedScopeIsForbidden() throws Exception {
    scopes.install(() -> null);

    mvc.perform(get("/dashboard/sales-ranking")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("区画の権限が無ければ、運営集計の権限があっても 403")
  void withoutRegionPermissionIsForbidden() throws Exception {
    authenticate("PERM_OPERATIONAL_REPORT_SET_VIEW");
    long own = store("当店", true);
    scopes.install(() -> new StoreScope(Set.of(own)));

    mvc.perform(get("/dashboard/sales-ranking")).andExpect(status().isForbidden());
  }

  private JsonNode ranking() throws Exception {
    return body(get("/dashboard/sales-ranking"));
  }

  private static void assertStore(
      JsonNode store, String name, String status, long sales, long orderCount) {
    assertThat(store.get("store_name").asString()).isEqualTo(name);
    assertThat(store.get("store_status").asString()).isEqualTo(status);
    assertThat(store.get("sales").asLong()).isEqualTo(sales);
    assertThat(store.get("order_count").asLong()).isEqualTo(orderCount);
  }

  private JsonNode body(MockHttpServletRequestBuilder request) throws Exception {
    var response = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
    return json.readTree(response.getContentAsString());
  }

  private static void authenticate(String... authorities) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "staff@example.test",
                "unused",
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
  }

  private long store(String name, boolean active) {
    return jdbc.queryForObject(
        "insert into t_stores(name,short_name,domain,status) values (?,?,?,?) returning id",
        Long.class,
        name,
        StoreShortNames.next(),
        UUID.randomUUID() + ".example.test",
        active ? "ACTIVE" : "PREPARING");
  }

  private void order(
      long storeId, LocalDate businessDate, String status, boolean invalidated, int totalFee) {
    String course = UUID.randomUUID().toString();
    String revision = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_services(id,store_id,kind,name,duration_minutes,price,remuneration,revision_number) values (?,?,'COURSE','検証コース',60,100,0,1)",
        course,
        storeId);
    jdbc.update(
        "insert into t_service_revisions(id,store_id,service_id,revision_number,operation,actor_id,occurred_at,after_kind,after_name,after_duration_minutes,after_price,after_remuneration) values (?,?,?,1,'CREATED',?,current_timestamp,'COURSE','検証コース',60,100,0)",
        revision,
        storeId,
        course,
        staff);
    jdbc.update(
        "insert into t_orders(id,store_id,business_date,course_name,course_minutes,course_service_id,course_revision_id,course_revision_number,course_price,course_remuneration,course_adoption_basis,course_adopted_at,status,completed_at,completion_invalidated,total_fee) values (?,?,?,'検証コース',60,?,?,1,100,0,'CURRENT_SETTING',current_timestamp,?,case when ? = 'COMPLETED' then current_timestamp end,?,?)",
        UUID.randomUUID().toString(),
        storeId,
        businessDate,
        course,
        revision,
        status,
        status,
        invalidated,
        totalFee);
  }

  private static HandlerMethodArgumentResolver[] argumentResolvers() {
    List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();
    resolvers.add(new PageableHandlerMethodArgumentResolver());
    return resolvers.toArray(HandlerMethodArgumentResolver[]::new);
  }

  @Configuration
  @EnableTransactionManagement
  @EnableMethodSecurity
  @Import({
    SalesRankingController.class,
    SalesRankingService.class,
    SalesByStoreReader.class,
    StoreDashboardReader.class,
    StoreScopeHolder.class,
    ReportSnapshot.class,
    OperationalReportReader.class,
    CrossStoreOperationalReportController.class,
    OperationalReportService.class,
    ReportRenderer.class,
    BusinessDateService.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return IntegrationTestDatabase.dataSource(SalesRankingIntegrationTest.class);
    }

    @Bean
    SpringLiquibase liquibase(DataSource source) {
      var migration = new SpringLiquibase();
      migration.setDataSource(source);
      migration.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
      migration.setContexts("production");
      return migration;
    }

    @Bean
    @DependsOn("liquibase")
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource source, ConfigurableListableBeanFactory beans) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(source);
      factory.setPackagesToScan("com.kizuna");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(
          Map.of(
              "hibernate.resource.beans.container",
              new SpringBeanContainer(beans),
              "hibernate.hbm2ddl.auto",
              "validate",
              "hibernate.physical_naming_strategy",
              "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
      return factory;
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
      return new JpaTransactionManager(factory);
    }

    @Bean
    AppProperties appProperties() {
      return new AppProperties();
    }

    @Bean
    RemunerationReportReader remunerationReportReader() {
      return mock(RemunerationReportReader.class);
    }

    @Bean
    AdvertisingReportReader advertisingReportReader() {
      return mock(AdvertisingReportReader.class);
    }

    @Bean
    AttendanceFacts attendanceFacts() {
      return mock(AttendanceFacts.class);
    }

    @Bean
    SettableClock clock() {
      return new SettableClock();
    }

    @Bean
    SystemConfigService systemConfigService() {
      var configs = mock(SystemConfigService.class);
      when(configs.getConfigValue("business_date_change_time")).thenReturn(Optional.of("05:00"));
      return configs;
    }
  }

  static class SettableClock extends Clock {
    private volatile Instant fixed;

    void set(Instant instant) {
      fixed = instant;
    }

    @Override
    public ZoneId getZone() {
      return TOKYO;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
      return fixed;
    }
  }
}
