package com.kizuna.store.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.store.StoreShortNames;
import com.kizuna.store.api.dto.StoreCreateDTO;
import com.kizuna.store.api.dto.StoreUpdateDTO;
import com.kizuna.store.domain.AttendanceRecordCheck;
import com.kizuna.store.domain.CompletedOrderCheck;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.storeprofile.domain.StoreProfileRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** 実PGで店舗登録の作成・更新・削除と監査を同じ取引で確定し、連鎖件数・FK 拒否・監査失敗時の巻戻しを確認する。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StoreDeleteAuditIntegrationTest {
  private static final String ORDINARY = "ordinary@example.test";
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private StoreRegistryService stores;
  private Long ordinaryId;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    stores = context.getBean(StoreRegistryService.class);
    var transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    ordinaryId =
        transactions.execute(
            status ->
                context
                    .getBean(UserRepository.class)
                    .saveAndFlush(
                        User.builder()
                            .email(ORDINARY)
                            .password(UUID.randomUUID().toString())
                            .displayName("監査担当")
                            .userType(UserType.STAFF)
                            .enabled(true)
                            .roleIds(Set.of(1L))
                            .storeScopeType(StoreScopeType.ALL_STORES)
                            .build())
                    .getId());
    jdbc.execute(
        "alter table t_store_profiles add constraint fk_test_profile_store foreign key(store_id) references t_stores(id) on delete cascade");
    jdbc.execute("create table t_cascade_child(id serial primary key, store_id bigint not null)");
    jdbc.execute(
        "alter table t_cascade_child add constraint fk_test_cascade_child foreign key(store_id) references t_stores(id) on delete cascade");
    jdbc.execute("create table t_services(id serial primary key, store_id bigint not null)");
    jdbc.execute(
        "alter table t_services add constraint fk_t_services_store foreign key(store_id) references t_stores(id)");
    jdbc.execute(
        "alter table t_audit_events add constraint fk_test_audit_actor foreign key(actor_id) references t_users(id)");
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
    if (jdbc != null) jdbc.execute("drop table if exists t_cascade_child, t_services");
    if (context != null) context.close();
  }

  @Test
  void createUpdateDeleteCommitAuditWithCascadeKindsAndCounts() {
    long id = create();
    jdbc.update("insert into t_cascade_child(store_id) values (?), (?)", id, id);
    var rename = new StoreUpdateDTO();
    rename.setName("改名後");
    rename.setShortName(StoreShortNames.next());
    stores.update(String.valueOf(id), rename);
    stores.update(String.valueOf(id), rename);

    stores.delete(String.valueOf(id));

    assertThat(count("select count(*) from t_stores where id=?", id)).isZero();
    assertThat(count("select count(*) from t_cascade_child where store_id=?", id)).isZero();
    assertThat(
            jdbc.queryForList(
                "select action from t_audit_events where store_id=? order by id", String.class, id))
        .containsExactly("STORE_CREATED", "STORE_UPDATED", "STORE_DELETED");
    var deleted =
        jdbc.queryForMap(
            "select actor_id, before_values ->> 'exists' as old_exists, before_values ->> 'status' as old_status, after_values ->> 'exists' as new_exists, after_values ->> 'cascade_deleted' as cascade from t_audit_events where store_id=? and action='STORE_DELETED'",
            id);
    assertThat(deleted)
        .containsEntry("actor_id", ordinaryId)
        .containsEntry("old_exists", "true")
        .containsEntry("old_status", "PREPARING")
        .containsEntry("new_exists", "false");
    assertThat((String) deleted.get("cascade"))
        .contains("t_cascade_child=2")
        .contains("t_store_profiles=1")
        .doesNotContain("t_services");
    assertThat(
            jdbc.queryForObject(
                "select string_agg(before_values::text || after_values::text, '') from t_audit_events where store_id=?",
                String.class,
                id))
        .doesNotContain("改名後");
  }

  @Test
  void foreignKeyRejectionLeavesNoDeleteAuditAndKeepsRows() {
    long id = create();
    jdbc.update("insert into t_services(store_id) values (?)", id);
    long audits = deleteAudits(id);

    assertThatThrownBy(() -> stores.delete(String.valueOf(id)))
        .isInstanceOf(ServiceException.class)
        .hasMessage("サービス設定が存在する店舗は削除できません");

    assertThat(deleteAudits(id)).isEqualTo(audits);
    assertThat(count("select count(*) from t_stores where id=?", id)).isEqualTo(1);
    assertThat(count("select count(*) from t_store_profiles where store_id=?", id)).isEqualTo(1);
  }

  @Test
  void auditFailureRollsBackTheDeletionAndItsCascade() {
    long id = create();
    jdbc.execute(
        "alter table t_audit_events add constraint ck_store_delete_audit_test check(action <> 'STORE_DELETED') not valid");
    try {
      assertThatThrownBy(() -> stores.delete(String.valueOf(id)))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      jdbc.execute("alter table t_audit_events drop constraint ck_store_delete_audit_test");
    }
    assertThat(deleteAudits(id)).isZero();
    assertThat(count("select count(*) from t_stores where id=?", id)).isEqualTo(1);
    assertThat(count("select count(*) from t_store_profiles where store_id=?", id)).isEqualTo(1);
  }

  private long create() {
    var request = new StoreCreateDTO();
    request.setName("検証店");
    request.setShortName(StoreShortNames.next());
    request.setDomain(UUID.randomUUID() + ".example.test");
    return stores.create(request);
  }

  private long count(String sql, long id) {
    return jdbc.queryForObject(sql, Long.class, id);
  }

  private long deleteAudits(long id) {
    return count(
        "select count(*) from t_audit_events where action='STORE_DELETED' and store_id=?", id);
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(
      basePackageClasses = {
        AuditEventRepository.class,
        StoreRepository.class,
        StoreProfileRepository.class
      })
  @Import({AuditWriter.class, BusinessAudit.class, StoreRegistryService.class})
  static class Config {
    @Bean
    DataSource dataSource() {
      return IntegrationTestDatabase.dataSource(StoreDeleteAuditIntegrationTest.class);
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource source, ConfigurableListableBeanFactory beans) {
      var f = new LocalContainerEntityManagerFactoryBean();
      f.setDataSource(source);
      f.setPackagesToScan(
          "com.kizuna.store.domain",
          "com.kizuna.storeprofile.domain",
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
    CompletedOrderCheck completedOrders() {
      return storeId -> false;
    }

    @Bean
    AttendanceRecordCheck attendanceRecords() {
      return storeId -> false;
    }

    @Bean
    PointLedgerService pointLedger() {
      return mock(PointLedgerService.class);
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
