package com.kizuna.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.member.domain.MemberRepository;
import com.kizuna.notification.transport.ContactTransport;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.TooManyRequestsException;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.user.domain.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 実 PostgreSQL で、会員登録の両経路が利用者を未検証で生み、トークンをダイジェストで残し、検証メールをコミット後に送ることと、
 * 確認がトークンを消費し、本人の再送が前のトークンを失効させ、間隔と回数の上限で止まることと、職員の照会が検証状態を読むことを確かめる。
 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberEmailVerificationIntegrationTest {
  private static final Pattern LINK =
      Pattern.compile("http://kizuna\\.test/auth/verify-email#([A-Za-z0-9_-]+)");

  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private TransactionTemplate tx;
  private MemberRegistrationService registration;
  private RecordingTransport transport;
  private EmailVerificationConfirmation confirmation;
  private MemberEmailVerificationService verification;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = context.getBean(JdbcTemplate.class);
    tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    registration = context.getBean(MemberRegistrationService.class);
    transport = context.getBean(RecordingTransport.class);
    confirmation = context.getBean(EmailVerificationConfirmation.class);
    verification = context.getBean(MemberEmailVerificationService.class);
  }

  @AfterAll
  void close() {
    context.close();
  }

  @BeforeEach
  void reset() {
    transport.deliveries.clear();
    transport.outcome = () -> ContactTransport.Result.SENT;
  }

  @Test
  void メール経路の登録は未検証の利用者を生み_コミット後に検証メールを送る() {
    String email = email();
    long deliveriesBefore = notificationDeliveries();

    tx.executeWithoutResult(
        status -> {
          registration.register(new MemberRegistrationCommand(email, "pass12345", "検証会員"));
          assertThat(transport.deliveries).isEmpty();
        });

    assertVerificationMailSentAfterCommit(email);
    assertThat(notificationDeliveries()).isEqualTo(deliveriesBefore);
  }

  @Test
  void LINE経路の登録は未検証の利用者を生み_コミット後に検証メールを送る() {
    String email = email();
    long deliveriesBefore = notificationDeliveries();

    // LineAuthService.register が外側の取引を張るのと同じ形で、送信はその取引のコミット後になる。
    tx.executeWithoutResult(
        status -> {
          registration.registerWithLine(email, "検証会員", "U-" + UUID.randomUUID());
          assertThat(transport.deliveries).isEmpty();
        });

    assertVerificationMailSentAfterCommit(email);
    assertThat(notificationDeliveries()).isEqualTo(deliveriesBefore);
  }

  @Test
  void 職員の照会は一覧で検証の有無を_詳細で検証日時を返す() {
    String email = email();
    tx.executeWithoutResult(
        status -> registration.register(new MemberRegistrationCommand(email, "pass12345", "検証会員")));
    var directory = context.getBean(MemberDirectoryService.class);
    var unverified =
        assertThat(directory.search(MemberStatusFilter.ALL, email, PageRequest.of(0, 10)))
            .singleElement()
            .actual();
    assertThat(unverified.emailVerified()).isFalse();
    long memberId = unverified.id();
    assertThat(directory.findProfile(memberId).orElseThrow().emailVerifiedAt()).isNull();

    jdbc.update(
        "update t_users set email_verified_at = timestamptz '2026-10-26 10:00:00+09' where email = ?",
        email);

    assertThat(directory.search(MemberStatusFilter.ALL, email, PageRequest.of(0, 10)))
        .singleElement()
        .satisfies(row -> assertThat(row.emailVerified()).isTrue());
    assertThat(directory.findProfile(memberId).orElseThrow().emailVerifiedAt())
        .isAtSameInstantAs(OffsetDateTime.parse("2026-10-26T10:00:00+09:00"));
  }

  @Test
  void 取引が巻き戻れば検証メールは送らず_トークンも残らない() {
    String email = email();

    tx.executeWithoutResult(
        status -> {
          registration.register(new MemberRegistrationCommand(email, "pass12345", "検証会員"));
          status.setRollbackOnly();
        });

    assertThat(transport.deliveries).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_email_verification_tokens where email = ?",
                Integer.class,
                email))
        .isZero();
  }

  @ParameterizedTest
  @EnumSource(names = {"UNAVAILABLE", "FAILED", "UNKNOWN"})
  void 送信手段が送れなくても登録は成立する(ContactTransport.Result result) {
    transport.outcome = () -> result;

    assertBothPathsRegisterDespiteSendFailure();
  }

  @Test
  void 送信手段が例外を投げても登録は成立する() {
    transport.outcome =
        () -> {
          throw new IllegalStateException("送信基盤の障害");
        };

    assertBothPathsRegisterDespiteSendFailure();
  }

  @Test
  void 有効なトークンで確認すると利用者が検証済みになり_トークンは消費される() {
    String email = email();
    String token = registerAndCaptureToken(email);

    confirmation.confirm(token);

    Map<String, Object> row =
        jdbc.queryForMap(
            "select u.email_verified_at, t.consumed_at from t_users u"
                + " join t_email_verification_tokens t on t.user_id = u.id where u.email = ?",
            email);
    assertThat(row.get("email_verified_at")).isNotNull();
    assertThat(row.get("consumed_at")).isNotNull();
  }

  @Test
  void 同じトークンの再提示は拒否する() {
    String token = registerAndCaptureToken(email());
    confirmation.confirm(token);

    assertRejected(token);
  }

  @Test
  void 存在しないトークンは拒否する() {
    assertRejected("not-a-token");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "update t_email_verification_tokens set expires_at = now() - interval '1 second'"
            + " where user_id = ?",
        "update t_email_verification_tokens set revoked_at = now() where user_id = ?",
        "update t_users set email = 'changed-' || email where id = ?",
        "update t_users set withdrawn_at = now(), enabled = false where id = ?"
      })
  void 期限切れ_失効済み_宛先の不一致_退会済みはどれも同じ文言で拒否し_検証済みにしない(String breakage) {
    String email = email();
    String token = registerAndCaptureToken(email);
    long userId = jdbc.queryForObject("select id from t_users where email = ?", Long.class, email);
    jdbc.update(breakage, userId);

    assertRejected(token);

    assertThat(
            jdbc.queryForObject(
                "select email_verified_at is null from t_users where id = ?",
                Boolean.class,
                userId))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "select consumed_at is null from t_email_verification_tokens where user_id = ?",
                Boolean.class,
                userId))
        .isTrue();
  }

  @Test
  void 同じトークンを並行して確認すると一方だけが通り_他方は拒否される() throws Exception {
    String token = registerAndCaptureToken(email());
    String digest = EmailVerificationMail.digest(token);
    DataSource source = context.getBean(DataSource.class);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try (Connection blocker = source.getConnection()) {
      // 先にトークン行を押さえ、両方の確認がロック待ちに並んでから放す。
      blocker.setAutoCommit(false);
      try (PreparedStatement lock =
          blocker.prepareStatement(
              "select id from t_email_verification_tokens where token_digest = ? for update")) {
        lock.setString(1, digest);
        lock.executeQuery();
      }
      List<Future<Throwable>> attempts =
          List.of(pool.submit(() -> attempt(token)), pool.submit(() -> attempt(token)));
      try {
        awaitLockWaiters(2);
      } finally {
        blocker.commit();
      }

      List<Throwable> outcomes = new ArrayList<>();
      for (Future<Throwable> attempt : attempts) {
        outcomes.add(attempt.get(10, TimeUnit.SECONDS));
      }
      assertThat(outcomes).filteredOn(Objects::isNull).hasSize(1);
      assertThat(outcomes)
          .filteredOn(Objects::nonNull)
          .singleElement()
          .isInstanceOfSatisfying(
              ServiceException.class,
              rejected -> assertThat(rejected).hasMessage(EmailVerificationConfirmation.REJECTED));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void 再送は前のトークンを失効させ_新しいトークンだけを生かし_コミット後に送る() {
    String email = email();
    long userId = registered(email, Duration.ofSeconds(61));
    String previous = liveDigest(userId);

    verification.resend(userId);

    assertThat(transport.deliveries).hasSize(1);
    Delivery delivery = transport.deliveries.get(0);
    assertThat(delivery.recipient()).isEqualTo(email);
    Matcher link = LINK.matcher(delivery.body());
    assertThat(link.find()).isTrue();
    assertThat(liveDigest(userId)).isEqualTo(EmailVerificationMail.digest(link.group(1)));
    assertThat(
            jdbc.queryForObject(
                "select revoked_at is not null from t_email_verification_tokens"
                    + " where token_digest = ?",
                Boolean.class,
                previous))
        .isTrue();
  }

  @Test
  void 再送の後は前のトークンでは確認できず_新しいトークンで検証済みになる() {
    String email = email();
    String previous = registerAndCaptureToken(email);
    long userId = jdbc.queryForObject("select id from t_users where email = ?", Long.class, email);
    backdate(userId, Duration.ofSeconds(61));
    transport.deliveries.clear();

    verification.resend(userId);

    assertRejected(previous);
    Matcher link = LINK.matcher(transport.deliveries.get(0).body());
    assertThat(link.find()).isTrue();
    confirmation.confirm(link.group(1));
    assertThat(
            jdbc.queryForObject(
                "select email_verified_at is not null from t_users where id = ?",
                Boolean.class,
                userId))
        .isTrue();
  }

  @Test
  void 前回の発行から60秒未満の再送は429で_何も発行しない() {
    long userId = registered(email(), Duration.ofSeconds(59));

    assertThatThrownBy(() -> verification.resend(userId))
        .isInstanceOf(TooManyRequestsException.class);

    assertThat(tokens(userId)).isOne();
    assertThat(transport.deliveries).isEmpty();
  }

  @Test
  void 直近24時間の発行が10回に達していれば429() {
    long userId = registered(email(), Duration.ofHours(23));
    for (int i = 1; i <= 9; i++) {
      pastRevokedToken(userId, Duration.ofHours(23).minusMinutes(i));
    }

    assertThatThrownBy(() -> verification.resend(userId))
        .isInstanceOf(TooManyRequestsException.class);
    assertThat(transport.deliveries).isEmpty();
  }

  @Test
  void 直近24時間より前の発行は回数に数えない() {
    long userId = registered(email(), Duration.ofHours(25));
    for (int i = 1; i <= 9; i++) {
      pastRevokedToken(userId, Duration.ofHours(23).minusMinutes(i));
    }

    verification.resend(userId);

    assertThat(transport.deliveries).hasSize(1);
  }

  @Test
  void 検証済みの利用者の再送は400で_何も発行しない() {
    long userId = registered(email(), Duration.ofHours(1));
    jdbc.update("update t_users set email_verified_at = now() where id = ?", userId);

    assertThatThrownBy(() -> verification.resend(userId)).isInstanceOf(ServiceException.class);

    assertThat(tokens(userId)).isOne();
    assertThat(transport.deliveries).isEmpty();
  }

  @Test
  void 利用者ごとに生きたトークンが2本になる挿入をDBが拒む() {
    long userId = registered(email(), Duration.ofHours(1));

    assertThatThrownBy(() -> pastToken(userId, Duration.ZERO, false))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("uq_t_email_verification_tokens_live_user");
  }

  // 並行した 2 つ目の再送を再現する: 相手の取引が失効と挿入を済ませて未コミットの間、こちらの失効は同じ行のロックを待つ。
  // 相手のコミット後、こちらの失効は相手が挿入した行を写しに持たず生かしたままにし、続く挿入が一意索引に当たる。
  @Test
  void 並行した再送が生きたトークンの一意索引に当たれば429へ写す() throws Exception {
    long userId = registered(email(), Duration.ofSeconds(61));
    try (Connection rival = context.getBean(DataSource.class).getConnection()) {
      rival.setAutoCommit(false);
      try (PreparedStatement revoke =
          rival.prepareStatement(
              "update t_email_verification_tokens set revoked_at = now()"
                  + " where user_id = ? and consumed_at is null and revoked_at is null")) {
        revoke.setLong(1, userId);
        revoke.executeUpdate();
      }
      try (PreparedStatement insert =
          rival.prepareStatement(
              "insert into t_email_verification_tokens"
                  + " (user_id, token_digest, email, issued_at, expires_at)"
                  + " values (?, ?, 'rival@example.test', now(), now() + interval '1 day')")) {
        insert.setLong(1, userId);
        insert.setString(2, UUID.randomUUID().toString());
        insert.executeUpdate();
      }
      CompletableFuture<Void> resend =
          CompletableFuture.runAsync(() -> verification.resend(userId));
      awaitLockWaiters(1);
      rival.commit();

      assertThatThrownBy(() -> resend.get(10, TimeUnit.SECONDS))
          .hasCauseInstanceOf(TooManyRequestsException.class);
    }
    assertThat(transport.deliveries).isEmpty();
  }

  // 登録して、その発行を ago だけ前へずらす。
  private long registered(String email, Duration ago) {
    registerAndCaptureToken(email);
    transport.deliveries.clear();
    long userId = jdbc.queryForObject("select id from t_users where email = ?", Long.class, email);
    backdate(userId, ago);
    return userId;
  }

  private void backdate(long userId, Duration ago) {
    jdbc.update(
        "update t_email_verification_tokens set issued_at = now() - ?::interval,"
            + " expires_at = now() - ?::interval + interval '1 day' where user_id = ?",
        ago.toSeconds() + " seconds",
        ago.toSeconds() + " seconds",
        userId);
  }

  private void pastRevokedToken(long userId, Duration ago) {
    pastToken(userId, ago, true);
  }

  private void pastToken(long userId, Duration ago, boolean revoked) {
    jdbc.update(
        "insert into t_email_verification_tokens"
            + " (user_id, token_digest, email, issued_at, expires_at, revoked_at)"
            + " values (?, ?, 'old@example.test', now() - ?::interval,"
            + " now() - ?::interval + interval '1 day', case when ? then now() end)",
        userId,
        UUID.randomUUID().toString(),
        ago.toSeconds() + " seconds",
        ago.toSeconds() + " seconds",
        revoked);
  }

  private String liveDigest(long userId) {
    return jdbc.queryForObject(
        "select token_digest from t_email_verification_tokens"
            + " where user_id = ? and consumed_at is null and revoked_at is null",
        String.class,
        userId);
  }

  private int tokens(long userId) {
    return jdbc.queryForObject(
        "select count(*) from t_email_verification_tokens where user_id = ?",
        Integer.class,
        userId);
  }

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
    throw new AssertionError("並行した取引がトークン行のロック待ちで止まっていません");
  }

  private Throwable attempt(String token) {
    try {
      confirmation.confirm(token);
      return null;
    } catch (RuntimeException failure) {
      return failure;
    }
  }

  private void assertRejected(String token) {
    assertThatThrownBy(() -> confirmation.confirm(token))
        .isInstanceOf(ServiceException.class)
        .hasMessage(EmailVerificationConfirmation.REJECTED);
  }

  private String registerAndCaptureToken(String email) {
    transport.deliveries.clear();
    tx.executeWithoutResult(
        status -> registration.register(new MemberRegistrationCommand(email, "pass12345", "検証会員")));
    Matcher link = LINK.matcher(transport.deliveries.get(0).body());
    assertThat(link.find()).isTrue();
    return link.group(1);
  }

  // afterCommit の例外は TransactionTemplate の呼び出し元へ伝播するので、ここで例外が無いことが応答を変えない証明になる。
  private void assertBothPathsRegisterDespiteSendFailure() {
    String byMail = email();
    String code =
        tx.execute(
            status ->
                registration.register(new MemberRegistrationCommand(byMail, "pass12345", "検証会員")));
    String byLine = email();
    tx.executeWithoutResult(
        status -> registration.registerWithLine(byLine, "検証会員", "U-" + UUID.randomUUID()));

    assertThat(code).matches("\\d{12}");
    assertThat(transport.deliveries)
        .extracting(Delivery::recipient)
        .containsExactly(byMail, byLine);
    for (String email : List.of(byMail, byLine)) {
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from t_users u join t_members m on m.user_id = u.id"
                      + " join t_email_verification_tokens t on t.user_id = u.id"
                      + " where u.email = ? and u.email_verified_at is null",
                  Integer.class,
                  email))
          .isOne();
    }
  }

  private void assertVerificationMailSentAfterCommit(String email) {
    assertThat(transport.deliveries).hasSize(1);
    Delivery delivery = transport.deliveries.get(0);
    assertThat(delivery.recipient()).isEqualTo(email);
    assertThat(delivery.subject()).isEqualTo(EmailVerificationMail.SUBJECT);
    // 別の接続から利用者行が見えた = 送信の時点で登録はコミット済み。
    assertThat(delivery.userCommitted()).isTrue();
    Matcher link = LINK.matcher(delivery.body());
    assertThat(link.find()).as(delivery.body()).isTrue();
    String token = link.group(1);

    var user =
        context.getBean(UserRepository.class).findByEmail(email).orElseThrow(AssertionError::new);
    assertThat(user.isEmailVerified()).isFalse();
    Map<String, Object> row =
        jdbc.queryForMap(
            "select token_digest, email, issued_at, expires_at, consumed_at, revoked_at"
                + " from t_email_verification_tokens where user_id = ?",
            user.getId());
    assertThat(row.get("token_digest"))
        .isEqualTo(EmailVerificationMail.digest(token))
        .isNotEqualTo(token);
    assertThat(row.get("email")).isEqualTo(email);
    assertThat(row.get("consumed_at")).isNull();
    assertThat(row.get("revoked_at")).isNull();
    assertThat(
            Duration.between(
                ((Timestamp) row.get("issued_at")).toInstant(),
                ((Timestamp) row.get("expires_at")).toInstant()))
        .isEqualTo(Duration.ofHours(24));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_email_verification_tokens where token_digest = ?",
                Integer.class,
                token))
        .isZero();
  }

  private long notificationDeliveries() {
    return jdbc.queryForObject("select count(*) from t_notification_deliveries", Long.class);
  }

  private static String email() {
    return UUID.randomUUID() + "@example.test";
  }

  record Delivery(String recipient, String subject, String body, boolean userCommitted) {}

  /** 送信を記録する送信手段。登録の取引とは別の接続で利用者行を引き、送信の時点でコミット済みかを残す。 */
  static final class RecordingTransport implements ContactTransport {
    final List<Delivery> deliveries = new CopyOnWriteArrayList<>();
    volatile Supplier<Result> outcome = () -> Result.SENT;
    private final JdbcTemplate observer =
        new JdbcTemplate(
            IntegrationTestDatabase.dataSource(MemberEmailVerificationIntegrationTest.class));

    @Override
    public Channel type() {
      return Channel.EMAIL;
    }

    @Override
    public boolean available() {
      return true;
    }

    @Override
    public Result deliver(String recipient, String subject, String body) {
      Integer visible =
          observer.queryForObject(
              "select count(*) from t_users where email = ?", Integer.class, recipient);
      deliveries.add(new Delivery(recipient, subject, body, visible == 1));
      return outcome.get();
    }
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackageClasses = {UserRepository.class, MemberRepository.class})
  @Import({
    MemberRegistrationService.class,
    EmailVerificationMail.class,
    EmailVerificationConfirmation.class,
    MemberDirectoryService.class,
    MemberEmailVerificationService.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return IntegrationTestDatabase.dataSource(MemberEmailVerificationIntegrationTest.class);
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
    JdbcTemplate jdbc(DataSource source) {
      return new JdbcTemplate(source);
    }

    @Bean
    AppProperties properties() {
      var properties = new AppProperties();
      properties.setDomain("kizuna.test");
      return properties;
    }

    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder(4);
    }

    @Bean
    @DependsOn("liquibase")
    RecordingTransport transport() {
      return new RecordingTransport();
    }
  }
}
