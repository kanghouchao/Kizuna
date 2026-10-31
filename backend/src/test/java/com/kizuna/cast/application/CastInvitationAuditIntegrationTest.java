package com.kizuna.cast.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.cast.api.dto.CastInvitationAcceptRequest;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastInvitationStateException;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.store.StoreShortNames;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.LocalDate;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.support.PersistenceExceptionTranslationInterceptor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** 実PGでキャスト招待の発行・受諾と監査を同じ取引で確定し、原子性と競合を確認する。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CastInvitationAuditIntegrationTest {
  private static final String STAFF = "staff@example.test";
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private CastInvitationService issuing;
  private CastInvitationAcceptanceService accepting;
  private TransactionTemplate transactions;
  private Long storeId;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    issuing = context.getBean(CastInvitationService.class);
    accepting = context.getBean(CastInvitationAcceptanceService.class);
    transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    storeId =
        transactions.execute(
            status ->
                context
                    .getBean(StoreRepository.class)
                    .saveAndFlush(
                        new Store(
                            "検証店", StoreShortNames.next(), UUID.randomUUID() + ".example.test"))
                    .getId());
    transactions.executeWithoutResult(
        status ->
            context
                .getBean(UserRepository.class)
                .saveAndFlush(
                    User.builder()
                        .email(STAFF)
                        .password(UUID.randomUUID().toString())
                        .displayName("担当")
                        .userType(UserType.STAFF)
                        .enabled(true)
                        .roleIds(Set.of(1L))
                        .storeScopeType(StoreScopeType.ALL_STORES)
                        .build()));
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  private String enrollment() {
    return transactions.execute(
        status -> {
          var enrollment = CastEnrollment.builder().startedOn(LocalDate.of(2026, 1, 1)).build();
          enrollment.setStoreId(storeId);
          return context.getBean(CastEnrollmentRepository.class).saveAndFlush(enrollment).getId();
        });
  }

  private String issue(String enrollmentId) {
    return issuing.issue(storeId, enrollmentId, STAFF).token();
  }

  private CastInvitationAcceptRequest request(String token, String email) {
    var request = new CastInvitationAcceptRequest();
    request.setToken(token);
    request.setEmail(email);
    request.setPassword("password-123");
    request.setDisplayName("新規の本人");
    return request;
  }

  private long audits(String action) {
    return jdbc.queryForObject(
        "select count(*) from t_audit_events where action=?", Long.class, action);
  }

  private long users() {
    return jdbc.queryForObject("select count(*) from t_users", Long.class);
  }

  private void rejectAudit(String action) {
    jdbc.execute(
        "alter table t_audit_events add constraint ck_invitation_audit_test check(action <> '"
            + action
            + "') not valid");
  }

  private void allowAudit() {
    jdbc.execute("alter table t_audit_events drop constraint ck_invitation_audit_test");
  }

  @Test
  void issueAndAcceptanceCommitAuditsWithRealVersionsAndGeneratedActor() {
    String enrollmentId = enrollment();
    String token = issue(enrollmentId);
    var issued =
        jdbc.queryForMap(
            "select a.target_id,a.source_id,a.after_values ->> 'version' as version,i.version as stored from t_audit_events a join t_cast_invitations i on i.id=a.target_id where a.action='CAST_INVITATION_ISSUED' and a.source_id=?",
            enrollmentId);
    assertThat(issued.get("version")).isEqualTo(String.valueOf(issued.get("stored")));

    accepting.acceptAsNewUser(token, request(token, "new-" + UUID.randomUUID() + "@example.test"));

    var accepted =
        jdbc.queryForMap(
            "select a.actor_id,a.actor_type,a.store_id,a.after_values ->> 'user_id' as user_id,a.after_values ->> 'acceptance' as acceptance,u.id as linked from t_audit_events a join t_cast_enrollments e on e.id=a.source_id join t_casts c on c.id=e.cast_id join t_users u on u.id=c.user_id where a.action='CAST_INVITATION_ACCEPTED' and a.source_id=?",
            enrollmentId);
    assertThat(accepted)
        .containsEntry("actor_type", "CAST")
        .containsEntry("store_id", storeId)
        .containsEntry("acceptance", "NEW_USER");
    assertThat(accepted.get("actor_id")).isEqualTo(accepted.get("linked"));
    assertThat(accepted.get("user_id")).isEqualTo(String.valueOf(accepted.get("linked")));
    assertThat(
            jdbc.queryForList(
                    "select before_values::text || after_values::text as v from t_audit_events where source_id=?",
                    enrollmentId)
                .toString())
        .doesNotContain(token, "example.test", "新規の本人");
  }

  @Test
  void auditFailureRollsBackIssuedInvitation() {
    String enrollmentId = enrollment();
    long invitations = jdbc.queryForObject("select count(*) from t_cast_invitations", Long.class);
    rejectAudit("CAST_INVITATION_ISSUED");
    try {
      assertThatThrownBy(() -> issue(enrollmentId))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(jdbc.queryForObject("select count(*) from t_cast_invitations", Long.class))
          .isEqualTo(invitations);
    } finally {
      allowAudit();
    }
  }

  @Test
  void auditFailureRollsBackAccountCreationLinkAndInvitationConsumption() {
    String enrollmentId = enrollment();
    String token = issue(enrollmentId);
    long userCount = users();
    long acceptedAudits = audits("CAST_INVITATION_ACCEPTED");
    rejectAudit("CAST_INVITATION_ACCEPTED");
    try {
      assertThatThrownBy(
              () ->
                  accepting.acceptAsNewUser(
                      token, request(token, "x-" + UUID.randomUUID() + "@example.test")))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      allowAudit();
    }
    assertThat(users()).isEqualTo(userCount);
    assertThat(audits("CAST_INVITATION_ACCEPTED")).isEqualTo(acceptedAudits);
    assertThat(
            jdbc.queryForObject(
                "select cast_id from t_cast_enrollments where id=?", String.class, enrollmentId))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "select status from t_cast_invitations where token=?", String.class, token))
        .isEqualTo("PENDING");
  }

  @Test
  void existingUserAcceptanceRollsBackOnAuditFailureThenAuditsRealVersion() {
    String email = "existing-" + UUID.randomUUID() + "@example.test";
    String home = issue(enrollment());
    accepting.acceptAsNewUser(home, request(home, email));
    Long otherStoreId =
        transactions.execute(
            status ->
                context
                    .getBean(StoreRepository.class)
                    .saveAndFlush(
                        new Store(
                            "別店", StoreShortNames.next(), UUID.randomUUID() + ".example.test"))
                    .getId());
    String enrollmentId =
        transactions.execute(
            status -> {
              var enrollment = CastEnrollment.builder().startedOn(LocalDate.of(2026, 1, 1)).build();
              enrollment.setStoreId(otherStoreId);
              return context
                  .getBean(CastEnrollmentRepository.class)
                  .saveAndFlush(enrollment)
                  .getId();
            });
    String token = issuing.issue(otherStoreId, enrollmentId, STAFF).token();
    long acceptedAudits = audits("CAST_INVITATION_ACCEPTED");

    rejectAudit("CAST_INVITATION_ACCEPTED");
    try {
      assertThatThrownBy(() -> accepting.acceptAsExistingUser(token, email, "password-123"))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      allowAudit();
    }
    assertThat(storeIds(email)).containsExactly(storeId);
    assertThat(
            jdbc.queryForObject(
                "select status from t_cast_invitations where token=?", String.class, token))
        .isEqualTo("PENDING");
    assertThat(audits("CAST_INVITATION_ACCEPTED")).isEqualTo(acceptedAudits);

    accepting.acceptAsExistingUser(token, email, "password-123");

    assertThat(storeIds(email)).containsExactlyInAnyOrder(storeId, otherStoreId);
    var event =
        jdbc.queryForMap(
            "select a.after_values ->> 'version' as version,a.after_values ->> 'store_membership_added' as added,i.version as stored from t_audit_events a join t_cast_invitations i on i.id=a.target_id where a.action='CAST_INVITATION_ACCEPTED' and a.source_id=?",
            enrollmentId);
    assertThat(event.get("version")).isEqualTo(String.valueOf(event.get("stored")));
    assertThat(event.get("added")).isEqualTo("true");
  }

  private Set<Long> storeIds(String email) {
    return transactions.execute(
        status ->
            context.getBean(UserRepository.class).findByEmail(email).orElseThrow().getStoreIds());
  }

  @Test
  void concurrentAcceptancesCommitOneAccountAndOneAudit() throws Exception {
    String enrollmentId = enrollment();
    String token = issue(enrollmentId);
    long acceptedAudits = audits("CAST_INVITATION_ACCEPTED");
    long userCount = users();
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var tasks =
          List.of(
              executor.submit(() -> race(token, ready, start)),
              executor.submit(() -> race(token, ready, start)));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(
              List.of(
                  tasks.get(0).get(15, TimeUnit.SECONDS), tasks.get(1).get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(audits("CAST_INVITATION_ACCEPTED")).isEqualTo(acceptedAudits + 1);
    assertThat(users()).isEqualTo(userCount + 1);
  }

  private boolean race(String token, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("同時操作が開始されませんでした");
    try {
      accepting.acceptAsNewUser(token, request(token, "r-" + UUID.randomUUID() + "@example.test"));
      return true;
    } catch (CastInvitationStateException lost) {
      return false;
    }
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(
      basePackageClasses = {
        AuditEventRepository.class,
        CastEnrollmentRepository.class,
        StoreRepository.class
      })
  @Import({
    AuditWriter.class,
    BusinessAudit.class,
    CastInvitationService.class,
    CastInvitationAcceptanceService.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return IntegrationTestDatabase.dataSource(CastInvitationAuditIntegrationTest.class);
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource source, ConfigurableListableBeanFactory beans) {
      var f = new LocalContainerEntityManagerFactoryBean();
      f.setDataSource(source);
      f.setPackagesToScan(
          "com.kizuna.cast.domain",
          "com.kizuna.store.domain",
          "com.kizuna.audit.domain",
          "com.kizuna.user.domain");
      f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      f.setJpaPropertyMap(
          Map.of(
              "hibernate.hbm2ddl.auto",
              "create-drop",
              "hibernate.resource.beans.container",
              new SpringBeanContainer(beans),
              "hibernate.physical_naming_strategy",
              "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
      return f;
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
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder(4);
    }

    @Bean
    UserRepository users(EntityManager em) {
      var factory = new JpaRepositoryFactory(em);
      factory.addRepositoryProxyPostProcessor(
          (proxy, information) ->
              proxy.addAdvice(
                  new PersistenceExceptionTranslationInterceptor(new HibernateJpaDialect())));
      return factory.getRepository(UserRepository.class);
    }
  }
}
