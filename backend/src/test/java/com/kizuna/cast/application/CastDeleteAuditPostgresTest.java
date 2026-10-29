package com.kizuna.cast.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.cast.api.dto.CastMapper;
import com.kizuna.cast.domain.AttendanceReferenceCheck;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastFieldDefinitionRepository;
import com.kizuna.cast.domain.OrderReferenceCheck;
import com.kizuna.shared.exception.ConflictException;
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
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mapstruct.factory.Mappers;
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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** 実PGでキャスト在籍の物理削除と監査を同じ取引で確定し、連鎖件数・FK 拒否・監査失敗時の巻戻しを確認する。 */
@EnabledIfEnvironmentVariable(named = "KIZUNA_CAST_DELETE_AUDIT_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CastDeleteAuditPostgresTest {
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private CastService casts;
  private CastInvitationService castInvitations;
  private TransactionTemplate transactions;
  private Long storeId;
  private Long ordinaryId;
  private static final String ORDINARY = "ordinary@example.test";

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    casts = context.getBean(CastService.class);
    castInvitations = context.getBean(CastInvitationService.class);
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
    ordinaryId = actor(ORDINARY);
    for (String table :
        List.of(
            "t_cast_profiles",
            "t_cast_enrollment_status_histories",
            "t_cast_enrollment_snapshots")) {
      jdbc.execute(
          "alter table "
              + table
              + " add constraint fk_test_"
              + table
              + " foreign key(enrollment_id) references t_cast_enrollments(id) on delete cascade");
    }
    jdbc.execute(
        "alter table t_cast_invitations add constraint fk_test_invitation_enrollment foreign key(cast_id) references t_cast_enrollments(id) on delete cascade");
    jdbc.execute("create table t_orders(id serial primary key, cast_id varchar not null)");
    jdbc.execute(
        "alter table t_orders add constraint fk_t_orders_cast foreign key(cast_id) references t_cast_enrollments(id)");
    jdbc.execute(
        "alter table t_cast_enrollments add constraint fk_test_enrollment_store foreign key(store_id) references t_stores(id)");
    jdbc.execute(
        "alter table t_audit_events add constraint fk_test_audit_actor foreign key(actor_id) references t_users(id)");
    transactions.executeWithoutResult(
        status -> {
          var definitions = context.getBean(CastFieldDefinitionRepository.class);
          var privateField =
              CastFieldDefinition.builder()
                  .key("private-key")
                  .label("秘密の内部項目")
                  .displayOrder(0)
                  .isPublic(false)
                  .build();
          privateField.setStoreId(storeId);
          definitions.save(privateField);
          var publicField =
              CastFieldDefinition.builder()
                  .key("public-key")
                  .label("秘密の公開項目")
                  .displayOrder(1)
                  .isPublic(true)
                  .build();
          publicField.setStoreId(storeId);
          definitions.save(publicField);
        });
  }

  private Long actor(String email) {
    return transactions.execute(
        status ->
            context
                .getBean(UserRepository.class)
                .saveAndFlush(
                    User.builder()
                        .email(email)
                        .password(UUID.randomUUID().toString())
                        .displayName("監査担当")
                        .userType(UserType.STAFF)
                        .enabled(true)
                        .roleIds(Set.of(1L))
                        .storeScopeType(StoreScopeType.ALL_STORES)
                        .build())
                .getId());
  }

  @BeforeEach
  void authenticate() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(ORDINARY, null, List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void deletionCommitsAuditWithCascadeCountsAndRemovesRows() {
    String id = create();
    castInvitations.issue(storeId, id, ORDINARY);
    long histories = count("t_cast_enrollment_status_histories", id);
    long snapshots = count("t_cast_enrollment_snapshots", id);
    assertThat(count("t_cast_invitations", "cast_id", id)).isEqualTo(1);

    casts.delete(storeId, id, ORDINARY);

    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_cast_enrollments where id=?", Long.class, id))
        .isZero();
    assertThat(count("t_cast_profiles", id)).isZero();
    assertThat(count("t_cast_invitations", "cast_id", id)).isZero();
    var event =
        jdbc.queryForMap(
            "select actor_id,store_id,before_values ->> 'exists' as old_exists,before_values ->> 'enrollment_version' as old_version,after_values ->> 'exists' as new_exists,after_values ->> 'deleted_status_histories' as h,after_values ->> 'deleted_snapshots' as s,after_values ->> 'deleted_invitations' as i,after_values ->> 'deleted_profiles' as p from t_audit_events where action='CAST_ENROLLMENT_DELETED' and target_id=?",
            id);
    assertThat(event)
        .containsEntry("actor_id", ordinaryId)
        .containsEntry("store_id", storeId)
        .containsEntry("old_exists", "true")
        .containsEntry("old_version", "0")
        .containsEntry("new_exists", "false")
        .containsEntry("h", String.valueOf(histories))
        .containsEntry("s", String.valueOf(snapshots))
        .containsEntry("i", "1")
        .containsEntry("p", "1");
    assertThat(
            jdbc.queryForObject(
                "select before_values::text || after_values::text from t_audit_events where action='CAST_ENROLLMENT_DELETED' and target_id=?",
                String.class,
                id))
        .doesNotContain("秘密", ORDINARY);
  }

  @Test
  void foreignKeyRejectionLeavesNoAuditAndKeepsRows() {
    String id = create();
    jdbc.update("insert into t_orders(cast_id) values (?)", id);
    long audits = deleteAudits();

    assertThatThrownBy(() -> casts.delete(storeId, id, ORDINARY))
        .isInstanceOf(ConflictException.class);

    assertThat(deleteAudits()).isEqualTo(audits);
    assertThat(count("t_cast_profiles", id)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_cast_enrollments where id=?", Long.class, id))
        .isEqualTo(1);
  }

  @Test
  void auditFailureRollsBackTheDeletionAndItsCascade() {
    String id = create();
    long histories = count("t_cast_enrollment_status_histories", id);
    long audits = deleteAudits();
    jdbc.execute(
        "alter table t_audit_events add constraint ck_cast_delete_audit_test check(action <> 'CAST_ENROLLMENT_DELETED') not valid");
    try {
      assertThatThrownBy(() -> casts.delete(storeId, id, ORDINARY))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      jdbc.execute("alter table t_audit_events drop constraint ck_cast_delete_audit_test");
    }
    assertThat(deleteAudits()).isEqualTo(audits);
    assertThat(count("t_cast_profiles", id)).isEqualTo(1);
    assertThat(count("t_cast_enrollment_status_histories", id)).isEqualTo(histories);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_cast_enrollments where id=?", Long.class, id))
        .isEqualTo(1);
  }

  private String create() {
    return casts.create(
        storeId,
        new CastCreateCommand(
            "秘密の名前-" + UUID.randomUUID(),
            null,
            LocalDate.of(2026, 1, 1),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null),
        ORDINARY);
  }

  private long count(String table, String id) {
    return count(table, "enrollment_id", id);
  }

  private long count(String table, String column, String id) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where " + column + "=?", Long.class, id);
  }

  private long deleteAudits() {
    return jdbc.queryForObject(
        "select count(*) from t_audit_events where action='CAST_ENROLLMENT_DELETED'", Long.class);
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
    CastService.class,
    CastEnrollmentService.class,
    CastInvitationService.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
          System.getenv("KIZUNA_CAST_DELETE_AUDIT_TEST_JDBC_URL"), "postgres", "");
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
    CastMapper mapper() {
      return Mappers.getMapper(CastMapper.class);
    }

    @Bean
    AttendanceReferenceCheck attendance() {
      return ids -> Set.of();
    }

    @Bean
    OrderReferenceCheck orders() {
      return ids -> Set.of();
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
