package com.kizuna.task.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditActor;
import com.kizuna.audit.recording.AuditReader;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.task.application.TaskLifecycle;
import com.kizuna.task.application.TaskQuery;
import com.kizuna.task.application.TaskRegistry;
import com.kizuna.task.domain.ExecutionRequestRepository;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import com.kizuna.user.domain.PermissionCode;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

// 専用の使い捨て PostgreSQL で、二つの独立した JPA コンテキスト間の実ロックを検証する。
@EnabledIfEnvironmentVariable(named = "KIZUNA_TASK_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskPostgresTest {
  private AnnotationConfigApplicationContext first;
  private AnnotationConfigApplicationContext second;
  private JdbcTemplate jdbc;
  private static final AuditActor SERVICE = new AuditActor(100L, "SERVICE", "検証処理");
  private static final AuditActor OPERATOR = new AuditActor(200L, "STAFF", "検証担当");

  @BeforeAll
  void connect() {
    first = new AnnotationConfigApplicationContext(Config.class);
    second = new AnnotationConfigApplicationContext(Config.class);
    jdbc = first.getBean(JdbcTemplate.class);
    jdbc.execute("create table if not exists t_task_probe (execution_id bigint primary key)");
  }

  @AfterAll
  void close() {
    if (second != null) second.close();
    if (first != null) first.close();
  }

  private TaskCommand command() {
    return new TaskCommand(
        "PROBE",
        UUID.randomUUID().toString(),
        SERVICE.id(),
        null,
        LocalDate.of(2026, 10, 7),
        LocalDate.of(2026, 10, 7));
  }

  @Test
  void failureRollsBackWorkButSurvivesAndRetryGetsANewAttempt() {
    var handler = first.getBean(Probe.class);
    handler.fail = true;
    var command = command();
    var executor = first.getBean(TaskExecutor.class);
    var failed = executor.executeScheduled(command).execution();
    handler.fail = false;
    assertThat(failed.status()).isEqualTo("FAILED");
    assertThat(countProbe(failed.id())).isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_audit_events where target_id = ? and action = 'TASK_FAILED'",
                Long.class,
                failed.id().toString()))
        .isEqualTo(1);
    var succeeded = executor.retry(failed.id(), "原因を修正したため", OPERATOR);
    assertThat(succeeded.status()).isEqualTo("SUCCEEDED");
    assertThat(succeeded.retryOf()).isEqualTo(failed.id());
    assertThat(succeeded.attemptNumber()).isEqualTo(2);
    assertThat(countProbe(succeeded.id())).isEqualTo(1);
    assertThat(executor.executeScheduled(command).execution().id()).isEqualTo(succeeded.id());
    assertThatThrownBy(() -> executor.retry(succeeded.id(), "二重実行", OPERATOR))
        .isInstanceOf(ConflictException.class);
    assertThat(first.getBean(TaskLifecycle.class).get(failed.id()).status()).isEqualTo("FAILED");
  }

  @Test
  void twoInstancesShareTheSameLogicalExecution() throws Exception {
    var command = command();
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                start.await();
                return first.getBean(TaskExecutor.class).executeScheduled(command);
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return second.getBean(TaskExecutor.class).executeScheduled(command);
              });
      start.countDown();
      var left = a.get(10, TimeUnit.SECONDS);
      var right = b.get(10, TimeUnit.SECONDS);
      assertThat(left.execution().id()).isEqualTo(right.execution().id());
      assertThat(left.created()).isNotEqualTo(right.created());
      assertThat(countProbe(left.execution().id())).isEqualTo(1);
    }
    var different =
        new TaskCommand(
            command.taskName(),
            command.logicalKey(),
            101L,
            null,
            command.periodStart(),
            command.periodEnd());
    assertThatThrownBy(() -> first.getBean(TaskExecutor.class).executeScheduled(different))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void activeDatabaseWorkCannotBeInterruptedFromAnotherInstance() throws Exception {
    var probe = first.getBean(Probe.class);
    probe.entered = new CountDownLatch(1);
    probe.release = new CountDownLatch(1);
    var command = command();
    try (var pool = Executors.newSingleThreadExecutor()) {
      var running = pool.submit(() -> first.getBean(TaskExecutor.class).executeScheduled(command));
      try {
        assertThat(probe.entered.await(10, TimeUnit.SECONDS)).isTrue();
        var current = second.getBean(TaskLifecycle.class).replay(command);
        assertThatThrownBy(
                () ->
                    second
                        .getBean(TaskLifecycle.class)
                        .interrupt(current.id(), "状態確認", OPERATOR, 0))
            .isInstanceOf(PessimisticLockingFailureException.class);
      } finally {
        probe.release.countDown();
      }
      assertThat(running.get(10, TimeUnit.SECONDS).execution().status()).isEqualTo("SUCCEEDED");
    } finally {
      probe.entered = null;
      probe.release = null;
    }
  }

  @Test
  void abandonedStartCanBeRecordedAsInterruptedAndRetried() {
    var lifecycle = first.getBean(TaskLifecycle.class);
    var started = lifecycle.begin(command(), SERVICE, null, "SCHEDULED");
    var interrupted =
        second.getBean(TaskLifecycle.class).interrupt(started.id(), "停止を確認", OPERATOR, 0);
    assertThat(interrupted.status()).isEqualTo("INTERRUPTED");
    assertThat(second.getBean(TaskExecutor.class).retry(started.id(), "復旧を確認", OPERATOR).status())
        .isEqualTo("SUCCEEDED");
  }

  @Test
  void permissionRevokedAfterSubmissionPreventsWork() {
    var identities = first.getBean(ServiceExecutionIdentityService.class);
    var calls = new AtomicInteger();
    when(identities.requireService(any(), any(), isNull()))
        .thenAnswer(
            call -> {
              if (calls.incrementAndGet() > 1) throw new AccessDeniedException("停止済み");
              return SERVICE;
            });
    try {
      var result = first.getBean(TaskExecutor.class).executeScheduled(command()).execution();
      assertThat(result.status()).isEqualTo("FAILED");
      assertThat(result.failureCode()).isEqualTo("AUTHORIZATION_DENIED");
      assertThat(countProbe(result.id())).isZero();
    } finally {
      org.mockito.Mockito.reset(identities);
      authorize(identities);
    }
  }

  @Test
  void auditRejectsSqlMutationAndHistoryUsesStableCursor() {
    for (var statement :
        List.of(
            "update t_audit_events set action = 'ALTERED'",
            "delete from t_audit_events",
            "truncate t_audit_events")) {
      assertThatThrownBy(() -> jdbc.execute(statement))
          .isInstanceOf(DataAccessException.class)
          .hasStackTraceContaining("55000");
    }
    first.getBean(TaskExecutor.class).executeScheduled(command());
    first.getBean(TaskExecutor.class).executeScheduled(command());
    var query = first.getBean(TaskQuery.class);
    var page = query.list(null, 1);
    assertThat(page.nextCursor()).isNotNull();
    assertThat(query.list(page.nextCursor(), 1).content().getFirst().id())
        .isNotEqualTo(page.content().getFirst().id());
  }

  private long countProbe(Long id) {
    return jdbc.queryForObject(
        "select count(*) from t_task_probe where execution_id = ?", Long.class, id);
  }

  private static void authorize(ServiceExecutionIdentityService identities) {
    when(identities.requireService(any(), any(), isNull())).thenReturn(SERVICE);
  }

  static class Probe implements TaskHandler {
    private final JdbcTemplate jdbc;
    volatile boolean fail;
    volatile CountDownLatch entered;
    volatile CountDownLatch release;

    Probe(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
    }

    public String name() {
      return "PROBE";
    }

    public PermissionCode permission() {
      return PermissionCode.TASK_EXECUTE;
    }

    public boolean platformWide() {
      return true;
    }

    public long execute(TaskContext context) {
      jdbc.update("insert into t_task_probe (execution_id) values (?)", context.executionId());
      if (entered != null) {
        entered.countDown();
        try {
          if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("検証タイムアウト");
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(ex);
        }
      }
      if (fail) throw new IllegalStateException("公開してはならない内部情報");
      return 1;
    }
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(
      basePackageClasses = {ExecutionRequestRepository.class, AuditEventRepository.class})
  @Import({
    TaskExecutor.class,
    TaskLifecycle.class,
    TaskRegistry.class,
    TaskQuery.class,
    AuditWriter.class,
    AuditReader.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
          System.getenv("KIZUNA_TASK_TEST_JDBC_URL"), "postgres", "");
    }

    @Bean
    SpringLiquibase liquibase(DataSource source) {
      var migration = new SpringLiquibase();
      migration.setDataSource(source);
      migration.setChangeLog("classpath:db/changelog/releases/v0.1.0/platform/14-task-audit.yaml");
      return migration;
    }

    @Bean
    @DependsOn("liquibase")
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(source);
      factory.setPackagesToScan("com.kizuna.task.domain", "com.kizuna.audit.domain");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(
          Map.of(
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
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    StoreContext storeContext() {
      return new StoreContext();
    }

    @Bean
    AppProperties properties() {
      return new AppProperties();
    }

    @Bean
    StoreRepository stores() {
      return mock(StoreRepository.class);
    }

    @Bean
    ServiceExecutionIdentityService identities() {
      var mock = mock(ServiceExecutionIdentityService.class);
      authorize(mock);
      return mock;
    }

    @Bean
    JdbcTemplate jdbc(DataSource source) {
      return new JdbcTemplate(source);
    }

    @Bean
    Probe probe(JdbcTemplate jdbc) {
      return new Probe(jdbc);
    }
  }
}
