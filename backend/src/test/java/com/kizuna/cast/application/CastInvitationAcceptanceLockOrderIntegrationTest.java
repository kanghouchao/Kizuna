package com.kizuna.cast.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.LiquibaseSchemaJpaConfig;
import com.kizuna.shared.persistence.RowLockProbe;
import com.kizuna.store.StoreShortNames;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.UserRepository;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** 招待の受諾が 店舗 → 在籍 → 招待 の順に行を押さえることを実 PostgreSQL で確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CastInvitationAcceptanceLockOrderIntegrationTest {
  private static final String PASSWORD = "password-123";
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private RowLockProbe locks;
  private CastInvitationAcceptanceService accepting;
  private ExecutorService executor;

  enum Acceptance {
    NEW_USER,
    EXISTING_USER
  }

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    locks =
        new RowLockProbe(
            jdbc, new TransactionTemplate(context.getBean(PlatformTransactionManager.class)));
    accepting = context.getBean(CastInvitationAcceptanceService.class);
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @BeforeEach
  void startExecutor() {
    executor = Executors.newFixedThreadPool(2);
  }

  @AfterEach
  void stopExecutor() {
    executor.shutdownNow();
  }

  @ParameterizedTest
  @EnumSource(Acceptance.class)
  void waitsOnTheEnrollmentRowHoldingTheStoreRowButNotTheInvitationRow(Acceptance acceptance)
      throws Exception {
    long store = store();
    String enrollment = enrollment(store);
    String invitation = UUID.randomUUID().toString();
    String token = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_cast_invitations(id,store_id,cast_id,token,status,expires_at)"
            + " values (?,?,?,?,'PENDING',current_timestamp + interval '1 hour')",
        invitation,
        store,
        enrollment,
        token);
    var release = new CountDownLatch(1);
    // 真ん中の在籍行で止めると、手前（店舗）は押さえ済み・先（招待）は未着手の両方が一度に見え、順序の入れ替えをどれも見分けられる。
    Future<?> holder = locks.hold(executor, "t_cast_enrollments", enrollment, release);

    Future<?> run = executor.submit(accept(acceptance, token));

    assertThat(locks.awaitBlockedStatement()).contains("t_cast_enrollments");
    assertThat(locks.sqlStateOfLockingWithoutWaiting("t_stores", store)).isEqualTo("55P03");
    assertThat(locks.sqlStateOfLockingWithoutWaiting("t_cast_invitations", invitation)).isNull();
    release.countDown();
    holder.get(10, TimeUnit.SECONDS);
    run.get(10, TimeUnit.SECONDS);
    assertThat(
            jdbc.queryForObject(
                "select status from t_cast_invitations where id = ?", String.class, invitation))
        .isEqualTo("ACCEPTED");
  }

  private Callable<String> accept(Acceptance acceptance, String token) {
    String email = UUID.randomUUID() + "@example.test";
    return switch (acceptance) {
      case NEW_USER ->
          () ->
              accepting.acceptAsNewUser(
                  token, new CastNewUserAcceptCommand(email, PASSWORD, "検証本人"));
      case EXISTING_USER -> {
        jdbc.update(
            "insert into t_users(email,password,display_name,user_type) values (?,?,'検証本人','CAST')",
            email,
            context.getBean(PasswordEncoder.class).encode(PASSWORD));
        yield () -> accepting.acceptAsExistingUser(token, email, PASSWORD);
      }
    };
  }

  private long store() {
    return jdbc.queryForObject(
        "insert into t_stores(name,short_name,domain) values ('検証店舗',?,?) returning id",
        Long.class,
        StoreShortNames.next(),
        UUID.randomUUID() + ".example.test");
  }

  private String enrollment(long store) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_cast_enrollments(id,store_id,started_on) values (?,?,current_date)",
        id,
        store);
    return id;
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(
      basePackageClasses = {
        CastEnrollmentRepository.class,
        StoreRepository.class,
        UserRepository.class
      })
  @Import(CastInvitationAcceptanceService.class)
  static class Config extends LiquibaseSchemaJpaConfig {
    @Override
    protected Class<?> owner() {
      return CastInvitationAcceptanceLockOrderIntegrationTest.class;
    }

    @Bean
    BusinessAudit businessAudit() {
      return mock(BusinessAudit.class);
    }

    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder(4);
    }
  }
}
