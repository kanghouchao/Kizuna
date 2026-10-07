package com.kizuna.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.kizuna.auth.api.platform.PlatformAuthController;
import com.kizuna.auth.application.AuthSessionService;
import com.kizuna.auth.application.PlatformAuthService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.config.RedisConfig;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.user.application.CredentialOperations;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** 専用 PostgreSQL と Redis の ACL 障害で、コミット後の失効反映漏れを再現する。 */
@EnabledIfEnvironmentVariable(named = "KIZUNA_AUTH_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CredentialRevocationPostgresTest {
  private AnnotationConfigApplicationContext context;
  private PlatformUserRepository users;
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
    users = context.getBean(PlatformUserRepository.class);
    credentials = context.getBean(CredentialOperations.class);
    versions = context.getBean(CredentialVersionService.class);
    redis = context.getBean(RedisTemplate.class);
    transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    decoder = context.getBean(JwtDecoder.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    var provider = new JwtAuthenticationProvider(decoder);
    provider.setJwtAuthenticationConverter(new PlatformJwtAuthenticationConverter());
    var bearer = new BearerTokenAuthenticationFilter(new ProviderManager(provider));
    bearer.setBearerTokenResolver(new PlatformBearerTokenResolver());
    bearer.setAuthenticationEntryPoint(new PlatformAuthenticationEntryPoint());
    var principal = new SecurityContextHolderAwareRequestFilter();
    principal.afterPropertiesSet();
    http =
        MockMvcBuilders.standaloneSetup(
                new PlatformAuthController(
                    context.getBean(PlatformAuthService.class),
                    context.getBean(AuthSessionService.class)))
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

    mutate(user, PlatformUser::resume);
    versions.reflect(user.getEmail(), 0);
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

    assertThat(users.findCredentialVersionByEmail(user.getEmail())).contains(0L);
    assertThat(redis.opsForValue().get(key(user))).isEqualTo("0");
    assertThat(decoder.decode(token).getSubject()).isEqualTo(user.getEmail());
  }

  private PlatformUser createUser(UserType type) {
    return transaction.execute(
        status ->
            users.saveAndFlush(
                PlatformUser.builder()
                    .email(UUID.randomUUID() + "@revocation.test")
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

  private String token(PlatformUser user, long version) {
    return context
        .getBean(PlatformJwtIssuer.class)
        .issue(
            user.getEmail(),
            Map.of(CredentialVersionService.CLAIM, version, "authorities", List.of("PERM_TEST")))
        .token();
  }

  private void mutate(PlatformUser user, Consumer<PlatformUser> mutation) {
    transaction.executeWithoutResult(
        status -> mutation.accept(users.findByIdForUpdate(user.getId()).orElseThrow()));
  }

  private void assertCommittedVersionWithStaleCache(PlatformUser user) {
    assertThat(
            jdbc.queryForObject(
                "select credential_version from t_users where id = ?", Long.class, user.getId()))
        .isEqualTo(1L);
    assertThat(redis.opsForValue().get(key(user))).isEqualTo("0");
  }

  private int meStatus(String token) throws Exception {
    try {
      return http.perform(get("/platform/me").header("Authorization", "Bearer " + token))
          .andReturn()
          .getResponse()
          .getStatus();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private String key(PlatformUser user) {
    return "credential-version:" + user.getEmail();
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
  @EnableJpaRepositories(basePackageClasses = PlatformUserRepository.class)
  @Import({
    PlatformAuthService.class,
    CredentialOperations.class,
    AuthSessionService.class,
    CredentialVersionService.class,
    CredentialVersionValidator.class,
    TokenBlacklistService.class,
    TokenBlacklistValidator.class,
    JwtDecoderConfig.class,
    JwtEncoderConfig.class,
    PlatformJwtIssuer.class,
    RedisConfig.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      var url = System.getenv("KIZUNA_AUTH_TEST_JDBC_URL");
      var source = new DriverManagerDataSource(url, "postgres", "");
      var schema = "auth_" + UUID.randomUUID().toString().replace("-", "");
      new JdbcTemplate(source).execute("create schema " + schema);
      source.setUrl(url + "?currentSchema=" + schema);
      return source;
    }

    @Bean
    SpringLiquibase liquibase(DataSource source) {
      var migration = new SpringLiquibase();
      migration.setDataSource(source);
      migration.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
      migration.setContexts("production");
      migration.setChangeLogParameters(
          Map.of(
              "initialAdminPasswordHash",
              new BCryptPasswordEncoder().encode(UUID.randomUUID().toString())));
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
      return new LettuceConnectionFactory(
          "127.0.0.1", Integer.parseInt(System.getenv("KIZUNA_AUTH_TEST_REDIS_PORT")));
    }

    @Bean
    StoreContext storeContext() {
      return new StoreContext();
    }

    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder();
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
