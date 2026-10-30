package com.kizuna.point.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.point.api.dto.BenefitRuleCreateRequest;
import com.kizuna.point.api.dto.BenefitRuleDeactivationRequest;
import com.kizuna.point.api.dto.BenefitRuleMapperImpl;
import com.kizuna.point.api.dto.BenefitRuleUpdateRequest;
import com.kizuna.point.domain.BenefitRuleRepeatPolicy;
import com.kizuna.point.domain.BenefitRuleRepository;
import com.kizuna.point.domain.BenefitRuleType;
import com.kizuna.settings.api.dto.SystemConfigMapperImpl;
import com.kizuna.settings.api.dto.SystemConfigUpdateRequest;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.settings.application.SystemConfigServiceImpl;
import com.kizuna.settings.domain.SystemConfig;
import com.kizuna.settings.domain.SystemConfigRepository;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
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

/** 実PGで特典規則・システム設定の更新と監査を同じ取引で確定し、group-wide 記録・秘密値の非複写・監査失敗時の巻戻しを確認する。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BenefitConfigAuditIntegrationTest {
  private static final String ORDINARY = "ordinary@example.test";
  private static final String SECRET = "秘密の値-" + UUID.randomUUID();
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private BenefitRuleService rules;
  private SystemConfigService configs;
  private TransactionTemplate transactions;
  private Long ordinaryId;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    rules = context.getBean(BenefitRuleService.class);
    configs = context.getBean(SystemConfigService.class);
    transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
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
    if (context != null) context.close();
  }

  @Test
  void ruleLifecycleCommitsGroupWideAuditWithRealVersions() {
    Long id = rules.create(create()).id();
    rules.update(id, update(0L, 800));
    rules.deactivate(id, deactivation(1L));

    var rows =
        jdbc.queryForList(
            "select action,actor_id,store_id,after_values ->> 'version' as v,after_values ->> 'points' as p,after_values ->> 'active' as a,after_values ->> 'changed_fields' as c from t_audit_events where target_type='BENEFIT_RULE' and target_id=? order by id",
            String.valueOf(id));
    assertThat(rows).hasSize(3);
    assertThat(rows.get(0))
        .containsEntry("action", "BENEFIT_RULE_CREATED")
        .containsEntry("actor_id", ordinaryId)
        .containsEntry("v", "0");
    assertThat(rows.get(0).get("store_id")).isNull();
    assertThat(rows.get(1))
        .containsEntry("action", "BENEFIT_RULE_UPDATED")
        .containsEntry("v", "1")
        .containsEntry("p", "800")
        .containsEntry("c", "points");
    assertThat(rows.get(2))
        .containsEntry("action", "BENEFIT_RULE_DEACTIVATED")
        .containsEntry("v", "2")
        .containsEntry("a", "false");
    assertThat(
            jdbc.queryForObject(
                "select string_agg(before_values::text || after_values::text, '') from t_audit_events where target_type='BENEFIT_RULE' and target_id=?",
                String.class,
                String.valueOf(id)))
        .doesNotContain("秘密", ORDINARY);
  }

  @Test
  void auditFailureRollsBackRuleUpdateAndDeactivation() {
    Long id = rules.create(create()).id();
    long audits = audits("BENEFIT_RULE");
    failAuditFor("BENEFIT_RULE_UPDATED", "BENEFIT_RULE_DEACTIVATED");
    try {
      assertThatThrownBy(() -> rules.update(id, update(0L, 900)))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> rules.deactivate(id, deactivation(0L)))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      jdbc.execute("alter table t_audit_events drop constraint ck_benefit_config_audit_test");
    }
    assertThat(audits("BENEFIT_RULE")).isEqualTo(audits);
    var row = jdbc.queryForMap("select points,enabled,version from t_benefit_rules where id=?", id);
    assertThat(row)
        .containsEntry("points", 500)
        .containsEntry("enabled", true)
        .containsEntry("version", 0L);
  }

  @Test
  void secretConfigRecordsChangedFlagOnlyAndRollsBackOnAuditFailure() {
    String secretKey = "smtp_password";
    String plainKey = "smtp_host";
    seed(secretKey, "旧秘密", true);
    seed(plainKey, "old.example.test", false);

    configs.updateConfig(secretKey, config(SECRET));
    configs.updateConfig(plainKey, config("new.example.test"));
    configs.updateConfig(plainKey, config("new.example.test"));

    var secretRow = audit(secretKey);
    assertThat(secretRow)
        .containsEntry("changed", "true")
        .containsEntry("old", null)
        .containsEntry("new", null);
    assertThat(secretRow.get("store_id")).isNull();
    assertThat(audit(plainKey))
        .containsEntry("old", "old.example.test")
        .containsEntry("new", "new.example.test");
    assertThat(audits("SYSTEM_CONFIG")).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select string_agg(before_values::text || after_values::text, '') from t_audit_events where target_type='SYSTEM_CONFIG'",
                String.class))
        .doesNotContain(SECRET, "旧秘密");

    failAuditFor("SYSTEM_CONFIG_UPDATED", "SYSTEM_CONFIG_UPDATED");
    try {
      assertThatThrownBy(() -> configs.updateConfig(plainKey, config("rolled.example.test")))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      jdbc.execute("alter table t_audit_events drop constraint ck_benefit_config_audit_test");
    }
    assertThat(
            jdbc.queryForObject(
                "select config_value from t_system_configs where config_key=?",
                String.class,
                plainKey))
        .isEqualTo("new.example.test");
    assertThat(audits("SYSTEM_CONFIG")).isEqualTo(2);
  }

  private Map<String, Object> audit(String key) {
    return jdbc.queryForMap(
        "select store_id,after_values ->> 'changed' as changed,before_values ->> 'config_value' as old,after_values ->> 'config_value' as new from t_audit_events where target_type='SYSTEM_CONFIG' and target_id=?",
        key);
  }

  private void seed(String key, String value, boolean secret) {
    transactions.executeWithoutResult(
        status ->
            context
                .getBean(SystemConfigRepository.class)
                .saveAndFlush(
                    SystemConfig.builder()
                        .configKey(key)
                        .configValue(value)
                        .secret(secret)
                        .build()));
  }

  private void failAuditFor(String first, String second) {
    jdbc.execute(
        "alter table t_audit_events add constraint ck_benefit_config_audit_test check(action not in ('"
            + first
            + "','"
            + second
            + "')) not valid");
  }

  private long audits(String targetType) {
    return jdbc.queryForObject(
        "select count(*) from t_audit_events where target_type=?", Long.class, targetType);
  }

  private static BenefitRuleCreateRequest create() {
    var request = new BenefitRuleCreateRequest();
    request.setType(BenefitRuleType.VISIT);
    request.setName("秘密の規則名");
    request.setStoreScopeType(StoreScopeType.ALL_STORES);
    request.setRepeatPolicy(BenefitRuleRepeatPolicy.EVERY_TIME);
    request.setPoints(500);
    return request;
  }

  private static BenefitRuleUpdateRequest update(Long version, int points) {
    var request = new BenefitRuleUpdateRequest();
    request.setVersion(version);
    request.setName("秘密の規則名");
    request.setStoreScopeType(StoreScopeType.ALL_STORES);
    request.setRepeatPolicy(BenefitRuleRepeatPolicy.EVERY_TIME);
    request.setPoints(points);
    return request;
  }

  private static BenefitRuleDeactivationRequest deactivation(Long version) {
    var request = new BenefitRuleDeactivationRequest();
    request.setVersion(version);
    return request;
  }

  private static SystemConfigUpdateRequest config(String value) {
    return SystemConfigUpdateRequest.builder().configValue(value).build();
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(
      basePackageClasses = {
        AuditEventRepository.class,
        BenefitRuleRepository.class,
        SystemConfigRepository.class
      },
      includeFilters =
          @ComponentScan.Filter(
              type = FilterType.ASSIGNABLE_TYPE,
              classes = {
                AuditEventRepository.class,
                BenefitRuleRepository.class,
                SystemConfigRepository.class
              }))
  @Import({
    AuditWriter.class,
    BusinessAudit.class,
    BenefitRuleService.class,
    BenefitRuleMapperImpl.class,
    SystemConfigServiceImpl.class,
    SystemConfigMapperImpl.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return IntegrationTestDatabase.dataSource(BenefitConfigAuditIntegrationTest.class);
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource source, ConfigurableListableBeanFactory beans) {
      var f = new LocalContainerEntityManagerFactoryBean();
      f.setDataSource(source);
      f.setPackagesToScan(
          "com.kizuna.point.domain",
          "com.kizuna.settings.domain",
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
