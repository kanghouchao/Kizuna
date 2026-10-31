package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.user.api.dto.AdminStaffCreateRequest;
import com.kizuna.user.api.dto.AdminStaffUpdateRequest;
import com.kizuna.user.api.dto.ServiceIdentityCreateRequest;
import com.kizuna.user.api.dto.ServiceIdentityUpdateRequest;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManagerFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** ロールを付与する全経路が、利用者行を書く前に付与するロール行で権限変更の確定を待つことを実 PostgreSQL で確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleGrantLockOrderIntegrationTest {
  private AnnotationConfigApplicationContext context;
  private UserRepository users;
  private RoleRepository roles;
  private StoreScopeHolder scopes;
  private TransactionTemplate transaction;
  private JdbcTemplate jdbc;
  private ExecutorService executor;
  private String actorEmail;

  enum GrantPath {
    STAFF_CREATE,
    STAFF_UPDATE,
    SERVICE_CREATE,
    SERVICE_UPDATE
  }

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    users = context.getBean(UserRepository.class);
    roles = context.getBean(RoleRepository.class);
    scopes = context.getBean(StoreScopeHolder.class);
    transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    actorEmail = createStaff(createRole(PermissionCode.ROLE_MANAGE)).getEmail();
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
  @EnumSource(GrantPath.class)
  void grantWaitsOnTheRoleRowBeforeWritingTheUser(GrantPath path) throws Exception {
    long kept = createRole(PermissionCode.ORDER_MANAGE);
    long granted = createRole(PermissionCode.ORDER_MANAGE);
    User target =
        switch (path) {
          case STAFF_UPDATE -> createStaff(kept);
          case SERVICE_UPDATE -> createServiceIdentity(kept);
          case STAFF_CREATE, SERVICE_CREATE -> null;
        };
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    Future<?> change =
        executor.submit(
            () ->
                transaction.executeWithoutResult(
                    status -> {
                      roles.findByIdForUpdate(granted).orElseThrow();
                      locked.countDown();
                      await(release);
                    }));
    assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

    Future<Long> grant = executor.submit(() -> asActor(() -> grant(path, target, kept, granted)));
    assertThat(awaitBlockedStatement()).contains("t_roles").contains("for share");
    release.countDown();
    change.get(10, TimeUnit.SECONDS);

    long grantee = grant.get(10, TimeUnit.SECONDS);
    assertThat(
            jdbc.queryForList(
                "select role_id from t_user_roles where user_id = ?", Long.class, grantee))
        .contains(granted);
  }

  private long grant(GrantPath path, User target, long kept, long granted) {
    return switch (path) {
      case STAFF_CREATE -> {
        var request = new AdminStaffCreateRequest();
        request.setEmail(UUID.randomUUID() + "@lock-order.test");
        request.setPassword(UUID.randomUUID().toString());
        request.setDisplayName("付与検証");
        request.setRoleIds(Set.of(granted));
        request.setStoreScopeType(StoreScopeType.ALL_STORES);
        request.setStoreIds(Set.of());
        yield context.getBean(AdminStaffService.class).create(request, actorEmail).id();
      }
      case STAFF_UPDATE -> {
        var request = new AdminStaffUpdateRequest();
        request.setRoleIds(Set.of(kept, granted));
        request.setStoreScopeType(StoreScopeType.ALL_STORES);
        request.setStoreIds(Set.of());
        request.setVersion(target.getVersion());
        yield context
            .getBean(AdminStaffService.class)
            .update(target.getId(), request, actorEmail)
            .id();
      }
      case SERVICE_CREATE -> {
        var request = new ServiceIdentityCreateRequest();
        request.setDisplayName("付与検証");
        request.setRoleIds(Set.of(granted));
        request.setStoreScopeType(StoreScopeType.ALL_STORES);
        request.setStoreIds(Set.of());
        yield context.getBean(ServiceIdentityService.class).create(request).id();
      }
      case SERVICE_UPDATE -> {
        var request = new ServiceIdentityUpdateRequest();
        request.setRoleIds(Set.of(kept, granted));
        request.setStoreScopeType(StoreScopeType.ALL_STORES);
        request.setStoreIds(Set.of());
        request.setVersion(target.getVersion());
        yield context.getBean(ServiceIdentityService.class).update(target.getId(), request).id();
      }
    };
  }

  private <T> T asActor(Callable<T> call) throws Exception {
    scopes.install(() -> new StoreScope(true, Set.of()));
    try {
      return call.call();
    } finally {
      scopes.clear();
    }
  }

  /** ロック待ちの接続がちょうど 1 本になるまで待ち、その待っている文を小文字で返す。 */
  private String awaitBlockedStatement() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      List<String> waiting =
          jdbc.queryForList(
              "select query from pg_stat_activity"
                  + " where datname = current_database() and wait_event_type = 'Lock'",
              String.class);
      if (waiting.size() == 1) return waiting.getFirst().toLowerCase();
      Thread.sleep(20);
    }
    throw new AssertionError("後続の事務がロックを待っていません");
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("待ち合わせが時間切れです");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new AssertionError(ex);
    }
  }

  private long createRole(PermissionCode... codes) {
    Set<Long> permissionIds =
        Set.copyOf(
            jdbc.queryForList(
                "select id from t_permissions where code = any(?)",
                Long.class,
                (Object) Arrays.stream(codes).map(Enum::name).toArray(String[]::new)));
    return transaction.execute(
        status ->
            roles
                .saveAndFlush(
                    Role.builder()
                        .name("付与検証-" + UUID.randomUUID())
                        .systemRole(false)
                        .permissionIds(permissionIds)
                        .build())
                .getId());
  }

  private User createStaff(long roleId) {
    return create(
        User.builder()
            .email(UUID.randomUUID() + "@lock-order.test")
            .password(UUID.randomUUID().toString())
            .userType(UserType.STAFF),
        roleId);
  }

  private User createServiceIdentity(long roleId) {
    return create(User.builder().userType(UserType.SERVICE), roleId);
  }

  private User create(User.UserBuilder builder, long roleId) {
    return transaction.execute(
        status ->
            users.saveAndFlush(
                builder
                    .displayName("付与検証")
                    .enabled(true)
                    .roleIds(Set.of(roleId))
                    .storeScopeType(StoreScopeType.ALL_STORES)
                    .build()));
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackageClasses = UserRepository.class)
  @Import({
    AdminStaffService.class,
    ServiceIdentityService.class,
    RoleManageHolderGuard.class,
    CredentialOperations.class,
    StoreScopeHolder.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return IntegrationTestDatabase.dataSource(RoleGrantLockOrderIntegrationTest.class);
    }

    @Bean
    SpringLiquibase liquibase(DataSource source) {
      var migration = new SpringLiquibase();
      migration.setDataSource(source);
      migration.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
      migration.setContexts("production");
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

    /** 監査の記録は操作者の解決を伴い、この試験の対象（ロック順序）の外にある。 */
    @Bean
    BusinessAudit businessAudit() {
      return mock(BusinessAudit.class);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder();
    }
  }
}
