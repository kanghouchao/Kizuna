package com.kizuna.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.dashboard.api.PendingOrderApplicationsController;
import com.kizuna.dashboard.api.PendingReviewsController;
import com.kizuna.dashboard.api.PendingShiftRequestsController;
import com.kizuna.dashboard.application.PendingWorkService;
import com.kizuna.order.dashboard.OrderDashboardReader;
import com.kizuna.review.dashboard.ReviewDashboardReader;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.shared.storescope.CurrentStoreArgumentResolver;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.shared.web.WebBindingConfig;
import com.kizuna.shift.dashboard.ShiftDashboardReader;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

/** 実 PostgreSQL で、やることの三区画が現在店舗の未処理だけを、それぞれの失効の扱いで数えることを確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PendingWorkIntegrationTest {
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
                context.getBean(PendingOrderApplicationsController.class),
                context.getBean(PendingShiftRequestsController.class),
                context.getBean(PendingReviewsController.class))
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
    authenticate(
        "PERM_DASHBOARD_PENDING_ORDER_APPLICATIONS_VIEW",
        "PERM_DASHBOARD_PENDING_SHIFT_REQUESTS_VIEW",
        "PERM_DASHBOARD_PENDING_REVIEWS_VIEW");
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("予約申請は希望営業日が今日以降の PENDING だけを数え、希望日を過ぎたもの・処理済み・他店は数えない")
  void pendingOrderApplicationsExcludeExpired() throws Exception {
    application(store, TODAY, "PENDING");
    application(store, TODAY.plusDays(1), "PENDING");
    application(store, TODAY.plusDays(30), "PENDING");
    // 数えないもの: 失効（希望日が昨日）・処理済み・他店
    application(store, TODAY.minusDays(1), "PENDING");
    application(store, TODAY, "CONFIRMED");
    application(store, TODAY, "DECLINED");
    application(store, TODAY, "WITHDRAWN");
    application(otherStore, TODAY, "PENDING");

    JsonNode body = read("/dashboard/pending-order-applications", store);

    assertThat(body.get("business_date").asString()).isEqualTo("2026-11-12");
    assertThat(body.get("count").asLong()).isEqualTo(3);
  }

  @Test
  @DisplayName("日付変更時刻の直前は、前の営業日を希望する申請がまだ失効していない")
  void beforeDateChangeTimePreviousBusinessDateIsNotExpired() throws Exception {
    application(store, TODAY.minusDays(1), "PENDING");
    application(store, TODAY.minusDays(2), "PENDING");
    clock.set(Instant.parse("2026-11-11T19:59:59Z")); // 東京 11/12 04:59:59

    JsonNode body = read("/dashboard/pending-order-applications", store);

    assertThat(body.get("business_date").asString()).isEqualTo("2026-11-11");
    assertThat(body.get("count").asLong()).isEqualTo(1);
  }

  @Test
  @DisplayName("出勤希望は目標営業日を過ぎた PENDING も数え、処理済み・他店は数えない")
  void pendingShiftRequestsIncludePastTargetDate() throws Exception {
    String cast = cast(store);
    shiftRequest(store, cast, TODAY, "PENDING");
    shiftRequest(store, cast, TODAY.plusDays(7), "PENDING");
    shiftRequest(store, cast, TODAY.minusDays(3), "PENDING");
    // 数えないもの: 処理済み・他店
    shiftRequest(store, cast, TODAY, "APPROVED");
    shiftRequest(store, cast, TODAY, "DECLINED");
    shiftRequest(otherStore, cast(otherStore), TODAY, "PENDING");

    JsonNode body = read("/dashboard/pending-shift-requests", store);

    assertThat(body.get("count").asLong()).isEqualTo(3);
  }

  @Test
  @DisplayName("口コミは審査待ち（PENDING）だけを数え、審査済み・取り下げ・他店は数えない")
  void pendingReviewsCountOnlyPending() throws Exception {
    review(store, "PENDING");
    review(store, "PENDING");
    // 数えないもの: 審査済み・取り下げ・他店
    review(store, "APPROVED");
    review(store, "REJECTED");
    review(store, "WITHDRAWN");
    review(otherStore, "PENDING");

    JsonNode body = read("/dashboard/pending-reviews", store);

    assertThat(body.get("count").asLong()).isEqualTo(2);
  }

  @ParameterizedTest
  @CsvSource({
    "/dashboard/pending-order-applications, PERM_ORDER_MANAGE",
    "/dashboard/pending-shift-requests, PERM_SHIFT_MANAGE",
    "/dashboard/pending-reviews, PERM_REVIEW_VIEW"
  })
  @DisplayName("区画の権限が無ければ、一覧の権限があっても 403")
  void withoutRegionPermissionIsForbidden(String path, String listPermission) throws Exception {
    authenticate(listPermission);

    mvc.perform(get(path).header("X-Store-ID", store)).andExpect(status().isForbidden());
  }

  private JsonNode read(String path, long storeId) throws Exception {
    var response =
        mvc.perform(get(path).header("X-Store-ID", storeId))
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

  private void application(long storeId, LocalDate businessDate, String status) {
    jdbc.update(
        "insert into t_order_applications(id,store_id,status,business_date,requester_declared_name) values (?,?,?,?,'名乗り')",
        UUID.randomUUID().toString(),
        storeId,
        status,
        businessDate);
  }

  private String cast(long storeId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_cast_enrollments(id,store_id,started_on) values (?,?,?)",
        id,
        storeId,
        TODAY.minusYears(1));
    return id;
  }

  private void shiftRequest(long storeId, String castId, LocalDate workDate, String status) {
    jdbc.update(
        "insert into t_shift_requests(id,store_id,cast_id,work_date,start_time,end_time,status,request_type) values (?,?,?,?,'18:00','23:00',?,'NEW')",
        UUID.randomUUID().toString(),
        storeId,
        castId,
        workDate,
        status);
  }

  private void review(long storeId, String status) {
    jdbc.update(
        "insert into t_reviews(id,store_id,body,received_via,received_at,recorded_by,recorder_name,status,permission_status) values (?,?,'本文','VERBAL',current_timestamp,?,'検証担当',?,'NOT_GRANTED')",
        UUID.randomUUID().toString(),
        storeId,
        staff,
        status);
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
    PendingOrderApplicationsController.class,
    PendingShiftRequestsController.class,
    PendingReviewsController.class,
    PendingWorkService.class,
    OrderDashboardReader.class,
    ShiftDashboardReader.class,
    ReviewDashboardReader.class,
    BusinessDateService.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return IntegrationTestDatabase.dataSource(PendingWorkIntegrationTest.class);
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
