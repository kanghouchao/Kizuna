package com.kizuna.remuneration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentStatus;
import com.kizuna.cast.domain.CastProfile;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.cast.remuneration.RemunerationPersonLookup;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCourse;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.infrastructure.RemunerationQuery;
import com.kizuna.order.remuneration.OrderRemunerationFacts;
import com.kizuna.remuneration.api.dto.RemunerationRequests.BonusCorrectionRequest;
import com.kizuna.remuneration.api.dto.RemunerationRequests.BonusCreateRequest;
import com.kizuna.remuneration.api.dto.RemunerationRequests.CancellationRequest;
import com.kizuna.remuneration.api.dto.RemunerationRequests.GuaranteeCorrectionRequest;
import com.kizuna.remuneration.api.dto.RemunerationRequests.GuaranteeCreateRequest;
import com.kizuna.remuneration.domain.DailyGuarantee.Status;
import com.kizuna.remuneration.domain.GuaranteeState;
import com.kizuna.remuneration.infrastructure.RemunerationRecords;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.persistence.StoreScopeStampListener;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreFilterEnable;
import com.kizuna.shift.domain.Attendance;
import com.kizuna.shift.remuneration.AttendanceFacts;
import com.kizuna.store.domain.Store;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfEnvironmentVariable(named = "KIZUNA_REMUNERATION_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RemunerationPostgresTest {
  private AnnotationConfigApplicationContext context;
  private EntityManager em;
  private JdbcTemplate jdbc;
  private TransactionTemplate transactions;
  private RemunerationManagementService management;
  private RemunerationStatementService statements;
  private Long storeId, personId;
  private String email, orderId, attendanceId;
  private static final LocalDate DAY = LocalDate.of(2026, 9, 30);

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    em = context.getBean(EntityManager.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    management = context.getBean(RemunerationManagementService.class);
    statements = context.getBean(RemunerationStatementService.class);
    jdbc.execute(
        "CREATE UNIQUE INDEX uq_test_timeline ON t_guarantee_timelines(store_id,person_id)");
    jdbc.execute(
        "CREATE UNIQUE INDEX uq_test_term ON t_guarantee_terms(store_id,person_id,effective_from) WHERE cancelled_at IS NULL");
    jdbc.execute(
        "CREATE UNIQUE INDEX uq_test_request ON t_remuneration_requests(store_id,actor_id,request_id)");
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
    SecurityContextHolder.clearContext();
  }

  private void authorize() {
    context.getBean(StoreContext.class).setStoreId(storeId);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                email, "", List.of(new SimpleGrantedAuthority("PERM_REMUNERATION_CORRECT"))));
  }

  @BeforeEach
  void fixture() {
    transactions.executeWithoutResult(
        tx -> {
          var store = new Store("保証検証店", UUID.randomUUID() + ".example.test", null);
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
          String first = enrollment(CastEnrollmentStatus.WITHDRAWN),
              second = enrollment(CastEnrollmentStatus.SUSPENDED);
          orderId = order(first, DAY, OrderStatus.COMPLETED, 7000, false);
          var a =
              Attendance.record(
                  first,
                  null,
                  DAY,
                  DAY.atTime(22, 0),
                  DAY.plusDays(1).atTime(1, 0),
                  null,
                  actor.getId());
          em.persist(a);
          attendanceId = a.getId();
          em.persist(
              Attendance.record(
                  second,
                  null,
                  DAY,
                  DAY.atTime(23, 0),
                  DAY.plusDays(1).atTime(2, 0),
                  null,
                  actor.getId()));
        });
    authorize();
  }

  private GuaranteeCreateRequest guarantee(long amount, long version, UUID id) {
    return new GuaranteeCreateRequest(
        personId, DAY, GuaranteeState.ACTIVE, amount, "保証条件", version, id);
  }

  @Test
  void dailyUnionAndBonusAreVisibleAcrossHistoricalEnrollments() {
    management.createGuarantee(guarantee(10000, 0, UUID.randomUUID()), email);
    management.createBonus(
        new BonusCreateRequest(personId, DAY, 1500, "付与", UUID.randomUUID()), email);
    var store = statements.store(personId, "2026-09", 0, 0, 20);
    var self = statements.self(email, storeId, "2026-09", 0, 0, 20);
    assertThat(store.guaranteeTotal()).isEqualTo(3000);
    assertThat(store.total()).isEqualTo(11500);
    assertThat(self.total()).isEqualTo(store.total());
    assertThat(store.days().getLast().closedDuration()).isEqualTo("PT4H");
    assertThat(store.orders().getTotalElements()).isEqualTo(1);
    assertThat(statements.self(email, storeId, "2026-10", 0, 0, 20).total()).isZero();
  }

  @Test
  void cancelledAttendanceAndStoppedTermRecalculateWithoutBlockingTheMonth() {
    management.createGuarantee(guarantee(10000, 0, UUID.randomUUID()), email);
    jdbc.update("update t_attendances set actual_end_at=null where id=?", attendanceId);
    assertThat(statements.store(personId, "2026-09", 0, 0, 20).guaranteeTotal()).isNull();
    var term = management.guarantees(personId, 0, 20).entries().getContent().getFirst();
    management.correctGuarantee(
        term.id(),
        new GuaranteeCorrectionRequest(
            DAY, GuaranteeState.STOPPED, null, "停止", "停止日訂正", 1L, UUID.randomUUID()),
        email);
    var stopped = statements.store(personId, "2026-09", 0, 0, 20);
    assertThat(stopped.guaranteeTotal()).isZero();
    assertThat(stopped.days().getLast().attendanceIncomplete()).isTrue();
    assertThat(stopped.days().getLast().guaranteeStatus()).isEqualTo(Status.STOPPED);
  }

  @Test
  void guaranteeTimelineAndBonusCorrectionsRetainHistoryAndRejectStaleVersions() {
    var created = management.createGuarantee(guarantee(10000, 0, UUID.randomUUID()), email);
    assertThatThrownBy(
            () ->
                management.createGuarantee(
                    new GuaranteeCreateRequest(
                        personId,
                        DAY.plusDays(1),
                        GuaranteeState.STOPPED,
                        null,
                        "停止",
                        0L,
                        UUID.randomUUID()),
                    email))
        .isInstanceOf(ConflictException.class);
    var corrected =
        management.correctGuarantee(
            created.guarantee().id(),
            new GuaranteeCorrectionRequest(
                DAY, GuaranteeState.ACTIVE, 12000L, "日額", "転記訂正", 1L, UUID.randomUUID()),
            email);
    assertThat(corrected.version()).isEqualTo(2);
    assertThat(management.guaranteeChanges(created.guarantee().id(), null, 20).content())
        .hasSize(2);
    var bonus =
        management
            .createBonus(
                new BonusCreateRequest(personId, DAY, 1000, "付与", UUID.randomUUID()), email)
            .bonus();
    var changed =
        management
            .correctBonus(
                bonus.id(),
                new BonusCorrectionRequest(
                    DAY, 2000, "付与", "金額訂正", bonus.version(), UUID.randomUUID()),
                email)
            .bonus();
    management.cancelBonus(
        changed.id(), new CancellationRequest("誤記取消", changed.version(), UUID.randomUUID()), email);
    assertThat(management.bonusChanges(bonus.id(), null, 20).content()).hasSize(3);
    assertThat(statements.store(personId, "2026-09", 0, 0, 20).bonusTotal()).isZero();
    management.cancelGuarantee(
        created.guarantee().id(), new CancellationRequest("誤記取消", 2L, UUID.randomUUID()), email);
    assertThat(statements.store(personId, "2026-09", 0, 0, 20).guaranteeTotal()).isNull();
  }

  @Test
  void allPersonAndResourcePathsRejectForeignStore() {
    var bonus =
        management
            .createBonus(
                new BonusCreateRequest(personId, DAY, 1000, "付与", UUID.randomUUID()), email)
            .bonus();
    var term =
        management.createGuarantee(guarantee(10000, 0, UUID.randomUUID()), email).guarantee();
    context.getBean(StoreContext.class).setStoreId(storeId + 100000);
    assertThatThrownBy(
            () ->
                management.createBonus(
                    new BonusCreateRequest(personId, DAY, 1000, "付与", UUID.randomUUID()), email))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> management.guarantees(personId, 0, 20))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> management.bonusChanges(bonus.id(), null, 20))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> management.guaranteeChanges(term.id(), null, 20))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                management.cancelBonus(
                    bonus.id(),
                    new CancellationRequest("取消", bonus.version(), UUID.randomUUID()),
                    email))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> statements.self(email, storeId + 100000, "2026-09", 0, 0, 20))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void concurrentSameRequestProducesOneAwardAndOneChange() throws Exception {
    var request = new BonusCreateRequest(personId, DAY, 1000, "付与", UUID.randomUUID());
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                authorize();
                return management.createBonus(request, email);
              });
      var second =
          pool.submit(
              () -> {
                authorize();
                return management.createBonus(request, email);
              });
      assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
    }
    assertThat(management.bonuses(personId, "2026-09", 0, 20).getTotalElements()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_remuneration_changes where store_id=?",
                Long.class,
                storeId))
        .isEqualTo(1);
    assertThatThrownBy(
            () ->
                management.createBonus(
                    new BonusCreateRequest(personId, DAY, 2000, "付与", request.requestId()), email))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void competingFirstTermsCannotBothConsumeVersionZero() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      var results =
          List.of(pool.submit(() -> attemptFirst(start)), pool.submit(() -> attemptFirst(start)));
      start.countDown();
      int succeeded = 0;
      for (var result : results) if (result.get(10, TimeUnit.SECONDS)) succeeded++;
      assertThat(succeeded).isEqualTo(1);
    }
    assertThat(management.guarantees(personId, 0, 20).version()).isEqualTo(1);
  }

  private boolean attemptFirst(CountDownLatch start) throws Exception {
    start.await();
    authorize();
    try {
      management.createGuarantee(guarantee(10000, 0, UUID.randomUUID()), email);
      return true;
    } catch (ConflictException e) {
      return false;
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void oneSnapshotContainsOrderAttendanceAndGuaranteeDespiteAnotherConnectionCommit(boolean self)
      throws Exception {
    var term =
        management.createGuarantee(guarantee(10000, 0, UUID.randomUUID()), email).guarantee();
    var facts = context.getBean(SnapshotFacts.class);
    facts.reached = new CountDownLatch(1);
    facts.resume = new CountDownLatch(1);
    try (var pool = Executors.newSingleThreadExecutor()) {
      var result =
          pool.submit(
              () -> {
                authorize();
                return self
                    ? statements.self(email, storeId, "2026-09", 0, 0, 20)
                    : statements.store(personId, "2026-09", 0, 0, 20);
              });
      assertThat(facts.reached.await(10, TimeUnit.SECONDS)).isTrue();
      jdbc.update("update t_orders set accrued_remuneration=9000 where id=?", orderId);
      jdbc.update("update t_guarantee_terms set daily_amount=20000 where id=?", term.id());
      jdbc.update("update t_attendances set actual_end_at=null where id=?", attendanceId);
      facts.resume.countDown();
      var old = result.get(10, TimeUnit.SECONDS);
      assertThat(old.orderTotal()).isEqualTo(7000);
      assertThat(old.guaranteeTotal()).isEqualTo(3000);
      assertThat(old.orders().getContent().getFirst().accruedRemuneration()).isEqualTo(7000);
    } finally {
      facts.resume.countDown();
      facts.reached = null;
    }
    assertThat(statements.store(personId, "2026-09", 0, 0, 20).guaranteeTotal()).isNull();
  }

  static class SnapshotFacts extends OrderRemunerationFacts {
    volatile CountDownLatch reached, resume;

    SnapshotFacts(RemunerationQuery query) {
      super(query);
    }

    @Override
    public Map<LocalDate, Long> daily(Long store, Long person, YearMonth month) {
      var result = super.daily(store, person, month);
      var gate = reached;
      if (gate != null) {
        gate.countDown();
        try {
          if (!resume.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("書込が完了しません");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        }
      }
      return result;
    }
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

  @Configuration
  @EnableTransactionManagement
  @EnableAspectJAutoProxy
  @Import({
    RemunerationManagementService.class,
    RemunerationStatementService.class,
    RemunerationRecords.class,
    RemunerationPersonLookup.class,
    AttendanceFacts.class,
    BusinessDateService.class,
    ActorIdentityService.class,
    StoreScopeStampListener.class,
    StoreFilterEnable.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
          System.getenv("KIZUNA_REMUNERATION_TEST_JDBC_URL"), "postgres", "");
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource source, ConfigurableListableBeanFactory beans) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(source);
      factory.setPackagesToScan(
          "com.kizuna.order.domain",
          "com.kizuna.remuneration.domain",
          "com.kizuna.shift.domain",
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
    RemunerationQuery query(EntityManager em) {
      return new RemunerationQuery(em);
    }

    @Bean
    SnapshotFacts facts(RemunerationQuery query) {
      return new SnapshotFacts(query);
    }

    @Bean
    BusinessAudit audit() {
      return mock(BusinessAudit.class);
    }

    @Bean
    SystemConfigService config() {
      return mock(SystemConfigService.class);
    }

    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    ObjectMapper mapper() {
      return JsonMapper.builder().build();
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
