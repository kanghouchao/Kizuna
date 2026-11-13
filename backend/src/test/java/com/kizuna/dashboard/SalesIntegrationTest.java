package com.kizuna.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.advertising.reporting.AdvertisingReportReader;
import com.kizuna.dashboard.api.SalesController;
import com.kizuna.dashboard.application.SalesService;
import com.kizuna.order.reporting.OperationalReportReader;
import com.kizuna.remuneration.reporting.RemunerationReportReader;
import com.kizuna.reporting.api.OperationalReportController;
import com.kizuna.reporting.application.OperationalReportService;
import com.kizuna.reporting.application.ReportSnapshot;
import com.kizuna.reporting.dashboard.StoreSalesReader;
import com.kizuna.reporting.infrastructure.ReportRenderer;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.shared.storescope.CurrentStoreArgumentResolver;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.shared.web.WebBindingConfig;
import com.kizuna.shift.remuneration.AttendanceFacts;
import com.kizuna.store.StoreShortNames;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

/** 実 PostgreSQL で、売上の区画の数字が同じ期間の運営集計の料金合計・件数と一致することを確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SalesIntegrationTest {
  private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private SettableClock clock;
  private MockMvc mvc;
  private final JsonMapper json =
      JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
  private long staff;
  private long store;
  private long otherStore;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    clock = context.getBean(SettableClock.class);
    mvc =
        MockMvcBuilders.standaloneSetup(
                context.getBean(SalesController.class),
                context.getBean(OperationalReportController.class))
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
  void stores() {
    store = store("当店");
    otherStore = store("他店");
    // 日付変更時刻 05:00 の 05:00 ちょうど。営業日は暦日と同じ 11/12 になる
    clock.set(Instant.parse("2026-11-11T20:00:00Z"));
    authenticate("PERM_DASHBOARD_SALES_VIEW");
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("今日・今月・前月同時点・日別が、同じ期間の運営集計の料金合計・件数と一致する")
  void equalsOperationalReportForTheSamePeriods() throws Exception {
    order(store, TODAY, "COMPLETED", false, 12_000);
    order(store, TODAY, "COMPLETED", false, 8_000);
    order(store, TODAY.withDayOfMonth(3), "COMPLETED", false, 30_000);
    order(store, TODAY.withDayOfMonth(1), "COMPLETED", false, 5_000);
    order(store, LocalDate.of(2026, 10, 1), "COMPLETED", false, 7_000);
    order(store, LocalDate.of(2026, 10, 12), "COMPLETED", false, 9_000);
    // 数えないもの: 誤完了の無効化・完了でない受注・前月同時点の翌日・未来の営業日・他店
    order(store, TODAY, "COMPLETED", true, 0);
    order(store, TODAY, "CONFIRMED", false, 60_000);
    order(store, LocalDate.of(2026, 10, 13), "COMPLETED", false, 70_000);
    order(store, TODAY.plusDays(1), "COMPLETED", false, 80_000);
    order(otherStore, TODAY, "COMPLETED", false, 90_000);

    JsonNode sales = sales();
    authenticate("PERM_OPERATIONAL_REPORT_VIEW", "PERM_ORDER_MANAGE");

    assertThat(sales.get("business_date").asString()).isEqualTo("2026-11-12");
    assertTotals(sales.get("today"), 20_000, 2);
    assertTotals(sales.get("month_to_date"), 55_000, 4);
    assertTotals(sales.get("previous_month_to_date"), 16_000, 2);
    assertSameAsReport(sales.get("today"), "2026-11-12", "2026-11-12");
    assertSameAsReport(sales.get("month_to_date"), "2026-11-01", "2026-11-12");
    assertSameAsReport(sales.get("previous_month_to_date"), "2026-10-01", "2026-10-12");
    var daily = sales.get("daily");
    assertThat(daily).hasSize(12);
    for (var day : daily) {
      var date = day.get("business_date").asString();
      assertSameAsReport(day, date, date);
    }
    assertThat(daily.get(0).get("business_date").asString()).isEqualTo("2026-11-01");
    assertTotals(daily.get(1), 0, 0);
  }

  @Test
  @DisplayName("前月に同じ日が無ければ、前月同時点は前月末で切れる（3/31 → 2/28）")
  void previousMonthToDateEndsAtTheEndOfAShorterMonth() throws Exception {
    clock.set(Instant.parse("2026-03-30T20:00:00Z")); // 東京 3/31 05:00
    order(store, LocalDate.of(2026, 2, 1), "COMPLETED", false, 1_000);
    order(store, LocalDate.of(2026, 2, 28), "COMPLETED", false, 2_000);
    order(store, LocalDate.of(2026, 3, 1), "COMPLETED", false, 4_000);

    JsonNode sales = sales();

    assertThat(sales.get("business_date").asString()).isEqualTo("2026-03-31");
    assertTotals(sales.get("previous_month_to_date"), 3_000, 2);
    assertTotals(sales.get("month_to_date"), 4_000, 1);
    assertThat(sales.get("daily")).hasSize(31);
  }

  @Test
  @DisplayName("日付変更時刻の直前は前の営業日を今日とし、月の境目なら前の月を数える")
  void beforeDateChangeTimeTodayIsThePreviousBusinessDate() throws Exception {
    clock.set(Instant.parse("2026-11-30T19:59:59Z")); // 東京 12/1 04:59:59
    order(store, LocalDate.of(2026, 11, 30), "COMPLETED", false, 3_000);
    order(store, LocalDate.of(2026, 12, 1), "COMPLETED", false, 6_000);

    JsonNode sales = sales();

    assertThat(sales.get("business_date").asString()).isEqualTo("2026-11-30");
    assertTotals(sales.get("today"), 3_000, 1);
    assertThat(sales.get("daily")).hasSize(30);
  }

  @Test
  @DisplayName("区画の権限が無ければ、運営集計の権限があっても 403")
  void withoutRegionPermissionIsForbidden() throws Exception {
    authenticate("PERM_OPERATIONAL_REPORT_VIEW", "PERM_ORDER_MANAGE");

    mvc.perform(get("/dashboard/sales").header("X-Store-ID", store))
        .andExpect(status().isForbidden());
  }

  private JsonNode sales() throws Exception {
    return body(get("/dashboard/sales").header("X-Store-ID", store));
  }

  private void assertSameAsReport(JsonNode totals, String from, String to) throws Exception {
    var report =
        body(
            get("/operational-reports")
                .header("X-Store-ID", store)
                .param("from", from)
                .param("to", to)
                .param("group_by", "store"));
    assertThat(totals.get("sales").asLong()).isEqualTo(report.get("total_fee").asLong());
    assertThat(totals.get("order_count").asLong())
        .isEqualTo(report.get("total_order_count").asLong());
  }

  private static void assertTotals(JsonNode totals, long sales, long orderCount) {
    assertThat(totals.get("sales").asLong()).isEqualTo(sales);
    assertThat(totals.get("order_count").asLong()).isEqualTo(orderCount);
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

  private long store(String name) {
    return jdbc.queryForObject(
        "insert into t_stores(name,short_name,domain) values (?,?,?) returning id",
        Long.class,
        name,
        StoreShortNames.next(),
        UUID.randomUUID() + ".example.test");
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
    new WebBindingConfig(
            new PageableHandlerMethodArgumentResolver(),
            new CurrentStoreArgumentResolver(new StoreScopeHolder(), id -> true))
        .addArgumentResolvers(resolvers);
    return resolvers.toArray(HandlerMethodArgumentResolver[]::new);
  }

  @Configuration
  @EnableTransactionManagement
  @EnableMethodSecurity
  @Import({
    SalesController.class,
    SalesService.class,
    StoreSalesReader.class,
    ReportSnapshot.class,
    OperationalReportReader.class,
    OperationalReportController.class,
    OperationalReportService.class,
    ReportRenderer.class,
    BusinessDateService.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return IntegrationTestDatabase.dataSource(SalesIntegrationTest.class);
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
