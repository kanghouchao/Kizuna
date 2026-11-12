package com.kizuna.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.dashboard.api.TodayOrdersController;
import com.kizuna.dashboard.application.TodayOrdersService;
import com.kizuna.order.dashboard.OrderDashboardReader;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.shared.storescope.CurrentStoreArgumentResolver;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.shared.web.WebBindingConfig;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/** 実 PostgreSQL で、今日の受注の区画が現在店舗の・今日の営業日の受注だけを状態別に数えることを確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TodayOrdersIntegrationTest {
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
        MockMvcBuilders.standaloneSetup(context.getBean(TodayOrdersController.class))
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
    authenticate("PERM_DASHBOARD_TODAY_ORDERS_VIEW");
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("受注の一覧の権限が無くても、区画の権限で現在店舗の今日の受注を状態別に数える")
  void countsTodaysOrdersOfCurrentStoreByStatus() throws Exception {
    order(store, TODAY, "CONFIRMED", false);
    order(store, TODAY, "CONFIRMED", false);
    order(store, TODAY, "IN_SERVICE", false);
    order(store, TODAY, "COMPLETED", false);
    order(store, TODAY, "COMPLETED", false);
    order(store, TODAY, "COMPLETED", false);
    order(store, TODAY, "CANCELLED", false);
    // 数えないもの: 誤完了の無効化を受けた完了・前日と翌日の営業日・他店
    order(store, TODAY, "COMPLETED", true);
    order(store, TODAY.minusDays(1), "CONFIRMED", false);
    order(store, TODAY.plusDays(1), "CONFIRMED", false);
    order(otherStore, TODAY, "CONFIRMED", false);
    order(otherStore, TODAY, "COMPLETED", false);

    JsonNode body = read(store);

    assertThat(body.get("business_date").asString()).isEqualTo("2026-11-12");
    assertThat(body.get("confirmed").asLong()).isEqualTo(2);
    assertThat(body.get("in_service").asLong()).isEqualTo(1);
    assertThat(body.get("completed").asLong()).isEqualTo(3);
    assertThat(body.get("cancelled").asLong()).isEqualTo(1);
  }

  @Test
  @DisplayName("日付変更時刻の直前は前の営業日の受注を数える")
  void beforeDateChangeTimeCountsPreviousBusinessDate() throws Exception {
    order(store, TODAY.minusDays(1), "CONFIRMED", false);
    order(store, TODAY, "CONFIRMED", false);
    order(store, TODAY, "CONFIRMED", false);
    clock.set(Instant.parse("2026-11-11T19:59:59Z")); // 東京 11/12 04:59:59

    JsonNode body = read(store);

    assertThat(body.get("business_date").asString()).isEqualTo("2026-11-11");
    assertThat(body.get("confirmed").asLong()).isEqualTo(1);
  }

  @Test
  @DisplayName("今日の受注が無い状態は 0 で返す")
  void noOrdersAreZero() throws Exception {
    order(store, TODAY.minusDays(1), "COMPLETED", false);

    JsonNode body = read(store);

    assertThat(body.get("confirmed").asLong()).isZero();
    assertThat(body.get("in_service").asLong()).isZero();
    assertThat(body.get("completed").asLong()).isZero();
    assertThat(body.get("cancelled").asLong()).isZero();
  }

  @Test
  @DisplayName("区画の権限が無ければ、受注の一覧の権限があっても 403")
  void withoutRegionPermissionIsForbidden() throws Exception {
    authenticate("PERM_ORDER_MANAGE");

    mvc.perform(get("/dashboard/today-orders").header("X-Store-ID", store))
        .andExpect(status().isForbidden());
  }

  private JsonNode read(long storeId) throws Exception {
    var response =
        mvc.perform(get("/dashboard/today-orders").header("X-Store-ID", storeId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
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

  private void order(long storeId, LocalDate businessDate, String status, boolean invalidated) {
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
        "insert into t_orders(id,store_id,business_date,course_name,course_minutes,course_service_id,course_revision_id,course_revision_number,course_price,course_remuneration,course_adoption_basis,course_adopted_at,status,completed_at,completion_invalidated,total_fee) values (?,?,?,'検証コース',60,?,?,1,100,0,'CURRENT_SETTING',current_timestamp,?,case when ? = 'COMPLETED' then current_timestamp end,?,0)",
        UUID.randomUUID().toString(),
        storeId,
        businessDate,
        course,
        revision,
        status,
        status,
        invalidated);
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
    TodayOrdersController.class,
    TodayOrdersService.class,
    OrderDashboardReader.class,
    BusinessDateService.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return IntegrationTestDatabase.dataSource(TodayOrdersIntegrationTest.class);
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
