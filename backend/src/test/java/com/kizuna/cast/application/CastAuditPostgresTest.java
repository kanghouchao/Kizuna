package com.kizuna.cast.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.cast.api.dto.CastCreateRequest;
import com.kizuna.cast.api.dto.CastMapper;
import com.kizuna.cast.api.dto.CastUpdateRequest;
import com.kizuna.cast.domain.AttendanceReferenceCheck;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastFieldDefinitionRepository;
import com.kizuna.cast.domain.CastPublicationStatus;
import com.kizuna.cast.domain.OrderReferenceCheck;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.StoreScopeStampListener;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreFilterEnable;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** 実PGの在籍・profile・履歴・監査を同じ取引で操作し、実版本と原子性を確認する。 */
@EnabledIfEnvironmentVariable(named = "KIZUNA_CAST_AUDIT_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CastAuditPostgresTest {
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private CastService casts;
  private CastEnrollmentService lifecycle;
  private TransactionTemplate transactions;
  private Long storeId;
  private Long ordinaryId;
  private Long elevatedId;
  private static final String ORDINARY = "ordinary@example.test";
  private static final String ELEVATED = "elevated@example.test";

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    casts = context.getBean(CastService.class);
    lifecycle = context.getBean(CastEnrollmentService.class);
    transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    storeId =
        transactions.execute(
            status ->
                context
                    .getBean(StoreRepository.class)
                    .saveAndFlush(new Store("検証店", UUID.randomUUID() + ".example.test", null))
                    .getId());
    ordinaryId = actor(ORDINARY);
    elevatedId = actor(ELEVATED);
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
        "alter table t_cast_enrollments add constraint fk_test_enrollment_store foreign key(store_id) references t_stores(id)");
    jdbc.execute(
        "alter table t_audit_events add constraint fk_test_audit_actor foreign key(actor_id) references t_users(id)");
    context.getBean(StoreContext.class).setStoreId(storeId);
    transactions.executeWithoutResult(
        status -> {
          var definitions = context.getBean(CastFieldDefinitionRepository.class);
          definitions.save(
              CastFieldDefinition.builder()
                  .key("private-key")
                  .label("秘密の内部項目")
                  .displayOrder(0)
                  .isPublic(false)
                  .build());
          definitions.save(
              CastFieldDefinition.builder()
                  .key("public-key")
                  .label("秘密の公開項目")
                  .displayOrder(1)
                  .isPublic(true)
                  .build());
        });
    context.getBean(StoreContext.class).clear();
  }

  private Long actor(String email) {
    return transactions.execute(
        status ->
            context
                .getBean(PlatformUserRepository.class)
                .saveAndFlush(
                    PlatformUser.builder()
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
    context.getBean(StoreContext.class).setStoreId(storeId);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(ORDINARY, null, List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    context.getBean(StoreContext.class).clear();
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void separateVersionsAndPrivateOnlyChangesMatchCommittedEntities() {
    String id = create();
    String profile = profileId(id);
    assertVersions(id, 0, 0);
    var request = new CastUpdateRequest();
    request.setCustomFields(Map.of("private-key", "秘密の内部値"));
    casts.update(id, request, ORDINARY);
    assertVersions(id, 1, 0);
    casts.update(id, request, ORDINARY);
    casts.update(id, new CastUpdateRequest(), ORDINARY);
    assertVersions(id, 1, 0);
    assertThat(auditCount(id, profile)).isEqualTo(2);
    request.setName("秘密の変更名");
    request.setCustomFields(Map.of("private-key", "秘密の内部値", "public-key", "秘密の公開値"));
    casts.update(id, request, ORDINARY);
    assertVersions(id, 1, 1);
    casts.changePublication(id, CastPublicationStatus.PUBLISHED);
    casts.changePublication(id, CastPublicationStatus.PUBLISHED);
    assertVersions(id, 1, 2);
    var events =
        jdbc.queryForList(
            "select action,before_values ->> 'enrollment_version' as old_e,after_values ->> 'enrollment_version' as new_e,before_values ->> 'profile_version' as old_p,after_values ->> 'profile_version' as new_p,after_values ->> 'redacted_fields_changed' as fields from t_audit_events where target_id=? order by id",
            id);
    assertThat(events).hasSize(3);
    assertThat(events.get(0)).containsEntry("new_e", "0").containsEntry("new_p", "0");
    assertThat(events.get(1))
        .containsEntry("old_e", "0")
        .containsEntry("new_e", "1")
        .containsEntry("old_p", "0")
        .containsEntry("new_p", "0")
        .containsEntry("fields", "internal_custom_fields");
    assertThat(events.get(2))
        .containsEntry("old_e", "1")
        .containsEntry("new_e", "1")
        .containsEntry("old_p", "0")
        .containsEntry("new_p", "1")
        .containsEntry("fields", "name,public_custom_fields");
    assertThat(
            jdbc.queryForMap(
                "select before_values ->> 'version' as old,after_values ->> 'version' as new,source_id from t_audit_events where target_id=?",
                profile))
        .containsEntry("old", "1")
        .containsEntry("new", "2")
        .containsEntry("source_id", id);
    assertThat(lifecycle.snapshots(id, null, 20).content()).hasSize(1);
    assertThat(
            jdbc.queryForList(
                    "select before_values,after_values from t_audit_events where target_id in (?,?)",
                    id,
                    profile)
                .toString())
        .doesNotContain("秘密", "private-key", "public-key", ORDINARY);
  }

  private String create() {
    var request = new CastCreateRequest();
    request.setName("秘密の名前-" + UUID.randomUUID());
    return casts.create(request, ORDINARY).getId();
  }

  @Test
  void creationAuditFailureRollsBackEnrollmentProfileAndHistory() {
    var before = tableCounts();
    rejectAudit("CAST_ENROLLMENT_CREATED");
    try {
      assertThatThrownBy(this::create).isInstanceOf(DataIntegrityViolationException.class);
      assertThat(tableCounts()).isEqualTo(before);
    } finally {
      allowAudit();
    }
  }

  @Test
  void editAuditFailureRestoresBothEntitiesAndInternalSnapshot() {
    String id = create();
    var before = storedState(id);
    var counts = tableCounts();
    var request = new CastUpdateRequest();
    request.setName("秘密の更新名");
    request.setCustomFields(Map.of("private-key", "内部更新値", "public-key", "公開更新値"));
    rejectAudit("CAST_ENROLLMENT_UPDATED");
    try {
      assertThatThrownBy(() -> casts.update(id, request, ORDINARY))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(storedState(id)).isEqualTo(before);
      assertThat(tableCounts()).isEqualTo(counts);
      assertThat(lifecycle.snapshots(id, null, 20).content()).isEmpty();
    } finally {
      allowAudit();
    }
  }

  @Test
  void publicationAuditFailureRestoresStatusAndProfileVersion() {
    String id = create();
    var before = storedState(id);
    var counts = tableCounts();
    rejectAudit("CAST_PROFILE_PUBLICATION_CHANGED");
    try {
      assertThatThrownBy(() -> casts.changePublication(id, CastPublicationStatus.PUBLISHED))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(storedState(id)).isEqualTo(before);
      assertThat(tableCounts()).isEqualTo(counts);
    } finally {
      allowAudit();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"SUSPENDED", "RESUMED", "WITHDRAWN"})
  void lifecycleAuditFailureRestoresEnrollmentAndHistory(String action) {
    String id = create();
    if (action.equals("RESUMED")) lifecycle.suspend(id, ORDINARY);
    var before = storedState(id);
    var counts = tableCounts();
    rejectAudit("CAST_ENROLLMENT_" + action);
    try {
      assertThatThrownBy(() -> transition(id, action, ORDINARY))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(storedState(id)).isEqualTo(before);
      assertThat(tableCounts()).isEqualTo(counts);
    } finally {
      allowAudit();
    }
  }

  @Test
  void outerRollbackRestoresBothEntitiesSnapshotsAndLifecycleHistory() {
    String id = create();
    var before = storedState(id);
    var counts = tableCounts();
    transactions.executeWithoutResult(
        status -> {
          var request = new CastUpdateRequest();
          request.setName("外側rollbackの秘密");
          request.setCustomFields(Map.of("private-key", "戻す内部値", "public-key", "戻す公開値"));
          casts.update(id, request, ORDINARY);
          casts.changePublication(id, CastPublicationStatus.PUBLISHED);
          lifecycle.suspend(id, ORDINARY);
          status.setRollbackOnly();
        });
    assertThat(storedState(id)).isEqualTo(before);
    assertThat(tableCounts()).isEqualTo(counts);
  }

  private void transition(String id, String action, String actor) {
    switch (action) {
      case "SUSPENDED" -> lifecycle.suspend(id, actor);
      case "RESUMED" -> lifecycle.resume(id, actor);
      case "WITHDRAWN" -> lifecycle.withdraw(id, actor);
      default -> throw new IllegalArgumentException(action);
    }
  }

  @Test
  void elevatedChangesKeepActualActorsVersionsAndHistorySources() {
    String id = create();
    String profile = profileId(id);
    var jwt =
        Jwt.withTokenValue(UUID.randomUUID().toString())
            .header("alg", "HS256")
            .subject(ELEVATED)
            .claim("elevationId", 77L)
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    var request = new CastUpdateRequest();
    request.setName("昇格で変更する秘密名");
    casts.update(id, request, ELEVATED);
    casts.changePublication(id, CastPublicationStatus.PUBLISHED);
    lifecycle.suspend(id, ELEVATED);
    lifecycle.resume(id, ELEVATED);
    lifecycle.withdraw(id, ELEVATED);
    assertVersions(id, 3, 2);
    var actors =
        jdbc.queryForList(
            "select actor_id,actor_type,store_id,after_values ->> 'emergency_elevation_id' as elevation from t_audit_events where target_id in (?,?) order by id",
            id,
            profile);
    assertThat(actors).hasSize(6);
    assertThat(actors.getFirst())
        .containsEntry("actor_id", ordinaryId)
        .containsEntry("elevation", null);
    for (var actor : actors.subList(1, actors.size())) {
      assertThat(actor)
          .containsEntry("actor_id", elevatedId)
          .containsEntry("actor_type", "STAFF")
          .containsEntry("store_id", storeId)
          .containsEntry("elevation", "77");
    }
    var history =
        jdbc.queryForList(
            "select a.action,a.before_values ->> 'version' as old,a.after_values ->> 'version' as new,a.after_values ->> 'ended_at' as ended,a.source_type,h.enrollment_id,h.actor_id,h.new_status from t_audit_events a join t_cast_enrollment_status_histories h on h.id=a.source_id where a.target_id=? order by a.id",
            id);
    assertThat(history).hasSize(3);
    for (int i = 0; i < history.size(); i++) {
      assertThat(history.get(i))
          .containsEntry("old", Integer.toString(i))
          .containsEntry("new", Integer.toString(i + 1))
          .containsEntry("enrollment_id", id)
          .containsEntry("source_type", "CAST_ENROLLMENT_STATUS_HISTORY")
          .containsEntry("actor_id", elevatedId);
    }
    assertThat(history)
        .extracting(row -> row.get("new_status"))
        .containsExactly("SUSPENDED", "ENROLLED", "WITHDRAWN");
    assertThat(history.getLast().get("ended")).isNotNull().isNotEqualTo("");
    assertThat(lifecycle.history(id, null, 20).content()).hasSize(4);
    assertThatThrownBy(() -> lifecycle.suspend(id, ELEVATED)).isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> lifecycle.resume(id, ELEVATED)).isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> lifecycle.withdraw(id, ELEVATED)).isInstanceOf(ServiceException.class);
    assertThat(auditCount(id, profile)).isEqualTo(6);
  }

  @Test
  void concurrentSuspensionCommitsOneHistoryAndOneAudit() throws Exception {
    String id = create();
    String profile = profileId(id);
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> concurrentSuspend(id, ready, start));
      var second = executor.submit(() -> concurrentSuspend(id, ready, start));
      try {
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      } finally {
        start.countDown();
      }
      assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertVersions(id, 1, 0);
    assertThat(lifecycle.history(id, null, 20).content()).hasSize(2);
    assertThat(auditCount(id, profile)).isEqualTo(2);
    assertThat(storedState(id)).containsEntry("status", "SUSPENDED");
  }

  private boolean concurrentSuspend(String id, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    context.getBean(StoreContext.class).setStoreId(storeId);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(ORDINARY, null, List.of()));
    try {
      ready.countDown();
      if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("同時操作が開始されませんでした");
      try {
        lifecycle.suspend(id, ORDINARY);
        return true;
      } catch (ServiceException conflict) {
        assertThat(conflict).hasMessage("在籍中のキャストのみ停止できます");
        return false;
      }
    } finally {
      SecurityContextHolder.clearContext();
      context.getBean(StoreContext.class).clear();
    }
  }

  private Map<String, Object> storedState(String id) {
    return jdbc.queryForMap(
        "select e.status,e.version as enrollment_version,e.custom_fields::text as internal_values,e.ended_at,p.name,p.version as profile_version,p.publication_status,p.custom_fields::text as public_values from t_cast_enrollments e join t_cast_profiles p on p.enrollment_id=e.id where e.id=?",
        id);
  }

  private List<Long> tableCounts() {
    return List.of(
            "t_cast_enrollments",
            "t_cast_profiles",
            "t_cast_enrollment_status_histories",
            "t_cast_enrollment_snapshots",
            "t_audit_events")
        .stream()
        .map(table -> jdbc.queryForObject("select count(*) from " + table, Long.class))
        .toList();
  }

  private void rejectAudit(String action) {
    jdbc.execute(
        "alter table t_audit_events add constraint ck_cast_audit_test check(action <> '"
            + action
            + "') not valid");
  }

  private void allowAudit() {
    jdbc.execute("alter table t_audit_events drop constraint ck_cast_audit_test");
  }

  private String profileId(String id) {
    return jdbc.queryForObject(
        "select id from t_cast_profiles where enrollment_id=?", String.class, id);
  }

  private long auditCount(String id, String profile) {
    return jdbc.queryForObject(
        "select count(*) from t_audit_events where target_id in (?,?)", Long.class, id, profile);
  }

  private void assertVersions(String id, long enrollment, long profile) {
    assertThat(
            jdbc.queryForMap(
                "select e.version as enrollment,p.version as profile from t_cast_enrollments e join t_cast_profiles p on p.enrollment_id=e.id where e.id=?",
                id))
        .containsEntry("enrollment", enrollment)
        .containsEntry("profile", profile);
  }

  @Configuration
  @EnableTransactionManagement
  @EnableAspectJAutoProxy
  @EnableJpaRepositories(
      basePackageClasses = {
        AuditEventRepository.class,
        CastEnrollmentRepository.class,
        StoreRepository.class
      })
  @Import({
    AuditWriter.class,
    BusinessAudit.class,
    StoreScopeStampListener.class,
    StoreFilterEnable.class,
    CastService.class,
    CastEnrollmentService.class,
    CastInvitationService.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
          System.getenv("KIZUNA_CAST_AUDIT_TEST_JDBC_URL"), "postgres", "");
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
    StoreContext storeContext() {
      return new StoreContext();
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
    PlatformUserRepository users(EntityManager em) {
      var factory = new JpaRepositoryFactory(em);
      factory.addRepositoryProxyPostProcessor(
          (proxy, information) ->
              proxy.addAdvice(
                  new PersistenceExceptionTranslationInterceptor(new HibernateJpaDialect())));
      return factory.getRepository(PlatformUserRepository.class);
    }
  }
}
