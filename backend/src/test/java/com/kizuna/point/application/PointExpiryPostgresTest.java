package com.kizuna.point.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditActor;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.point.domain.PointAllocation;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.pointsexpiry.application.PointExpiryTask;
import com.kizuna.settings.application.PointSettings;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.task.application.TaskLifecycle;
import com.kizuna.task.application.TaskRegistry;
import com.kizuna.task.domain.ExecutionRequestRepository;
import com.kizuna.task.execution.TaskCommand;
import com.kizuna.task.execution.TaskExecutor;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PlatformUserRepository;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

// 明示指定した専用の使い捨て DB だけで、実際の台帳・監査・授権を二接続から検証する。
@EnabledIfEnvironmentVariable(named = "KIZUNA_POINTS_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PointExpiryPostgresTest {
  private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
  private static final LocalDate TODAY = LocalDate.now(ZONE);
  private static final LocalDate AS_OF = TODAY.plusDays(1);
  private static final String HASH =
      new BCryptPasswordEncoder(4).encode(UUID.randomUUID().toString());
  private AnnotationConfigApplicationContext first;
  private AnnotationConfigApplicationContext second;
  private JdbcTemplate jdbc;
  private long serviceId;
  private long executeRole;
  private long expiryRole;
  private long memberId;
  private AuditActor operator;

  @BeforeAll
  void connect() {
    first = new AnnotationConfigApplicationContext(Config.class);
    second = new AnnotationConfigApplicationContext(Config.class);
    jdbc = first.getBean(JdbcTemplate.class);
    executeRole = role("TASK_EXECUTE");
    expiryRole = role("POINT_EXPIRE");
    serviceId = service("失効処理", executeRole, expiryRole);
    operator =
        new AuditActor(
            jdbc.queryForObject(
                "select id from t_users where user_type='STAFF' order by id limit 1", Long.class),
            "STAFF",
            "検証担当");
  }

  @BeforeEach
  void prepare() {
    jdbc.update("delete from t_point_usage_allocations");
    jdbc.update("delete from t_point_entries");
    jdbc.update(
        "update t_users set enabled=true, store_scope_type='ALL_STORES' where id=?", serviceId);
    jdbc.update(
        "insert into t_user_roles(platform_user_id,role_id) values (?,?) on conflict do nothing",
        serviceId,
        expiryRole);
    first.getBean(AppProperties.class).getPointsExpiry().setMaxLots(100);
    jdbc.update(
        "insert into t_user_roles(platform_user_id,role_id) values (?,?) on conflict do nothing",
        operator.id(),
        expiryRole);
    memberId = member();
  }

  @AfterAll
  void close() {
    if (second != null) second.close();
    if (first != null) first.close();
  }

  @Test
  void recordsOneEntryPerMemberAndKeepsBalanceAndEvidenceOnReplay() {
    long old = credit(memberId, 100, TODAY.minusDays(5));
    credit(memberId, 50, TODAY.minusDays(1));
    credit(memberId, 70, AS_OF);
    credit(memberId, 90, null);
    long beforeBalance = first.getBean(PointLedgerService.class).balance(memberId);
    var command = command();
    var executor = first.getBean(TaskExecutor.class);
    var run = executor.executeScheduled(command).execution();
    assertThat(run.status()).isEqualTo("SUCCEEDED");
    assertThat(run.processedCount()).isEqualTo(1);
    assertThat(expired()).isEqualTo(150);
    assertThat(executor.executeScheduled(command).created()).isFalse();
    assertThat(
            second
                .getBean(TaskExecutor.class)
                .executeScheduled(command())
                .execution()
                .processedCount())
        .isZero();
    assertThat(expired()).isEqualTo(150);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_point_entries where entry_type='EXPIRE' and actor_user_id is not null",
                Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_audit_events where action='POINT_EXPIRED' and source_id=?",
                Long.class,
                run.id().toString()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_point_usage_allocations a join t_point_entries e on e.id=a.source_entry_id where e.id=? and e.expires_on=?",
                Long.class,
                old,
                TODAY.minusDays(5)))
        .isEqualTo(1);
    assertThat(first.getBean(PointLedgerService.class).balance(memberId))
        .isEqualTo(beforeBalance)
        .isEqualTo(160);
    jdbc.update("update t_users set enabled=false where id=?", serviceId);
    jdbc.update(
        "delete from t_user_roles where platform_user_id=? and role_id=?",
        operator.id(),
        expiryRole);
    var replay = executor.execute(command, operator);
    assertThat(replay.created()).isFalse();
    assertThat(replay.execution().id()).isEqualTo(run.id());
    assertThat(expired()).isEqualTo(150);
  }

  @Test
  void simultaneousRunsAndCancellationNeverAllocateTheSameRemainderTwice() throws Exception {
    long lot = credit(memberId, 100, TODAY);
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(3)) {
      var cancel =
          pool.submit(
              () ->
                  tx(first)
                      .execute(
                          status -> {
                            first
                                .getBean(PointEntryRepository.class)
                                .findCreditsForUpdate(memberId);
                            locked.countDown();
                            await(release);
                            first.getBean(PointLedgerService.class).cancel(lot, "取消検証", serviceId);
                            return null;
                          }));
      assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
      var a = pool.submit(() -> first.getBean(TaskExecutor.class).executeScheduled(command()));
      var b = pool.submit(() -> second.getBean(TaskExecutor.class).executeScheduled(command()));
      assertPointLockWait();
      release.countDown();
      cancel.get(20, TimeUnit.SECONDS);
      assertThat(a.get(20, TimeUnit.SECONDS).execution().status()).isEqualTo("SUCCEEDED");
      assertThat(b.get(20, TimeUnit.SECONDS).execution().status()).isEqualTo("SUCCEEDED");
      assertThat(expired()).isZero();
      assertThat(
              jdbc.queryForObject(
                  "select sum(amount) from t_point_usage_allocations where source_entry_id=?",
                  Long.class,
                  lot))
          .isEqualTo(100);
    }
  }

  @Test
  void concurrentConsumptionThenUseCancellationLeavesOnlyTheReturnedExpiredAmount()
      throws Exception {
    credit(memberId, 100, TODAY);
    var order = order();
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var use =
          pool.submit(
              () ->
                  tx(first)
                      .execute(
                          status -> {
                            first
                                .getBean(PointEntryRepository.class)
                                .findCreditsForUpdate(memberId);
                            locked.countDown();
                            await(release);
                            first
                                .getBean(PointLedgerService.class)
                                .useForOrder(memberId, order.id(), order.storeId(), 30, serviceId);
                            return null;
                          }));
      assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
      var expire =
          pool.submit(() -> second.getBean(TaskExecutor.class).executeScheduled(command()));
      assertPointLockWait();
      release.countDown();
      use.get(20, TimeUnit.SECONDS);
      assertThat(expire.get(20, TimeUnit.SECONDS).execution().status()).isEqualTo("SUCCEEDED");
    }
    assertThat(expired()).isEqualTo(70);
    tx(first)
        .executeWithoutResult(
            status -> {
              var repository = first.getBean(PointEntryRepository.class);
              repository.findCreditsForUpdate(memberId);
              var use = repository.findUsesByOrderId(order.id()).getFirst();
              repository.saveAndFlush(PointEntry.reverseUse(use, "利用取消", serviceId));
            });
    assertThat(
            first
                .getBean(TaskExecutor.class)
                .executeScheduled(command())
                .execution()
                .processedCount())
        .isEqualTo(1);
    assertThat(expired()).isEqualTo(100);
    assertThat(
            first
                .getBean(TaskExecutor.class)
                .executeScheduled(command())
                .execution()
                .processedCount())
        .isZero();
  }

  @Test
  void twoInstancesWithDifferentKeysOnlyMaterializeOnce() throws Exception {
    credit(memberId, 100, TODAY);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                await(start);
                return first.getBean(TaskExecutor.class).executeScheduled(command());
              });
      var b =
          pool.submit(
              () -> {
                await(start);
                return second.getBean(TaskExecutor.class).executeScheduled(command());
              });
      start.countDown();
      assertThat(a.get(20, TimeUnit.SECONDS).execution().status()).isEqualTo("SUCCEEDED");
      assertThat(b.get(20, TimeUnit.SECONDS).execution().status()).isEqualTo("SUCCEEDED");
      assertThat(expired()).isEqualTo(100);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from t_point_entries where entry_type='EXPIRE'", Long.class))
          .isEqualTo(1);
    }
  }

  @Test
  void auditStorageFailureRollsBackLedgerAndCanBeRetriedAfterRecovery() {
    credit(memberId, 100, TODAY);
    jdbc.execute(
        "create function issue820_reject_audit() returns trigger language plpgsql as $$ begin if NEW.action = 'POINT_EXPIRED' then raise exception '検証用監査障害'; end if; return NEW; end $$");
    jdbc.execute(
        "create trigger issue820_reject_audit before insert on t_audit_events for each row execute function issue820_reject_audit()");
    Long failedId;
    try {
      var failed = first.getBean(TaskExecutor.class).executeScheduled(command()).execution();
      assertThat(failed.status()).isEqualTo("FAILED");
      assertThat(expired()).isZero();
      failedId = failed.id();
    } finally {
      jdbc.execute("drop trigger issue820_reject_audit on t_audit_events");
      jdbc.execute("drop function issue820_reject_audit()");
    }
    assertThat(first.getBean(TaskExecutor.class).retry(failedId, "監査ストレージ復旧", operator).status())
        .isEqualTo("SUCCEEDED");
    assertThat(expired()).isEqualTo(100);
  }

  @Test
  void secondMemberFailureRollsBackAllEntriesAndRetryRecovers() {
    credit(memberId, 100, TODAY);
    long next = member();
    credit(next, Integer.MAX_VALUE, TODAY);
    long extra = credit(next, 1, TODAY);
    var executor = first.getBean(TaskExecutor.class);
    var failed = executor.executeScheduled(command()).execution();
    assertThat(failed.status()).isEqualTo("FAILED");
    assertThat(expired()).isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_audit_events where action='POINT_EXPIRED' and source_id=?",
                Long.class,
                failed.id().toString()))
        .isZero();
    first.getBean(PointLedgerService.class).cancel(extra, "重複付与の取消", serviceId);
    var retried = executor.retry(failed.id(), "過大な付与を訂正", operator);
    assertThat(retried.status()).isEqualTo("SUCCEEDED");
    assertThat(retried.processedCount()).isEqualTo(2);
    assertThat(expired()).isEqualTo(100L + Integer.MAX_VALUE);
  }

  @Test
  void limitFailureRecoversWithoutPartialSuccessAndFutureDateIsRejected() {
    credit(memberId, 100, TODAY);
    credit(memberId, 100, TODAY);
    first.getBean(AppProperties.class).getPointsExpiry().setMaxLots(1);
    var executor = first.getBean(TaskExecutor.class);
    var failed = executor.executeScheduled(command()).execution();
    assertThat(failed.status()).isEqualTo("FAILED");
    assertThat(expired()).isZero();
    first.getBean(AppProperties.class).getPointsExpiry().setMaxLots(2);
    assertThat(executor.retry(failed.id(), "許容上限の確認", operator).status()).isEqualTo("SUCCEEDED");
    var future =
        new TaskCommand(
            PointExpiryTask.NAME,
            UUID.randomUUID().toString(),
            serviceId,
            null,
            AS_OF.plusDays(1),
            AS_OF.plusDays(1));
    assertThat(executor.executeScheduled(future).execution().status()).isEqualTo("FAILED");
  }

  @Test
  void sourceIdentityMustKeepBothPermissionsAndCurrentScopeEvenInsideOneTransaction() {
    credit(memberId, 100, TODAY);
    var identities = first.getBean(ServiceExecutionIdentityService.class);
    assertThatThrownBy(
            () ->
                tx(first)
                    .executeWithoutResult(
                        status -> {
                          identities.requireService(serviceId, PermissionCode.POINT_EXPIRE, null);
                          second
                              .getBean(JdbcTemplate.class)
                              .update(
                                  "update t_users set store_scope_type='SPECIFIC_STORES' where id=?",
                                  serviceId);
                          identities.requireService(serviceId, PermissionCode.POINT_EXPIRE, null);
                        }))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> first.getBean(TaskExecutor.class).executeScheduled(command()))
        .isInstanceOf(AccessDeniedException.class);
    jdbc.update("update t_users set store_scope_type='ALL_STORES' where id=?", serviceId);
    jdbc.update(
        "delete from t_user_roles where platform_user_id=? and role_id=?", serviceId, expiryRole);
    assertThatThrownBy(() -> first.getBean(TaskExecutor.class).executeScheduled(command()))
        .isInstanceOf(AccessDeniedException.class);
    jdbc.update(
        "insert into t_user_roles(platform_user_id,role_id) values (?,?)", serviceId, expiryRole);
    jdbc.update("update t_users set enabled=false where id=?", serviceId);
    assertThatThrownBy(() -> first.getBean(TaskExecutor.class).executeScheduled(command()))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(expired()).isZero();
  }

  @Test
  void independentAuthorizationSeesCommittedRevocationsDespiteRepeatableReadAndLoadedRoles() {
    var external = second.getBean(JdbcTemplate.class);
    var identities = first.getBean(ServiceExecutionIdentityService.class);
    for (String change : List.of("enabled", "scope", "role", "permission")) {
      var work = tx(first);
      work.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
      assertThatThrownBy(
              () ->
                  work.executeWithoutResult(
                      status -> {
                        first
                            .getBean(PlatformUserRepository.class)
                            .findById(serviceId)
                            .orElseThrow()
                            .getRoleIds()
                            .size();
                        identities.requireService(serviceId, PermissionCode.POINT_EXPIRE, null);
                        switch (change) {
                          case "enabled" ->
                              external.update(
                                  "update t_users set enabled=false where id=?", serviceId);
                          case "scope" ->
                              external.update(
                                  "update t_users set store_scope_type='SPECIFIC_STORES' where id=?",
                                  serviceId);
                          case "role" ->
                              external.update(
                                  "delete from t_user_roles where platform_user_id=? and role_id=?",
                                  serviceId,
                                  expiryRole);
                          case "permission" ->
                              external.update(
                                  "delete from t_role_permissions where role_id=?", expiryRole);
                          default -> throw new IllegalStateException();
                        }
                        identities.requireService(serviceId, PermissionCode.POINT_EXPIRE, null);
                      }))
          .isInstanceOf(AccessDeniedException.class);
      external.update(
          "update t_users set enabled=true, store_scope_type='ALL_STORES' where id=?", serviceId);
      external.update(
          "insert into t_user_roles(platform_user_id,role_id) values (?,?) on conflict do nothing",
          serviceId,
          expiryRole);
      external.update(
          "insert into t_role_permissions(role_id,permission_id) select ?,id from t_permissions where code='POINT_EXPIRE' on conflict do nothing",
          expiryRole);
    }
  }

  @Test
  void exhaustedNestedConnectionBudgetFailsClosedAndLeavesNoPartialWork() {
    credit(memberId, 100, TODAY);
    var source = (HikariDataSource) first.getBean(DataSource.class);
    source.setMaximumPoolSize(1);
    source.getHikariPoolMXBean().softEvictConnections();
    try {
      var failed = first.getBean(TaskExecutor.class).executeScheduled(command()).execution();
      assertThat(failed.status()).isEqualTo("FAILED");
      assertThat(expired()).isZero();
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from t_audit_events where action='POINT_EXPIRED' and source_id=?",
                  Long.class,
                  failed.id().toString()))
          .isZero();
    } finally {
      source.setMaximumPoolSize(4);
    }
  }

  @Test
  void revocationAfterMaterializationRollsBackExpiryAndKeepsCommittedConsumption()
      throws Exception {
    credit(memberId, 100, TODAY);
    var order = order();
    tx(first)
        .executeWithoutResult(
            status ->
                first
                    .getBean(PointLedgerService.class)
                    .useForOrder(memberId, order.id(), order.storeId(), 30, serviceId));
    jdbc.execute(
        "create function issue820_pause_expiry() returns trigger language plpgsql as $$ begin if NEW.entry_type='EXPIRE' then perform pg_advisory_xact_lock(820820); end if; return NEW; end $$");
    jdbc.execute(
        "create trigger issue820_pause_expiry after insert on t_point_entries for each row execute function issue820_pause_expiry()");
    try (var connection = second.getBean(DataSource.class).getConnection();
        var pool = Executors.newSingleThreadExecutor()) {
      connection.setAutoCommit(false);
      try (var statement = connection.createStatement()) {
        statement.execute("select pg_advisory_xact_lock(820820)");
      }
      var run = pool.submit(() -> first.getBean(TaskExecutor.class).executeScheduled(command()));
      try {
        assertPointLockWait();
        jdbc.update("update t_users set enabled=false where id=?", serviceId);
      } finally {
        connection.commit();
      }
      var result = run.get(20, TimeUnit.SECONDS).execution();
      assertThat(result.status()).isEqualTo("FAILED");
      assertThat(result.failureCode()).isEqualTo("AUTHORIZATION_DENIED");
      assertThat(expired()).isZero();
      assertThat(
              jdbc.queryForObject("select sum(amount) from t_point_usage_allocations", Long.class))
          .isEqualTo(30);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from t_audit_events where action='POINT_EXPIRED' and source_id=?",
                  Long.class,
                  result.id().toString()))
          .isZero();
    } finally {
      jdbc.execute("drop trigger issue820_pause_expiry on t_point_entries");
      jdbc.execute("drop function issue820_pause_expiry()");
    }
  }

  @Test
  void manualExecutionAndRetryRequireCurrentStaffPermissionAndRejectForgedActorType() {
    var executor = first.getBean(TaskExecutor.class);
    var request = command();
    assertThatThrownBy(() -> executor.execute(request, new AuditActor(serviceId, "STAFF", "偽装")))
        .isInstanceOf(AccessDeniedException.class);
    jdbc.update(
        "delete from t_user_roles where platform_user_id=? and role_id=?",
        operator.id(),
        expiryRole);
    assertThatThrownBy(() -> executor.execute(request, operator))
        .isInstanceOf(AccessDeniedException.class);
    var future =
        new TaskCommand(
            PointExpiryTask.NAME,
            UUID.randomUUID().toString(),
            serviceId,
            null,
            AS_OF.plusDays(1),
            AS_OF.plusDays(1));
    var failed = executor.executeScheduled(future).execution();
    assertThatThrownBy(() -> executor.retry(failed.id(), "再試行", operator))
        .isInstanceOf(AccessDeniedException.class);
    jdbc.update(
        "insert into t_user_roles(platform_user_id,role_id) values (?,?)",
        operator.id(),
        expiryRole);
    assertThat(executor.execute(request, operator).execution().status()).isEqualTo("SUCCEEDED");
  }

  @Test
  void candidatePaginationRequiresBothPermissionsEvenWhenTheyAreOnDifferentRoles() {
    service("実行のみ", executeRole);
    service("失効のみ", expiryRole);
    var page =
        first
            .getBean(ServiceExecutionIdentityService.class)
            .candidates(0, 1, PermissionCode.POINT_EXPIRE);
    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent().getFirst().id()).isEqualTo(serviceId);
    assertThat(
            first
                .getBean(ServiceExecutionIdentityService.class)
                .candidates(1, 1, PermissionCode.POINT_EXPIRE)
                .getContent())
        .isEmpty();
  }

  @Test
  void databaseRejectsDuplicateExecutionMemberKey() {
    long lot = credit(memberId, 100, TODAY);
    var key = "expiry:fixture:" + memberId;
    tx(first)
        .executeWithoutResult(
            status ->
                first
                    .getBean(PointEntryRepository.class)
                    .saveAndFlush(
                        PointEntry.expire(
                            memberId, 50, List.of(PointAllocation.of(lot, 50)), key)));
    assertThatThrownBy(
            () ->
                tx(second)
                    .executeWithoutResult(
                        status ->
                            second
                                .getBean(PointEntryRepository.class)
                                .saveAndFlush(
                                    PointEntry.expire(
                                        memberId, 50, List.of(PointAllocation.of(lot, 50)), key))))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(expired()).isEqualTo(50);
  }

  private TaskCommand command() {
    return new TaskCommand(
        PointExpiryTask.NAME, UUID.randomUUID().toString(), serviceId, null, AS_OF, AS_OF);
  }

  private record OrderFixture(String id, long storeId) {}

  private OrderFixture order() {
    long store = jdbc.queryForObject("select id from t_stores limit 1", Long.class);
    String order = UUID.randomUUID().toString();
    String course = UUID.randomUUID().toString();
    String revision = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_services(id,store_id,kind,name,duration_minutes,price,remuneration,revision_number) values (?,?,'COURSE','検証コース',60,100,0,1)",
        course,
        store);
    jdbc.update(
        "insert into t_service_revisions(id,store_id,service_id,revision_number,operation,actor_id,occurred_at,after_kind,after_name,after_duration_minutes,after_price,after_remuneration) values (?,?,?,1,'CREATED',?,current_timestamp,'COURSE','検証コース',60,100,0)",
        revision,
        store,
        course,
        serviceId);
    jdbc.update(
        "insert into t_orders(id,store_id,business_date,course_name,course_minutes,course_service_id,course_revision_id,course_revision_number,course_price,course_remuneration,course_adoption_basis,course_adopted_at) select ?,store_id,?,'検証コース',60,service_id,id,revision_number,100,0,'CURRENT_SETTING',current_timestamp from t_service_revisions where store_id=? limit 1",
        order,
        TODAY,
        store);
    return new OrderFixture(order, store);
  }

  private long expired() {
    return jdbc.queryForObject(
        "select coalesce(-sum(amount),0) from t_point_entries where entry_type='EXPIRE'",
        Long.class);
  }

  private long credit(long member, int amount, LocalDate expiry) {
    return tx(first)
        .execute(
            status ->
                first
                    .getBean(PointEntryRepository.class)
                    .saveAndFlush(
                        PointEntry.manualAdjust(
                            member,
                            null,
                            amount,
                            "検証付与",
                            expiry,
                            List.of(),
                            serviceId,
                            UUID.randomUUID().toString()))
                    .getId());
  }

  private long member() {
    String key = UUID.randomUUID().toString();
    long user =
        jdbc.queryForObject(
            "insert into t_users(display_name,user_type,store_scope_type,email,password) values ('検証会員','MEMBER','SPECIFIC_STORES',?,?) returning id",
            Long.class,
            key + "@example.test",
            HASH);
    return jdbc.queryForObject(
        "insert into t_members(member_code,platform_user_id) values (?,?) returning id",
        Long.class,
        key.substring(0, 20),
        user);
  }

  private long role(String permission) {
    long id =
        jdbc.queryForObject(
            "insert into t_roles(name) values (?) returning id",
            Long.class,
            UUID.randomUUID().toString());
    jdbc.update(
        "insert into t_role_permissions(role_id,permission_id) select ?,id from t_permissions where code=?",
        id,
        permission);
    return id;
  }

  private long service(String name, long... roles) {
    long id =
        jdbc.queryForObject(
            "insert into t_users(display_name,user_type,store_scope_type) values (?,'SERVICE','ALL_STORES') returning id",
            Long.class,
            name);
    for (long role : roles)
      jdbc.update("insert into t_user_roles(platform_user_id,role_id) values (?,?)", id, role);
    return id;
  }

  private TransactionTemplate tx(AnnotationConfigApplicationContext context) {
    return new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
  }

  private void assertPointLockWait() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      long blocked =
          second
              .getBean(JdbcTemplate.class)
              .queryForObject(
                  "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like '%t_point_entries%'",
                  Long.class);
      if (blocked > 0) return;
      Thread.sleep(20);
    }
    throw new AssertionError("失効処理が元ロットのロックを待っていません");
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("待機が上限を超過");
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(failure);
    }
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(
      basePackageClasses = {
        PointEntryRepository.class,
        ExecutionRequestRepository.class,
        AuditEventRepository.class,
        PlatformUserRepository.class,
        StoreRepository.class
      })
  @Import({
    PointExpiryTask.class,
    PointExpiryLedger.class,
    PointLedgerService.class,
    TaskExecutor.class,
    TaskLifecycle.class,
    TaskRegistry.class,
    AuditWriter.class,
    BusinessAudit.class,
    ServiceExecutionIdentityService.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      var source = new HikariDataSource();
      source.setJdbcUrl(System.getenv("KIZUNA_POINTS_TEST_JDBC_URL"));
      source.setUsername("postgres");
      source.setMinimumIdle(0);
      source.setMaximumPoolSize(4);
      source.setConnectionTimeout(300);
      return source;
    }

    @Bean
    SpringLiquibase liquibase(DataSource source) {
      var migration = new SpringLiquibase();
      migration.setDataSource(source);
      migration.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
      migration.setContexts("demo");
      migration.setChangeLogParameters(
          Map.of("initialAdminPasswordHash", HASH, "demoUserPasswordHash", HASH));
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
    Clock clock() {
      return Clock.fixed(AS_OF.atStartOfDay(ZONE).toInstant(), ZONE);
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
    JdbcTemplate jdbc(DataSource source) {
      return new JdbcTemplate(source);
    }

    @Bean
    SystemConfigService settings() {
      var settings = mock(SystemConfigService.class);
      when(settings.pointSettings()).thenReturn(new PointSettings(100, 1, 1));
      return settings;
    }
  }
}
