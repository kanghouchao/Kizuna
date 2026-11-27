package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;

import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.PermissionRepository;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** 権限集合の置き換えが走っている間ロール行が押さえられていることと、明示ロックの読み口が載せる強さを、実 PostgreSQL で確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleRowLockPremiseIntegrationTest {
  private AnnotationConfigApplicationContext context;
  private RoleRepository roles;
  private UserRepository users;
  private CredentialOperations credentials;
  private RoleManageHolderGuard guard;
  private TransactionTemplate transaction;
  private DataSource dataSource;
  private JdbcTemplate jdbc;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    roles = context.getBean(RoleRepository.class);
    users = context.getBean(UserRepository.class);
    credentials = context.getBean(CredentialOperations.class);
    guard = AopTestUtils.getTargetObject(context.getBean(RoleManageHolderGuard.class));
    transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    dataSource = context.getBean(DataSource.class);
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  // 本体はロール行を FOR NO KEY UPDATE で押さえ、保持者の利用者行を押さえる手前で止まる。探りは自動コミットの別接続で
  // FOR UPDATE NOWAIT を一度だけ試み、待たないのでデッドロックしない。子表の書き込み（外部キー検査の KEY SHARE）より前で
  // 探るので、当たるのはロール行の明示ロックだけ。
  @Test
  void roleServiceHoldsTheRoleRowWhileReplacingPermissions() throws Exception {
    long role = createRole(PermissionCode.ORDER_MANAGE);
    var paused = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            call -> {
              paused.countDown();
              await(release);
              return call.callRealMethod();
            })
        .when(guard)
        .lockRoleHolders(anyLong());
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<?> update =
          executor.submit(
              () ->
                  context
                      .getBean(RoleService.class)
                      .update(1L, role, command(role, PermissionCode.CUSTOMER_MANAGE)));
      assertThat(paused.await(10, TimeUnit.SECONDS)).isTrue();
      try {
        assertThat(probe(role)).isEqualTo("55P03");
      } finally {
        release.countDown();
      }
      update.get(10, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
      reset(guard);
    }
  }

  @Test
  void replacingPermissionsOfAnUnlockedRoleIsRejected() {
    long role = createRole(PermissionCode.ORDER_MANAGE);
    long next = permissionId(PermissionCode.CUSTOMER_MANAGE);
    transaction.executeWithoutResult(
        status -> {
          Role loaded = roles.findById(role).orElseThrow();
          assertThat(probe(role)).as("押さえていないロール行は探りで取れる").isNull();
          assertThatThrownBy(() -> credentials.replaceRolePermissions(loaded, Set.of(next)))
              .isInstanceOf(IllegalStateException.class);
          status.setRollbackOnly();
        });
  }

  // 明示ロックの読み口が載せる強さを、握られた行への探りで見分ける。FOR KEY SHARE（子表の INSERT の外部キー検査が
  // 要求する強さ）が通るなら FOR UPDATE ではなく、FOR SHARE が弾かれるなら FOR SHARE でもない。
  @Test
  void pessimisticWriteReadLeavesTheRowOpenToKeyShare() {
    String email = "lock-strength-" + UUID.randomUUID() + "@example.test";
    long user = createUser(email);
    transaction.executeWithoutResult(
        status -> {
          users.findByEmailForUpdate(email).orElseThrow();
          assertThat(probe("t_users", user, "for key share")).isNull();
          assertThat(probe("t_users", user, "for share")).isEqualTo("55P03");
        });
  }

  @Test
  void nativeForUpdateShutsOutKeyShare() {
    long user = createUser("lock-strength-" + UUID.randomUUID() + "@example.test");
    transaction.executeWithoutResult(
        status -> {
          jdbc.queryForObject("select id from t_users where id = ? for update", Long.class, user);
          assertThat(probe("t_users", user, "for key share")).isEqualTo("55P03");
        });
  }

  /** ロール行の FOR UPDATE NOWAIT を試み、取れれば null、取れなければ SQLState を返す。 */
  private String probe(long role) {
    return probe("t_roles", role, "for update");
  }

  /** 自動コミットの別接続で、指定の強さの行ロックを待たずに試みる。取れれば null、取れなければ SQLState を返す。 */
  private String probe(String table, long id, String strength) {
    try (var connection = dataSource.getConnection();
        var statement =
            connection.prepareStatement(
                "select id from " + table + " where id = ? " + strength + " nowait")) {
      connection.setAutoCommit(true);
      statement.setLong(1, id);
      statement.executeQuery().close();
      return null;
    } catch (SQLException ex) {
      return ex.getSQLState();
    }
  }

  private long createUser(String email) {
    return jdbc.queryForObject(
        "insert into t_users(email,display_name,user_type) values (?,'検証担当','STAFF')"
            + " returning id",
        Long.class,
        email);
  }

  private RoleCommand.Update command(long role, PermissionCode permission) {
    return new RoleCommand.Update(
        jdbc.queryForObject("select name from t_roles where id = ?", String.class, role),
        Set.of(permission.name()),
        jdbc.queryForObject("select version from t_roles where id = ?", Long.class, role));
  }

  private long permissionId(PermissionCode permission) {
    return jdbc.queryForObject(
        "select id from t_permissions where code = ?", Long.class, permission.name());
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
                        .name("ロック前提-" + UUID.randomUUID())
                        .systemRole(false)
                        .permissionIds(permissionIds)
                        .build())
                .getId());
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("待ち合わせが時間切れです");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new AssertionError(ex);
    }
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackageClasses = UserRepository.class)
  @Import({RoleService.class, CredentialOperations.class})
  static class Config {
    @Bean
    DataSource dataSource() {
      return IntegrationTestDatabase.dataSource(RoleRowLockPremiseIntegrationTest.class);
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

    /** 権限集合の置き換えの途中で止めるため、保持者を押さえる口を差し替えられるようにする。 */
    @Bean
    RoleManageHolderGuard roleManageHolderGuard(
        UserRepository users, RoleRepository roles, PermissionRepository permissions) {
      return spy(new RoleManageHolderGuard(users, roles, permissions));
    }

    /** 監査の記録は操作者の解決を伴い、この試験の対象（ロックの前提）の外にある。 */
    @Bean
    BusinessAudit businessAudit() {
      return mock(BusinessAudit.class);
    }
  }
}
