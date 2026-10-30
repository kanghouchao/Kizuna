package com.kizuna.point.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.point.domain.BenefitRuleRepository;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.shared.persistence.EnabledIfPostgres;
import com.kizuna.shared.persistence.PostgresTestDatabase;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

// 明示指定した専用の使い捨て DB だけで、同日の並行ログインが部分一意索引で高々 1 件に収束することを二接続から検証する。
@EnabledIfPostgres
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoginBenefitPostgresTest {
  private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
  private AnnotationConfigApplicationContext first;
  private AnnotationConfigApplicationContext second;
  private JdbcTemplate jdbc;
  private long userId;
  private long memberId;

  @BeforeAll
  void connect() {
    first = new AnnotationConfigApplicationContext(Config.class);
    second = new AnnotationConfigApplicationContext(Config.class);
    jdbc = first.getBean(JdbcTemplate.class);
  }

  @BeforeEach
  void prepare() {
    jdbc.update("delete from t_point_usage_allocations");
    jdbc.update("delete from t_point_entries");
    jdbc.update("delete from t_benefit_rules");
    String key = UUID.randomUUID().toString();
    userId =
        jdbc.queryForObject(
            "insert into t_users(display_name,user_type,store_scope_type,email,password) values ('検証会員','MEMBER','SPECIFIC_STORES',?,'x') returning id",
            Long.class,
            key + "@example.test");
    memberId =
        jdbc.queryForObject(
            "insert into t_members(member_code,user_id) values (?,?) returning id",
            Long.class,
            key.substring(0, 20),
            userId);
  }

  @AfterAll
  void close() {
    if (second != null) second.close();
    if (first != null) first.close();
  }

  @Test
  void sequentialLoginsOnTheSameDayPostExactlyOnce() {
    rule("ONCE_PER_DAY");

    first.getBean(BenefitGrantService.class).grantLoginBenefits(userId);
    second.getBean(BenefitGrantService.class).grantLoginBenefits(userId);

    assertThat(grants()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select granted_on from t_point_entries where member_id=?",
                LocalDate.class,
                memberId))
        .isEqualTo(TODAY);
  }

  /**
   * 先の側を挿入直前で止める advisory lock で、同日の並行を再現する。後の側は会員行のロック待ちで止まり、解放後に先の commit を
   * 読んで二件目を記帳しない（直列化を外すと索引違反を「二回目」として静かに終える経路に落ちる）。
   */
  @Test
  void concurrentLoginsOnTheSameDayConvergeToOneGrantWithoutFailing() throws Exception {
    rule("ONCE_PER_DAY");
    jdbc.execute(
        "create function issue1062_pause_grant() returns trigger language plpgsql as $$ begin perform pg_advisory_xact_lock(1062); return NEW; end $$");
    jdbc.execute(
        "create trigger issue1062_pause_grant before insert on t_point_entries for each row execute function issue1062_pause_grant()");
    try (var connection = second.getBean(DataSource.class).getConnection();
        var pool = Executors.newFixedThreadPool(2)) {
      connection.setAutoCommit(false);
      try (var statement = connection.createStatement()) {
        statement.execute("select pg_advisory_xact_lock(1062)");
      }
      var a =
          pool.submit(() -> first.getBean(BenefitGrantService.class).grantLoginBenefits(userId));
      var b =
          pool.submit(() -> second.getBean(BenefitGrantService.class).grantLoginBenefits(userId));
      try {
        awaitLockWaiters(2);
      } finally {
        connection.commit();
      }
      a.get(20, TimeUnit.SECONDS);
      b.get(20, TimeUnit.SECONDS);
    } finally {
      jdbc.execute("drop trigger issue1062_pause_grant on t_point_entries");
      jdbc.execute("drop function issue1062_pause_grant()");
    }
    assertThat(grants()).isEqualTo(1);
  }

  /**
   * 一人一回限りの初回ログインが業務時間帯の午前 0 時を跨いで並行すると、付与日が異なるので部分一意索引は二行を許す。 会員行の FOR UPDATE で判定と挿入を直列化し、後の側が先の
   * commit を読んで二件目を記帳しないこと。
   */
  @Test
  void concurrentFirstLoginsAcrossMidnightStillPostOnce() throws Exception {
    rule("ONCE_PER_MEMBER");
    BenefitGrantService nextDay =
        new BenefitGrantService(
            second.getBean(BenefitRuleRepository.class),
            second.getBean(PointEntryRepository.class),
            second.getBean(MemberLookupService.class),
            Clock.fixed(TODAY.plusDays(1).atTime(0, 5).atZone(ZONE).toInstant(), ZONE),
            second.getBean(PlatformTransactionManager.class));
    jdbc.execute(
        "create function issue1062_pause_grant() returns trigger language plpgsql as $$ begin perform pg_advisory_xact_lock(1062); return NEW; end $$");
    jdbc.execute(
        "create trigger issue1062_pause_grant before insert on t_point_entries for each row execute function issue1062_pause_grant()");
    try (var connection = second.getBean(DataSource.class).getConnection();
        var pool = Executors.newFixedThreadPool(2)) {
      connection.setAutoCommit(false);
      try (var statement = connection.createStatement()) {
        statement.execute("select pg_advisory_xact_lock(1062)");
      }
      var a =
          pool.submit(() -> first.getBean(BenefitGrantService.class).grantLoginBenefits(userId));
      var b = pool.submit(() -> nextDay.grantLoginBenefits(userId));
      try {
        awaitLockWaiters(2);
      } finally {
        connection.commit();
      }
      a.get(20, TimeUnit.SECONDS);
      b.get(20, TimeUnit.SECONDS);
    } finally {
      jdbc.execute("drop trigger issue1062_pause_grant on t_point_entries");
      jdbc.execute("drop function issue1062_pause_grant()");
    }
    assertThat(grants()).isEqualTo(1);
  }

  /** 呼び元が取引を張っていても（本番は張らない）、付与はその取引に参加せず独立に commit されること。 */
  @Test
  void grantCommitsIndependentlyOfTheCallersReadOnlyTransaction() {
    rule("ONCE_PER_DAY");
    var outer = new TransactionTemplate(first.getBean(PlatformTransactionManager.class));
    outer.setReadOnly(true);

    outer.executeWithoutResult(
        status -> first.getBean(BenefitGrantService.class).grantLoginBenefits(userId));

    assertThat(grants()).isEqualTo(1);
  }

  @Test
  void oncePerMemberLoginRuleAlsoConvergesOnTheFirstDay() {
    rule("ONCE_PER_MEMBER");

    first.getBean(BenefitGrantService.class).grantLoginBenefits(userId);
    second.getBean(BenefitGrantService.class).grantLoginBenefits(userId);

    assertThat(grants()).isEqualTo(1);
  }

  private void rule(String repeatPolicy) {
    jdbc.update(
        "insert into t_benefit_rules(name,type,store_scope_type,repeat_policy,points) values ('ログインボーナス','LOGIN','ALL_STORES',?,100)",
        repeatPolicy);
  }

  private long grants() {
    return jdbc.queryForObject(
        "select count(*) from t_point_entries where entry_type='BENEFIT_GRANT' and member_id=?",
        Long.class,
        memberId);
  }

  /** 片方は挿入直前の advisory lock、もう片方は会員行のロックで待つ。両方が止まるまで待つ。 */
  private void awaitLockWaiters(int expected) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      long blocked =
          jdbc.queryForObject(
              "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'",
              Long.class);
      if (blocked >= expected) return;
      Thread.sleep(20);
    }
    throw new AssertionError("並行ログインがロック待ちで止まっていません");
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(
      basePackageClasses = {
        PointEntryRepository.class,
        BenefitRuleRepository.class,
        MemberRepository.class
      })
  @Import({BenefitGrantService.class, MemberLookupService.class})
  static class Config {
    @Bean
    DataSource dataSource() {
      var source = PostgresTestDatabase.pool(LoginBenefitPostgresTest.class);
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
      return Clock.fixed(TODAY.atTime(12, 0).atZone(ZONE).toInstant(), ZONE);
    }

    @Bean
    JdbcTemplate jdbc(DataSource source) {
      return new JdbcTemplate(source);
    }
  }
}
