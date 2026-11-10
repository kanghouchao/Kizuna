package com.kizuna.store.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.store.StoreShortNames;
import com.kizuna.store.domain.AttendanceRecordCheck;
import com.kizuna.store.domain.CompletedOrderCheck;
import com.kizuna.store.domain.StorePatch;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.storeprofile.domain.StoreProfileRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.application.CreatedStoreGrant;
import com.kizuna.user.application.InitialSetupCommand;
import com.kizuna.user.application.InitialSetupService;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.SystemRole;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.support.PersistenceExceptionTranslationInterceptor;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** 実PGで、店舗の管理が呼び出し者の店舗集合の内側に限られ、作成者の集合へ新店舗が同じ取引で加わることを確認する。統括は初期設定で登録し、 空の店舗集合から始まる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StoreScopeManagementIntegrationTest {
  private static final String HQ = "hq@example.test";
  private static final String MANAGER = "manager@example.test";
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private TransactionTemplate transactions;
  private StoreRegistryService stores;
  private Long managerId;
  private long hqId;
  private List<Long> hqStoresAfterSetup;
  private long storeA;
  private long storeB;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    stores = context.getBean(StoreRegistryService.class);
    transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    transactions.executeWithoutResult(
        status ->
            context
                .getBean(RoleRepository.class)
                .saveAndFlush(
                    Role.builder()
                        .name(SystemRole.ADMIN.getRoleName())
                        .systemRole(true)
                        .permissionIds(Set.of(1L))
                        .build()));
    hqId =
        context
            .getBean(InitialSetupService.class)
            .register(new InitialSetupCommand(HQ, UUID.randomUUID().toString(), "統括"));
    hqStoresAfterSetup = storeIdsOf(hqId);
    actAs(HQ);
    storeA = create();
    storeB = create();
    managerId = saveUser(MANAGER, Set.of(storeA));
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
  void listsOnlyStoresInTheSet() {
    var all = new StoreScope(Set.of(storeA, storeB));
    var onlyA = new StoreScope(Set.of(storeA));

    assertThat(stores.list(onlyA, null, PageRequest.of(0, 100)).map(StoreView::id))
        .contains(storeA)
        .doesNotContain(storeB);
    assertThat(stores.list(all, null, PageRequest.of(0, 100)).map(StoreView::id))
        .contains(storeA, storeB);
    assertThat(stores.count(onlyA)).isEqualTo(1);
  }

  @Test
  void emptySetListsCountsAndAuthorizesNoStore() {
    var empty = new StoreScope(Set.of());

    assertThat(stores.list(empty, null, PageRequest.of(0, 10))).isEmpty();
    assertThat(stores.list(empty, "検証店", PageRequest.of(0, 10))).isEmpty();
    assertThat(stores.count(empty)).isZero();
    assertThatThrownBy(() -> stores.getById(empty, String.valueOf(storeA)))
        .isInstanceOf(NotFoundException.class);
    var holder = context.getBean(StoreScopeHolder.class);
    holder.install(() -> empty);
    try {
      assertThat(context.getBean(AdminStoreService.class).listAuthorizedStores()).isEmpty();
    } finally {
      holder.clear();
    }
  }

  @Test
  void searchStaysInsideTheSetAndEscapesLikeWildcards() {
    var onlyA = new StoreScope(Set.of(storeA));

    assertThat(stores.list(onlyA, "検証店", PageRequest.of(0, 100)).map(StoreView::id))
        .containsExactly(storeA);
    assertThat(stores.list(onlyA, "%", PageRequest.of(0, 100))).isEmpty();
  }

  @Test
  void storeOutsideTheSetCannotBeReadUpdatedOrDeleted() {
    actAs(MANAGER);
    var onlyA = new StoreScope(Set.of(storeA));
    String outside = String.valueOf(storeB);
    long auditsBefore = audits(storeB);

    assertThatThrownBy(() -> stores.getById(onlyA, outside)).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () -> stores.update(onlyA, outside, new StorePatch("改名", StoreShortNames.next())))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> stores.delete(onlyA, outside)).isInstanceOf(NotFoundException.class);

    assertThat(stores.getById(new StoreScope(Set.of(storeB)), outside).name()).isEqualTo("検証店");
    assertThat(audits(storeB)).isEqualTo(auditsBefore);
  }

  @Test
  void storeInsideTheSetCanBeReadUpdatedAndDeleted() {
    actAs(HQ);
    long target = create();
    long holder = saveUser(UUID.randomUUID() + "@example.test", Set.of(target));
    actAs(emailOf(holder));
    var scope = new StoreScope(Set.of(target));
    String id = String.valueOf(target);

    assertThat(stores.getById(scope, id).id()).isEqualTo(target);
    stores.update(scope, id, new StorePatch("改名後", StoreShortNames.next()));
    stores.delete(scope, id);

    assertThat(jdbc.queryForObject("select count(*) from t_stores where id=?", Long.class, target))
        .isZero();
  }

  @Test
  void specificCreatorGainsTheNewStoreInTheSameTransaction() {
    actAs(MANAGER);

    long created = create();

    assertThat(storeIdsOf(managerId)).containsExactlyInAnyOrder(storeA, created);
    var grant =
        jdbc.queryForMap(
            "select actor_id, originating_store_id, target_id, before_values ->> 'store_ids' as old_ids, after_values ->> 'store_ids' as new_ids from t_audit_events where action='STAFF_GRANTS_CHANGED' and target_id=? order by id desc limit 1",
            String.valueOf(managerId));
    assertThat(grant)
        .containsEntry("actor_id", managerId)
        .containsEntry("originating_store_id", null)
        .containsEntry("old_ids", String.valueOf(storeA));
    assertThat((String) grant.get("new_ids")).contains(String.valueOf(created));
  }

  @Test
  void initialSetupGrantsAnEmptySetAndTheAdminGainsEachStoreTheyCreate() {
    assertThat(hqStoresAfterSetup).as("初期設定の直後の統括は店舗集合が空").isEmpty();
    assertThat(storeIdsOf(hqId)).as("作成した店舗は作成者の集合へ加わる").contains(storeA, storeB);

    actAs(HQ);
    long created = create();

    assertThat(storeIdsOf(hqId)).contains(created);
  }

  private long create() {
    return stores.create(
        new StoreCreateCommand("検証店", StoreShortNames.next(), UUID.randomUUID() + ".example.test"));
  }

  private long saveUser(String email, Set<Long> storeIds) {
    return transactions.execute(
        status ->
            context
                .getBean(UserRepository.class)
                .saveAndFlush(
                    User.builder()
                        .email(email)
                        .password(UUID.randomUUID().toString())
                        .displayName("店舗担当")
                        .userType(UserType.STAFF)
                        .enabled(true)
                        .roleIds(Set.of(1L))
                        .storeIds(storeIds)
                        .build())
                .getId());
  }

  private String emailOf(long userId) {
    return jdbc.queryForObject("select email from t_users where id=?", String.class, userId);
  }

  private List<Long> storeIdsOf(long userId) {
    return jdbc.queryForList(
        "select store_id from t_user_stores where user_id=?", Long.class, userId);
  }

  private long audits(long storeId) {
    return jdbc.queryForObject(
        "select count(*) from t_audit_events where originating_store_id=?", Long.class, storeId);
  }

  private static void actAs(String email) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of()));
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(
      basePackageClasses = {
        AuditEventRepository.class,
        StoreRepository.class,
        StoreProfileRepository.class
      })
  @Import({
    AuditWriter.class,
    AdminStoreService.class,
    BusinessAudit.class,
    CreatedStoreGrant.class,
    InitialSetupService.class,
    StoreRegistryService.class,
    StoreScopeHolder.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return IntegrationTestDatabase.dataSource(StoreScopeManagementIntegrationTest.class);
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
    PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder(4);
    }

    @Bean
    AppProperties appProperties() {
      return new AppProperties();
    }

    @Bean
    RoleRepository roles(EntityManager em) {
      return new JpaRepositoryFactory(em).getRepository(RoleRepository.class);
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
