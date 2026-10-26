package com.kizuna.point.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.point.domain.BenefitRuleRepository;
import com.kizuna.point.domain.PointEntryRepository;
import com.zaxxer.hikari.HikariDataSource;
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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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

// 明示指定した専用の使い捨て DB だけで、同日の並行ログインが部分一意索引で高々 1 件に収束することを二接続から検証する。
@EnabledIfEnvironmentVariable(named = "KIZUNA_POINTS_TEST_JDBC_URL", matches = ".+")
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
   * 二本とも事前判定（その日の受益なし）を通り抜けた後に挿入へ進む並行を、挿入直前で止める advisory lock で再現する。
   * 解放後は先に通った側が記帳し、後の側は索引違反を「二回目」として静かに終える。
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
        awaitBlockedInserts(2);
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

  private void awaitBlockedInserts(int expected) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      long blocked =
          jdbc.queryForObject(
              "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like 'insert into t_point_entries%'",
              Long.class);
      if (blocked >= expected) return;
      Thread.sleep(20);
    }
    throw new AssertionError("並行ログインが挿入直前で止まっていません");
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
