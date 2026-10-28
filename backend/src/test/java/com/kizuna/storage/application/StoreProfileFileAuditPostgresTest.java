package com.kizuna.storage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.storage.api.FileUploadController;
import com.kizuna.storeprofile.api.dto.StoreProfileMapperImpl;
import com.kizuna.storeprofile.api.dto.StoreProfileUpdateRequest;
import com.kizuna.storeprofile.application.StoreProfileService;
import com.kizuna.storeprofile.domain.StoreProfile;
import com.kizuna.storeprofile.domain.StoreProfileRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.util.ArrayList;
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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** 実PGで店舗プロフィール更新の原子性と、ファイル登録の「保存成功後に独立取引で監査」の順序を確認する。 */
@EnabledIfEnvironmentVariable(
    named = "KIZUNA_STORE_PROFILE_FILE_AUDIT_TEST_JDBC_URL",
    matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StoreProfileFileAuditPostgresTest {
  private static final String ORDINARY = "ordinary@example.test";
  private static final Long STORE_ID = 11L;
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private StoreProfileService profiles;
  private TransactionTemplate transactions;
  private Long ordinaryId;
  private final List<String> stored = new ArrayList<>();
  private boolean storeFails;
  private FileUploadController uploads;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    profiles = context.getBean(StoreProfileService.class);
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
    FileStorageService storage =
        (prefix, bucket, file) -> {
          if (storeFails) throw new IllegalStateException("s3");
          String path = bucket + "/" + prefix + "/" + file.getOriginalFilename();
          stored.add(path);
          return path;
        };
    var properties = new AppProperties();
    properties.setUpload(new AppProperties.Upload());
    uploads = new FileUploadController(storage, properties, context.getBean(StoredFileAudit.class));
    transactions.executeWithoutResult(
        status ->
            context
                .getBean(StoreProfileRepository.class)
                .saveAndFlush(StoreProfile.createDefault(STORE_ID)));
  }

  @BeforeEach
  void authenticate() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                ORDINARY, null, List.of(new SimpleGrantedAuthority("PERM_STORE_PROFILE_MANAGE"))));
    storeFails = false;
    stored.clear();
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
  void profileUpdateCommitsWithRealVersionAndRollsBackOnAuditFailure() {
    long before = audits("STORE_PROFILE");
    profiles.update(STORE_ID, profile("https://example.test/a.png", "秘密の紹介文"));
    profiles.update(STORE_ID, profile("https://example.test/a.png", "秘密の紹介文"));

    var row =
        jdbc.queryForMap(
            "select action,actor_id,store_id,before_values ->> 'version' as b,after_values ->> 'version' as a,after_values ->> 'changed_fields' as c from t_audit_events where target_type='STORE_PROFILE' and target_id=?",
            String.valueOf(STORE_ID));
    assertThat(audits("STORE_PROFILE") - before).isEqualTo(1);
    assertThat(row)
        .containsEntry("action", "STORE_PROFILE_UPDATED")
        .containsEntry("actor_id", ordinaryId)
        .containsEntry("store_id", STORE_ID)
        .containsEntry("c", "logo_url,description");
    assertThat(row.get("a")).isNotEqualTo(row.get("b"));
    assertThat(
            jdbc.queryForObject(
                "select string_agg(before_values::text || after_values::text, '') from t_audit_events where target_type='STORE_PROFILE'",
                String.class))
        .doesNotContain("秘密", "a.png", ORDINARY);

    failAuditFor("STORE_PROFILE_UPDATED");
    try {
      assertThatThrownBy(
              () -> profiles.update(STORE_ID, profile("https://example.test/b.png", "別")))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      dropAuditFailure();
    }
    assertThat(
            jdbc.queryForObject(
                "select logo_url from t_store_profiles where store_id=?", String.class, STORE_ID))
        .isEqualTo("https://example.test/a.png");
    assertThat(audits("STORE_PROFILE") - before).isEqualTo(1);
  }

  @Test
  void uploadAuditsAfterStoreInIndependentTransaction() {
    uploads.upload(STORE_ID, file("one.jpg"), "public");
    // 外側の業務取引が巻き戻っても、保存済みファイルの監査は独立取引で確定している。
    transactions.executeWithoutResult(
        status -> {
          uploads.upload(STORE_ID, file("two.jpg"), "public");
          status.setRollbackOnly();
        });

    var rows =
        jdbc.queryForList(
            "select target_id,store_id,actor_id,after_values ->> 'bucket' as bucket,after_values ->> 'size' as size,after_values ->> 'content_type' as type from t_audit_events where action='FILE_UPLOADED' order by id");
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0))
        .containsEntry("target_id", "public/11/one.jpg")
        .containsEntry("store_id", STORE_ID)
        .containsEntry("actor_id", ordinaryId)
        .containsEntry("bucket", "public")
        .containsEntry("size", "3")
        .containsEntry("type", "image/jpeg");
    assertThat(rows.get(1)).containsEntry("target_id", "public/11/two.jpg");
    assertThat(
            jdbc.queryForObject(
                "select string_agg(before_values::text || after_values::text, '') from t_audit_events where action='FILE_UPLOADED'",
                String.class))
        .doesNotContain("abc", "one.jpg", ORDINARY);
  }

  @Test
  void failedStoreWritesNoAudit() {
    storeFails = true;

    assertThatThrownBy(() -> uploads.upload(STORE_ID, file("none.jpg"), "public"))
        .isInstanceOf(IllegalStateException.class);

    assertThat(auditedPaths()).doesNotContain("public/11/none.jpg");
  }

  @Test
  void auditFailureKeepsStoredFileAndResponse() {
    failAuditFor("FILE_UPLOADED");
    try {
      var response = uploads.upload(STORE_ID, file("window.jpg"), "public");
      assertThat(response.getStatusCode().value()).isEqualTo(201);
    } finally {
      dropAuditFailure();
    }

    assertThat(stored).containsExactly("public/11/window.jpg");
    assertThat(auditedPaths()).doesNotContain("public/11/window.jpg");
  }

  private List<String> auditedPaths() {
    return jdbc.queryForList(
        "select target_id from t_audit_events where action='FILE_UPLOADED'", String.class);
  }

  private static MockMultipartFile file(String name) {
    return new MockMultipartFile("file", name, "image/jpeg", "abc".getBytes());
  }

  private static StoreProfileUpdateRequest profile(String logo, String description) {
    var request = new StoreProfileUpdateRequest();
    request.setLogoUrl(logo);
    request.setDescription(description);
    return request;
  }

  private void failAuditFor(String action) {
    jdbc.execute(
        "alter table t_audit_events add constraint ck_store_profile_file_audit_test check(action <> '"
            + action
            + "') not valid");
  }

  private void dropAuditFailure() {
    jdbc.execute("alter table t_audit_events drop constraint ck_store_profile_file_audit_test");
  }

  private long audits(String targetType) {
    return jdbc.queryForObject(
        "select count(*) from t_audit_events where target_type=?", Long.class, targetType);
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(
      basePackageClasses = {AuditEventRepository.class, StoreProfileRepository.class},
      includeFilters =
          @ComponentScan.Filter(
              type = FilterType.ASSIGNABLE_TYPE,
              classes = {AuditEventRepository.class, StoreProfileRepository.class}))
  @Import({
    AuditWriter.class,
    BusinessAudit.class,
    StoreProfileService.class,
    StoreProfileMapperImpl.class,
    StoredFileAudit.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
          System.getenv("KIZUNA_STORE_PROFILE_FILE_AUDIT_TEST_JDBC_URL"), "postgres", "");
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource source, ConfigurableListableBeanFactory beans) {
      var f = new LocalContainerEntityManagerFactoryBean();
      f.setDataSource(source);
      f.setPackagesToScan(
          "com.kizuna.storeprofile.domain", "com.kizuna.audit.domain", "com.kizuna.user.domain");
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
