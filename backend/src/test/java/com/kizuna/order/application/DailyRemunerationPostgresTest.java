package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentStatus;
import com.kizuna.cast.domain.CastProfile;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.order.api.platform.PlatformSelfDailyRemunerationController;
import com.kizuna.order.api.store.DailyRemunerationController;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCourse;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.infrastructure.RemunerationQuery;
import com.kizuna.order.infrastructure.SelfMonthlyRemunerationQuery;
import com.kizuna.shared.persistence.StoreScopeStampListener;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreFilterEnable;
import com.kizuna.store.domain.Store;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/** 独立した検証DBで実サービスの読み取り取引と別接続の変更を交差させる。 */
@EnabledIfEnvironmentVariable(named = "KIZUNA_DAILY_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DailyRemunerationPostgresTest {
  private AnnotationConfigApplicationContext context;
  private EntityManager em;
  private JdbcTemplate jdbc;
  private TransactionTemplate transactions;
  private DailyRemunerationService service;
  private Long storeId;
  private Long personId;
  private String email;
  private String orderId;
  private static final LocalDate DAY = LocalDate.of(2026, 9, 30);

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    em = context.getBean(EntityManager.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    service = context.getBean(DailyRemunerationService.class);
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @BeforeEach
  void fixture() {
    transactions.executeWithoutResult(
        status -> {
          var store = new Store("日別検証店", UUID.randomUUID() + ".example.test", null);
          em.persist(store);
          storeId = store.getId();
          context.getBean(StoreContext.class).setStoreId(storeId);
          email = UUID.randomUUID() + "@example.test";
          var actor =
              PlatformUser.builder()
                  .email(email)
                  .password(UUID.randomUUID().toString())
                  .displayName("本人")
                  .userType(UserType.CAST)
                  .enabled(true)
                  .roleIds(Set.of())
                  .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                  .storeIds(Set.of(storeId))
                  .build();
          em.persist(actor);
          var person = Cast.builder().platformUserId(actor.getId()).build();
          em.persist(person);
          personId = person.getId();
          String first = enrollment(CastEnrollmentStatus.WITHDRAWN);
          String second = enrollment(CastEnrollmentStatus.SUSPENDED);
          orderId = order(first, DAY, OrderStatus.COMPLETED, 7000, false);
          order(second, DAY, OrderStatus.COMPLETED, 9000, false);
          order(second, DAY, OrderStatus.COMPLETED, 5000, true);
          order(second, DAY, OrderStatus.CONFIRMED, 0, false);
          order(second, DAY, OrderStatus.CANCELLED, 0, false);
          order(second, DAY.plusDays(1), OrderStatus.COMPLETED, 2000, false);
          order(second, LocalDate.of(9999, 12, 31), OrderStatus.COMPLETED, 3000, false);
        });
  }

  private String enrollment(CastEnrollmentStatus status) {
    var enrollment = CastEnrollment.builder().castId(personId).status(status).build();
    em.persist(enrollment);
    var profile = CastProfile.builder().enrollmentId(enrollment.getId()).name("保存された源氏名").build();
    em.persist(profile);
    return enrollment.getId();
  }

  private String order(
      String enrollment, LocalDate day, OrderStatus status, int amount, boolean invalidated) {
    var order =
        Order.builder()
            .course(
                new OrderCourse(
                    "course",
                    "revision",
                    1L,
                    "保存コース",
                    60,
                    15000,
                    amount,
                    "CURRENT_SETTING",
                    OffsetDateTime.parse("2026-09-30T10:00:00+09:00")))
            .castId(enrollment)
            .businessDate(day)
            .status(status)
            .accruedRemuneration(amount)
            .completionInvalidated(invalidated)
            .completedAt(OffsetDateTime.parse("2026-10-01T01:00:00+09:00"))
            .build();
    em.persist(order);
    return order.getId();
  }

  @Test
  void bothReadsAggregateHistoricalEnrollmentsAndKeepInvalidatedRows() {
    var store = service.store(personId, DAY.toString(), 0, 1);
    var self = service.self(email, storeId, DAY.toString(), 0, 1);
    assertThat(store.totalRemuneration()).isEqualTo(16000);
    assertThat(self.totalRemuneration()).isEqualTo(16000);
    assertThat(store.orders().getTotalElements()).isEqualTo(3);
    assertThat(store.orders().getContent()).hasSize(1);
    var all = service.store(personId, DAY.toString(), 0, 20);
    assertThat(all.orders().stream().mapToInt(row -> row.accruedRemuneration()).sum())
        .isEqualTo(16000);
    assertThat(all.orders().stream().filter(row -> row.completionInvalidated()))
        .singleElement()
        .satisfies(row -> assertThat(row.accruedRemuneration()).isZero());
    assertThat(service.store(personId, DAY.toString(), 10, 20).orders()).isEmpty();
    assertThat(service.store(personId, "0001-01-01", 0, 20).totalRemuneration()).isZero();
    assertThat(service.store(personId, "9999-12-31", 0, 20).totalRemuneration()).isEqualTo(3000);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void httpResponseKeepsOneSnapshotAcrossConcurrentChange(boolean self) throws Exception {
    var probe = context.getBean(SnapshotQuery.class);
    probe.reached = new CountDownLatch(1);
    probe.resume = new CountDownLatch(1);
    var http =
        standaloneSetup(
                new DailyRemunerationController(service),
                new PlatformSelfDailyRemunerationController(service))
            .setMessageConverters(
                new JacksonJsonHttpMessageConverter(
                    JsonMapper.builder()
                        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                        .build()))
            .build();
    try (var executor = Executors.newSingleThreadExecutor()) {
      var reading =
          executor.submit(
              () -> {
                context.getBean(StoreContext.class).setStoreId(storeId);
                try {
                  return http.perform(
                          get(self
                                  ? "/platform/me/daily-remunerations"
                                  : "/store/daily-remunerations")
                              .principal(() -> email)
                              .param("person_id", personId.toString())
                              .param("store_id", storeId.toString())
                              .param("business_date", DAY.toString()))
                      .andReturn()
                      .getResponse();
                } finally {
                  context.getBean(StoreContext.class).clear();
                }
              });
      try {
        assertThat(probe.reached.await(10, TimeUnit.SECONDS)).isTrue();
        transactions.executeWithoutResult(
            status -> {
              Integer writer = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
              assertThat(writer).isNotEqualTo(probe.readerPid);
              jdbc.update(
                  "update t_orders set accrued_remuneration=11000, version=version+1 where id=?",
                  orderId);
            });
      } finally {
        probe.resume.countDown();
      }
      var response = reading.get(15, TimeUnit.SECONDS);
      assertThat(response.getStatus()).isEqualTo(200);
      var json = new ObjectMapper().readTree(response.getContentAsString());
      assertThat(json.get("total_remuneration").asLong()).isEqualTo(16000);
      long sum = 0;
      for (var row : json.get("orders").get("content"))
        sum += row.get("accrued_remuneration").asLong();
      assertThat(sum).isEqualTo(16000);
    } finally {
      probe.reached = null;
      probe.resume = null;
    }
    assertThat(service.store(personId, DAY.toString(), 0, 20).totalRemuneration()).isEqualTo(20000);
    assertThat(service.self(email, storeId, DAY.toString(), 0, 20).totalRemuneration())
        .isEqualTo(20000);
  }

  static class SnapshotQuery extends RemunerationQuery {
    private final EntityManager em;
    volatile CountDownLatch reached;
    volatile CountDownLatch resume;
    volatile Integer readerPid;

    SnapshotQuery(EntityManager em) {
      super(em);
      this.em = em;
    }

    @Override
    public long dailyTotal(Long storeId, Long personId, LocalDate day) {
      long total = super.dailyTotal(storeId, personId, day);
      var gate = reached;
      if (gate != null) {
        readerPid =
            ((Number)
                    em.createNativeQuery("select pg_backend_pid()", Integer.class)
                        .getSingleResult())
                .intValue();
        gate.countDown();
        try {
          if (!resume.await(10, TimeUnit.SECONDS))
            throw new IllegalStateException("並行更新が完了しませんでした");
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(ex);
        }
      }
      return total;
    }
  }

  @Configuration
  @EnableTransactionManagement
  @EnableAspectJAutoProxy
  @Import({
    DailyRemunerationService.class,
    SelfMonthlyRemunerationQuery.class,
    ActorIdentityService.class,
    StoreScopeStampListener.class,
    StoreFilterEnable.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
          System.getenv("KIZUNA_DAILY_TEST_JDBC_URL"), "postgres", "");
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource source, ConfigurableListableBeanFactory beans) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(source);
      factory.setPackagesToScan(
          "com.kizuna.order.domain",
          "com.kizuna.cast.domain",
          "com.kizuna.store.domain",
          "com.kizuna.user.domain");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(
          Map.of(
              "hibernate.hbm2ddl.auto",
              "create-drop",
              "hibernate.resource.beans.container",
              new SpringBeanContainer(beans),
              "hibernate.physical_naming_strategy",
              "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
      return factory;
    }

    @Bean
    @Primary
    EntityManager entityManager(EntityManagerFactory f) {
      return SharedEntityManagerCreator.createSharedEntityManager(f);
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory f) {
      return new JpaTransactionManager(f);
    }

    @Bean
    StoreContext storeContext() {
      return new StoreContext();
    }

    @Bean
    SnapshotQuery query(EntityManager em) {
      return new SnapshotQuery(em);
    }

    @Bean
    CastRepository people(EntityManager em) {
      return new JpaRepositoryFactory(em).getRepository(CastRepository.class);
    }

    @Bean
    PlatformUserRepository users(EntityManager em) {
      return new JpaRepositoryFactory(em).getRepository(PlatformUserRepository.class);
    }
  }
}
