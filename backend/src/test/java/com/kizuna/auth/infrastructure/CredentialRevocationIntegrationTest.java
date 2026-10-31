package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.kizuna.auth.api.auth.AuthController;
import com.kizuna.auth.application.AuthService;
import com.kizuna.auth.application.AuthSessionService;
import com.kizuna.auth.application.LoginBenefitTrigger;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.config.RedisConfig;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.user.application.CredentialOperations;
import com.kizuna.user.application.RoleManageHolderGuard;
import com.kizuna.user.domain.PermissionCode;
import com.kizuna.user.domain.Role;
import com.kizuna.user.domain.RoleRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** 専用 PostgreSQL と Redis の ACL 障害で、コミット後の失効反映漏れを再現する。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CredentialRevocationIntegrationTest {
  private AnnotationConfigApplicationContext context;
  private UserRepository users;
  private RoleRepository roles;
  private RoleManageHolderGuard guard;
  private CredentialOperations credentials;
  private CredentialVersionService versions;
  private RedisTemplate<String, Object> redis;
  private TransactionTemplate transaction;
  private JwtDecoder decoder;
  private JdbcTemplate jdbc;
  private MockMvc http;

  @BeforeAll
  @SuppressWarnings("unchecked")
  void connect() throws Exception {
    context = new AnnotationConfigApplicationContext(Config.class);
    users = context.getBean(UserRepository.class);
    roles = context.getBean(RoleRepository.class);
    guard = context.getBean(RoleManageHolderGuard.class);
    credentials = context.getBean(CredentialOperations.class);
    versions = context.getBean(CredentialVersionService.class);
    redis = context.getBean(RedisTemplate.class);
    // 版の鍵は利用者 ID で、この試験は毎回新しいスキーマに ID 1 から採番する。前回の run が残した版が
    // 新しい行の版 0 を「旧い」と見なさないよう、専用 Redis の中身を空にしてから始める。
    redis.execute(
        (RedisCallback<Object>)
            connection -> {
              connection.serverCommands().flushDb();
              return null;
            });
    transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    decoder = context.getBean(JwtDecoder.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    var provider = new JwtAuthenticationProvider(decoder);
    provider.setJwtAuthenticationConverter(new JwtAuthoritiesConverter());
    var converter = new BearerTokenAuthenticationConverter();
    converter.setBearerTokenResolver(new ExemptingBearerTokenResolver());
    var bearer = new BearerTokenAuthenticationFilter(new ProviderManager(provider), converter);
    bearer.setAuthenticationEntryPoint(new JwtAuthenticationEntryPoint());
    var principal = new SecurityContextHolderAwareRequestFilter();
    principal.afterPropertiesSet();
    http =
        MockMvcBuilders.standaloneSetup(
                new AuthController(
                    context.getBean(AuthService.class), context.getBean(AuthSessionService.class)))
            .addFilters(bearer, principal)
            .build();
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @ParameterizedTest
  @EnumSource(
      value = UserType.class,
      names = {"STAFF", "CAST", "MEMBER"})
  void committedStopRejectsOldJwtAfterRedisWriteFailureAndResume(UserType type) throws Exception {
    var user = createUser(type);
    var token = token(user, 0);
    assertThat(meStatus(token)).isEqualTo(200);

    failReflection(() -> mutate(user, credentials::stop));

    assertThat(
            jdbc.queryForObject(
                "select enabled from t_users where id = ?", Boolean.class, user.getId()))
        .isFalse();
    assertCommittedVersionWithStaleCache(user);
    assertThat(meStatus(token)).isEqualTo(401);
    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    assertThat(redis.opsForValue().get(key(user))).isEqualTo("1");

    mutate(user, User::resume);
    versions.reflect(user.getId(), 0);
    assertThat(redis.opsForValue().get(key(user))).isEqualTo("1");
    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    assertThat(decoder.decode(token(user, 1)).getSubject()).isEqualTo(user.getEmail());
  }

  @Test
  void committedPasswordChangeRejectsOldJwtAfterRedisWriteFailure() throws Exception {
    var user = createUser(UserType.MEMBER);
    var token = token(user, 0);
    assertThat(meStatus(token)).isEqualTo(200);

    failReflection(
        () ->
            mutate(
                user,
                current -> credentials.changePassword(current, UUID.randomUUID().toString())));

    assertCommittedVersionWithStaleCache(user);
    assertThat(meStatus(token)).isEqualTo(401);
    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    assertThat(decoder.decode(token(user, 1)).getSubject()).isEqualTo(user.getEmail());
  }

  @Test
  void rollbackDoesNotRevokeCurrentJwt() {
    var user = createUser(UserType.MEMBER);
    var token = token(user, 0);
    decoder.decode(token);
    transaction.executeWithoutResult(
        status -> {
          credentials.stop(users.findByIdForUpdate(user.getId()).orElseThrow());
          status.setRollbackOnly();
        });

    assertThat(users.findCredentialVersionById(user.getId())).contains(0L);
    assertThat(redis.opsForValue().get(key(user))).isEqualTo("0");
    assertThat(decoder.decode(token).getSubject()).isEqualTo(user.getEmail());
  }

  @Test
  void withdrawnMembersOldJwtStaysRejectedAfterTheSameEmailIsReused() throws Exception {
    var withdrawn = createUser(UserType.MEMBER);
    var oldToken = token(withdrawn, 0);
    assertThat(meStatus(oldToken)).isEqualTo(200);

    mutate(withdrawn, current -> credentials.withdraw(current, OffsetDateTime.now()));
    assertThat(meStatus(oldToken)).isEqualTo(401);

    // 退会で解放された電子メールを後継の身分が引き継ぐ（部分一意索引）。版は 0 から始まる。
    var successor = createUser(UserType.MEMBER, withdrawn.getEmail());
    assertThat(successor.getCredentialVersion()).isZero();
    assertThat(meStatus(token(successor, 0))).as("後継の新しいトークンは通る").isEqualTo(200);
    assertThat(meStatus(oldToken)).as("退会者の旧トークンは後継として通らない").isEqualTo(401);
    assertThat(users.findByEmail(withdrawn.getEmail()).map(User::getId))
        .contains(successor.getId());
  }

  @Test
  void concurrentRoleFanOutsOverTheSameHolderBothAdvanceItsVersion() throws Exception {
    long role = createRole();
    var holder = createStaff(role);
    var token = token(holder, 0);
    assertThat(meStatus(token)).isEqualTo(200);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var locked = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      Future<?> first =
          executor.submit(
              () ->
                  transaction.executeWithoutResult(
                      status -> {
                        invalidateHolders(role);
                        locked.countDown();
                        await(release);
                      }));
      assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
      Future<?> second =
          executor.submit(
              () -> transaction.executeWithoutResult(status -> invalidateHolders(role)));
      awaitLockWait();
      release.countDown();
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    assertThat(users.findCredentialVersionById(holder.getId())).contains(2L);
    assertThat(meStatus(token)).isEqualTo(401);
    assertThat(meStatus(token(holder, 1))).isEqualTo(401);
    assertThat(meStatus(token(holder, 2))).isEqualTo(200);
  }

  @Test
  void grantEditReadBeforeARoleFanOutIsRejectedInsteadOfOverwritingTheVersion() throws Exception {
    long role = createRole();
    long otherRole = createRole();
    var holder = createStaff(role);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var read = new CountDownLatch(1);
      var proceed = new CountDownLatch(1);
      // 従業員編集と同じく、版の照合だけを頼りに素の読みで実体を載せてから書く。
      Future<?> edit =
          executor.submit(
              () ->
                  transaction.executeWithoutResult(
                      status -> {
                        User stale = users.findById(holder.getId()).orElseThrow();
                        read.countDown();
                        await(proceed);
                        credentials.reassignGrants(
                            stale, Set.of(otherRole), StoreScopeType.ALL_STORES, Set.of());
                        users.saveAndFlush(stale);
                      }));
      assertThat(read.await(10, TimeUnit.SECONDS)).isTrue();
      transaction.executeWithoutResult(status -> invalidateHolders(role));
      proceed.countDown();
      assertThatThrownBy(() -> edit.get(10, TimeUnit.SECONDS))
          .hasCauseInstanceOf(ObjectOptimisticLockingFailureException.class);
    } finally {
      executor.shutdownNow();
    }

    assertThat(users.findCredentialVersionById(holder.getId())).contains(1L);
    assertThat(users.findById(holder.getId()).orElseThrow().getRoleIds()).containsExactly(role);
  }

  @Test
  void roleFanOutAndSuspensionOfAnotherHolderDoNotDeadlock() throws Exception {
    long role = createRole(PermissionCode.ROLE_MANAGE);
    var first = createStaff(role);
    var second = createStaff(role);
    var executor = Executors.newFixedThreadPool(3);
    try {
      var held = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      // 先頭の保持者を押さえて、権限変更の保持者走査をそこで待たせる。停止はその後ろから二人目を先に押さえにくる。
      Future<?> blocker =
          executor.submit(
              () ->
                  transaction.executeWithoutResult(
                      status -> {
                        users.findByIdForUpdate(first.getId()).orElseThrow();
                        held.countDown();
                        await(release);
                      }));
      assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
      Future<?> fanOut =
          executor.submit(
              () -> transaction.executeWithoutResult(status -> invalidateHolders(role)));
      awaitLockWaiters(1);
      Future<?> suspension =
          executor.submit(
              () ->
                  transaction.executeWithoutResult(
                      status -> {
                        User target = guard.loadForSuspension(second.getId());
                        credentials.stop(target);
                        users.saveAndFlush(target);
                      }));
      awaitLockWaiters(2);
      release.countDown();
      blocker.get(10, TimeUnit.SECONDS);
      fanOut.get(10, TimeUnit.SECONDS);
      suspension.get(10, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    assertThat(users.findCredentialVersionById(first.getId())).contains(1L);
    assertThat(users.findCredentialVersionById(second.getId())).contains(2L);
  }

  /** RoleService.update が権限集合を変えたときに保持者に対して行うことと同じ（RoleService はこの試験の文脈に載せない）。 */
  private void invalidateHolders(long roleId) {
    List<User> holders = guard.lockRoleHolders(roleId);
    holders.forEach(credentials::invalidateSessions);
    users.saveAllAndFlush(holders);
  }

  private long createRole() {
    return createRole(PermissionCode.ORDER_MANAGE);
  }

  private long createRole(PermissionCode permission) {
    return transaction.execute(
        status ->
            roles
                .saveAndFlush(
                    Role.builder()
                        .name("失効検証-" + UUID.randomUUID())
                        .systemRole(false)
                        .permissionIds(
                            Set.of(
                                jdbc.queryForObject(
                                    "select id from t_permissions where code = ?",
                                    Long.class,
                                    permission.name())))
                        .build())
                .getId());
  }

  private User createStaff(long roleId) {
    return transaction.execute(
        status ->
            users.saveAndFlush(
                User.builder()
                    .email(UUID.randomUUID() + "@revocation.test")
                    .password(UUID.randomUUID().toString())
                    .displayName("失効検証")
                    .enabled(true)
                    .userType(UserType.STAFF)
                    .roleIds(Set.of(roleId))
                    .storeScopeType(StoreScopeType.ALL_STORES)
                    .build()));
  }

  private void awaitLockWait() throws InterruptedException {
    awaitLockWaiters(1);
  }

  private void awaitLockWaiters(int expected) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      Long waiting =
          jdbc.queryForObject(
              "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'",
              Long.class);
      if (waiting != null && waiting >= expected) return;
      Thread.sleep(20);
    }
    throw new AssertionError("後続の失効が保持者の行ロックを待っていません");
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("待ち合わせが時間切れです");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new AssertionError(ex);
    }
  }

  private User createUser(UserType type) {
    return createUser(type, UUID.randomUUID() + "@revocation.test");
  }

  private User createUser(UserType type, String email) {
    return transaction.execute(
        status ->
            users.saveAndFlush(
                User.builder()
                    .email(email)
                    .password(UUID.randomUUID().toString())
                    .displayName("失効検証")
                    .enabled(true)
                    .userType(type)
                    .roleIds(
                        type == UserType.STAFF
                            ? Set.of(jdbc.queryForObject("select min(id) from t_roles", Long.class))
                            : Set.of())
                    .storeScopeType(
                        type == UserType.MEMBER
                            ? StoreScopeType.SPECIFIC_STORES
                            : StoreScopeType.ALL_STORES)
                    .build()));
  }

  private String token(User user, long version) {
    return context
        .getBean(JwtIssuer.class)
        .issue(
            user.getEmail(),
            Map.of(
                CredentialVersionService.CLAIM,
                version,
                CredentialVersionService.USER_ID_CLAIM,
                user.getId(),
                "authorities",
                List.of("PERM_TEST")))
        .token();
  }

  private void mutate(User user, Consumer<User> mutation) {
    transaction.executeWithoutResult(
        status -> mutation.accept(users.findByIdForUpdate(user.getId()).orElseThrow()));
  }

  private void assertCommittedVersionWithStaleCache(User user) {
    assertThat(
            jdbc.queryForObject(
                "select credential_version from t_users where id = ?", Long.class, user.getId()))
        .isEqualTo(1L);
    assertThat(redis.opsForValue().get(key(user))).isEqualTo("0");
  }

  private int meStatus(String token) throws Exception {
    try {
      return http.perform(get("/me").header("Authorization", "Bearer " + token))
          .andReturn()
          .getResponse()
          .getStatus();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private String key(User user) {
    return "credential-version:" + user.getId();
  }

  private void failReflection(Runnable mutation) {
    acl("-eval", "-evalsha");
    try {
      assertThatThrownBy(mutation::run).isInstanceOf(DataAccessException.class);
    } finally {
      acl("+eval", "+evalsha");
    }
  }

  private void acl(String... permissions) {
    String[] args = {"SETUSER", "default", permissions[0], permissions[1]};
    byte[][] bytes =
        Arrays.stream(args)
            .map(value -> value.getBytes(StandardCharsets.UTF_8))
            .toArray(byte[][]::new);
    redis.execute((RedisCallback<Object>) connection -> connection.execute("ACL", bytes));
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackageClasses = UserRepository.class)
  @Import({
    AuthService.class,
    CredentialOperations.class,
    RoleManageHolderGuard.class,
    AuthSessionService.class,
    CredentialVersionService.class,
    CredentialVersionValidator.class,
    TokenBlacklistService.class,
    TokenBlacklistValidator.class,
    JwtDecoderConfig.class,
    JwtEncoderConfig.class,
    JwtIssuer.class,
    RedisConfig.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return IntegrationTestDatabase.dataSource(CredentialRevocationIntegrationTest.class);
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

    @Bean
    LettuceConnectionFactory redisConnectionFactory() {
      // ACL を書き換えるので、task test:integration が立てる専用 Redis だけに向ける。
      return new LettuceConnectionFactory(System.getenv("KIZUNA_TEST_REDIS_HOST"), 6379);
    }

    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder();
    }

    /** ログイン特典の発火は認証の外側の関心で、この試験の対象ではない。 */
    @Bean
    LoginBenefitTrigger loginBenefitTrigger() {
      return mock(LoginBenefitTrigger.class);
    }

    @Bean
    AuthenticationManager authenticationManager() {
      return mock(AuthenticationManager.class);
    }

    @Bean
    AppProperties properties() {
      var properties = new AppProperties();
      var jwt = new AppProperties.Jwt();
      jwt.setSecret(UUID.randomUUID().toString());
      jwt.setExpiration(3_600_000L);
      properties.setJwt(jwt);
      return properties;
    }
  }
}
