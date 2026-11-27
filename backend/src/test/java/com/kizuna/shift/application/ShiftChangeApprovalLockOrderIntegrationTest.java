package com.kizuna.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.LiquibaseSchemaJpaConfig;
import com.kizuna.shared.persistence.RowLockProbe;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.store.StoreShortNames;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** 変更申請の承認が、申請行を書く前に対象シフトの行を押さえることを実 PostgreSQL で確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShiftChangeApprovalLockOrderIntegrationTest {
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private RowLockProbe locks;
  private ShiftRequestService decisions;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    locks =
        new RowLockProbe(
            jdbc, new TransactionTemplate(context.getBean(PlatformTransactionManager.class)));
    decisions = context.getBean(ShiftRequestService.class);
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void waitsOnTheRequestRowHoldingTheTargetShiftRow() throws Exception {
    long actor =
        jdbc.queryForObject(
            "insert into t_users(email,display_name,user_type) values (?,'検証担当','STAFF') returning id",
            Long.class,
            UUID.randomUUID() + "@example.test");
    long store =
        jdbc.queryForObject(
            "insert into t_stores(name,short_name,domain) values ('検証店舗',?,?) returning id",
            Long.class,
            StoreShortNames.next(),
            UUID.randomUUID() + ".example.test");
    String enrollment = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_cast_enrollments(id,store_id,started_on) values (?,?,current_date)",
        enrollment,
        store);
    String shift = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_shifts(id,store_id,cast_id,work_date,start_time,end_time,status)"
            + " values (?,?,?,date '2030-01-02',time '10:00',time '18:00','CONFIRMED')",
        shift,
        store,
        enrollment);
    String request = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_shift_requests(id,store_id,cast_id,request_type,shift_id,"
            + "work_date,start_time,end_time,"
            + "original_work_date,original_start_time,original_end_time)"
            + " values (?,?,?,'CHANGE',?,date '2030-01-02',time '11:00',time '18:00',"
            + "date '2030-01-02',time '10:00',time '18:00')",
        request,
        store,
        enrollment,
        shift);
    var release = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<?> holder = locks.hold(executor, "t_shift_requests", request, release);

      Future<?> approval = executor.submit(() -> decisions.approve(store, request, null, actor));

      // 申請行の書き込みは取引の終わりまで遅れているだけで、間に申請行を触る問い合わせが挟まれば Hibernate が先に流して逆転する。
      assertThat(locks.awaitBlockedStatement()).contains("t_shift_requests");
      assertThat(locks.sqlStateOfLockingWithoutWaiting("t_shifts", shift)).isEqualTo("55P03");
      release.countDown();
      holder.get(10, TimeUnit.SECONDS);
      approval.get(10, TimeUnit.SECONDS);
    }
    assertThat(
            jdbc.queryForObject(
                "select start_time::text from t_shifts where id = ?", String.class, shift))
        .isEqualTo("11:00:00");
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(
      basePackageClasses = {
        ShiftRepository.class,
        CastEnrollmentRepository.class,
        StoreRepository.class
      })
  @Import(ShiftRequestService.class)
  static class Config extends LiquibaseSchemaJpaConfig {
    @Override
    protected Class<?> owner() {
      return ShiftChangeApprovalLockOrderIntegrationTest.class;
    }

    @Bean
    BusinessAudit businessAudit() {
      return mock(BusinessAudit.class);
    }

    @Bean
    BusinessDateService businessDateService() {
      return mock(BusinessDateService.class);
    }
  }
}
