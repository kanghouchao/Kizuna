package com.kizuna.cast.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.cast.api.dto.CastCreateRequest;
import com.kizuna.cast.api.dto.CastFieldDefinitionCreateRequest;
import com.kizuna.cast.api.dto.CastFieldDefinitionMapper;
import com.kizuna.cast.api.dto.CastFieldDefinitionUpdateRequest;
import com.kizuna.cast.api.dto.CastMapper;
import com.kizuna.cast.api.dto.CastUpdateRequest;
import com.kizuna.cast.domain.AttendanceReferenceCheck;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.OrderReferenceCheck;
import com.kizuna.shared.exception.NotFoundException;
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

/** 実際の店舗行ロックと監査sinkを使い、項目削除の全副作用が同時に確定することを確認する。 */
@EnabledIfEnvironmentVariable(named = "KIZUNA_CAST_FIELD_AUDIT_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CastFieldAuditPostgresTest {
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private CastFieldDefinitionService fields;
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
    fields = context.getBean(CastFieldDefinitionService.class);
    casts = context.getBean(CastService.class);
    lifecycle = context.getBean(CastEnrollmentService.class);
    transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
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
        "alter table t_cast_field_definitions add constraint uq_test_definition unique(store_id,key)");
    jdbc.execute(
        "alter table t_cast_profiles add constraint uq_test_profile unique(enrollment_id)");
    jdbc.execute(
        "alter table t_cast_field_definitions add constraint fk_test_definition_store foreign key(store_id) references t_stores(id)");
    jdbc.execute(
        "alter table t_cast_enrollments add constraint fk_test_enrollment_store foreign key(store_id) references t_stores(id)");
    jdbc.execute(
        "alter table t_audit_events add constraint fk_test_audit_actor foreign key(actor_id) references t_users(id)");
    jdbc.execute(
        "alter table t_cast_enrollment_snapshots add constraint fk_test_snapshot_actor foreign key(actor_id) references t_users(id)");
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

  private Long store() {
    return transactions.execute(
        status ->
            context
                .getBean(StoreRepository.class)
                .saveAndFlush(new Store("検証店", UUID.randomUUID() + ".example.test", null))
                .getId());
  }

  @BeforeEach
  void authenticate() {
    storeId = store();
    authenticate(storeId, ORDINARY);
  }

  private void authenticate(Long store, String email) {
    context.getBean(StoreContext.class).setStoreId(store);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of()));
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

  private String definition(String key, boolean isPublic) {
    var request = new CastFieldDefinitionCreateRequest();
    request.setKey(key);
    request.setLabel("秘密のラベル");
    request.setIsPublic(isPublic);
    return fields.create(request).getId();
  }

  private String cast(Map<String, String> values) {
    var request = new CastCreateRequest();
    request.setName("秘密の名前-" + UUID.randomUUID());
    String id = casts.create(request, ORDINARY).getId();
    if (!values.isEmpty()) write(id, values);
    return id;
  }

  private void write(String id, Map<String, String> values) {
    var request = new CastUpdateRequest();
    request.setCustomFields(values);
    casts.update(id, request, ORDINARY);
  }

  private long count(String table) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where store_id=?", Long.class, storeId);
  }

  private List<Long> counts() {
    return List.of(
            "t_cast_field_definitions",
            "t_cast_enrollments",
            "t_cast_profiles",
            "t_cast_enrollment_snapshots",
            "t_audit_events")
        .stream()
        .map(this::count)
        .toList();
  }

  private Map<String, Object> stored(String id) {
    return jdbc.queryForMap(
        "select e.version as internal_version,e.custom_fields::text as internal_values,p.version as public_version,p.custom_fields::text as public_values from t_cast_enrollments e join t_cast_profiles p on p.enrollment_id=e.id where e.id=?",
        id);
  }

  @Test
  void definitionVersionsAndAffectedReferencesMatchCommittedRows() {
    String internal = definition("private-key", false);
    String external = definition("public-key", true);
    String first = cast(Map.of("private-key", "秘密の内部値", "public-key", "秘密の公開値"));
    String second = cast(Map.of("private-key", "二人目の秘密"));
    String untouched = cast(Map.of());
    var edit = new CastFieldDefinitionUpdateRequest();
    edit.setLabel("変更した秘密");
    fields.update(internal, edit);
    fields.update(internal, edit);
    fields.update(internal, new CastFieldDefinitionUpdateRequest());
    assertThat(
            jdbc.queryForObject(
                "select version from t_cast_field_definitions where id=?", Long.class, internal))
        .isEqualTo(1);
    fields.delete(internal, ORDINARY);
    assertThat(fields.list()).extracting(d -> d.getId()).containsExactly(external);
    assertThat(stored(first))
        .containsEntry("internal_values", "{}")
        .containsEntry("internal_version", 2L)
        .containsEntry("public_version", 1L);
    assertThat(stored(first).get("public_values").toString()).contains("秘密の公開値");
    assertThat(stored(second))
        .containsEntry("internal_values", "{}")
        .containsEntry("internal_version", 2L);
    assertThat(stored(untouched))
        .containsEntry("internal_version", 0L)
        .containsEntry("public_version", 0L);
    var definitionEvents =
        jdbc.queryForList(
            "select action,before_values ->> 'version' as old,after_values ->> 'version' as new,after_values ->> 'exists' as present,after_values ->> 'redacted_fields_changed' as changed from t_audit_events where target_id=? order by id",
            internal);
    assertThat(definitionEvents).hasSize(3);
    assertThat(definitionEvents.get(0)).containsEntry("new", "0");
    assertThat(definitionEvents.get(1))
        .containsEntry("old", "0")
        .containsEntry("new", "1")
        .containsEntry("changed", "label");
    assertThat(definitionEvents.get(2))
        .containsEntry("old", "1")
        .containsEntry("new", null)
        .containsEntry("present", "false");
    var removed =
        jdbc.queryForList(
            "select a.target_id,a.source_type,a.source_id,a.actor_id,a.before_values ->> 'version' as old,a.after_values ->> 'version' as new,s.enrollment_id,s.actor_id as snapshot_actor,s.custom_fields::text as old_values from t_audit_events a join t_cast_enrollment_snapshots s on s.id=a.after_values ->> 'snapshot_id' where a.source_id=? order by a.target_id",
            internal);
    assertThat(removed)
        .hasSize(2)
        .allSatisfy(
            row ->
                assertThat(row)
                    .containsEntry("source_type", "CAST_FIELD_DEFINITION")
                    .containsEntry("source_id", internal)
                    .containsEntry("old", "1")
                    .containsEntry("new", "2")
                    .containsEntry("actor_id", ordinaryId)
                    .containsEntry("snapshot_actor", ordinaryId));
    assertThat(removed)
        .allSatisfy(
            row -> {
              assertThat(row.get("target_id")).isEqualTo(row.get("enrollment_id"));
              assertThat(row.get("old_values").toString()).contains("private-key");
            });
    fields.delete(external, ORDINARY);
    assertThat(stored(first))
        .containsEntry("public_values", "{}")
        .containsEntry("public_version", 2L);
    var publicEvent =
        jdbc.queryForMap(
            "select a.target_id,p.enrollment_id,a.before_values ->> 'version' as old,a.after_values ->> 'version' as new from t_audit_events a join t_cast_profiles p on p.id=a.target_id where a.source_id=?",
            external);
    assertThat(publicEvent)
        .containsEntry("enrollment_id", first)
        .containsEntry("old", "1")
        .containsEntry("new", "2");
    assertNoSecrets();
  }

  private void assertNoSecrets() {
    assertThat(
            jdbc.queryForList(
                    "select before_values,after_values from t_audit_events where store_id=?",
                    storeId)
                .toString())
        .doesNotContain("秘密", "private-key", "public-key", ORDINARY, ELEVATED);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "CAST_FIELD_DEFINITION_CREATED",
        "CAST_FIELD_DEFINITION_UPDATED",
        "CAST_FIELD_DEFINITION_DELETED",
        "CAST_INTERNAL_FIELD_REMOVED",
        "CAST_PROFILE_FIELD_REMOVED"
      })
  void auditFailureRestoresDefinitionValuesSnapshotsAndAllEvents(String rejected) {
    String internal = definition("private-key", false);
    String external = definition("public-key", true);
    String id = cast(Map.of("private-key", "秘密の内部値", "public-key", "秘密の公開値"));
    var before = stored(id);
    var beforeCounts = counts();
    var beforeDefinitions =
        jdbc.queryForList(
            "select * from t_cast_field_definitions where store_id=? order by id", storeId);
    jdbc.execute(
        "alter table t_audit_events add constraint ck_field_audit_test check(action <> '"
            + rejected
            + "') not valid");
    try {
      assertThatThrownBy(
              () -> {
                if (rejected.endsWith("CREATED")) definition("new-key", false);
                else if (rejected.endsWith("UPDATED")) {
                  var request = new CastFieldDefinitionUpdateRequest();
                  request.setLabel("rollbackする秘密");
                  request.setDisplayOrder(10);
                  fields.update(internal, request);
                } else
                  fields.delete(
                      rejected.equals("CAST_PROFILE_FIELD_REMOVED") ? external : internal,
                      ORDINARY);
              })
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(stored(id)).isEqualTo(before);
      assertThat(counts()).isEqualTo(beforeCounts);
      assertThat(
              jdbc.queryForList(
                  "select * from t_cast_field_definitions where store_id=? order by id", storeId))
          .isEqualTo(beforeDefinitions);
    } finally {
      jdbc.execute("alter table t_audit_events drop constraint ck_field_audit_test");
    }
  }

  @Test
  void outerRollbackRestoresTheWholeDefinitionChangeSequence() {
    String internal = definition("private-key", false);
    String external = definition("public-key", true);
    String id = cast(Map.of("private-key", "秘密の内部値", "public-key", "秘密の公開値"));
    var before = stored(id);
    var beforeCounts = counts();
    var beforeDefinitions =
        jdbc.queryForList(
            "select * from t_cast_field_definitions where store_id=? order by id", storeId);
    transactions.executeWithoutResult(
        status -> {
          var request = new CastFieldDefinitionUpdateRequest();
          request.setLabel("rollbackする秘密");
          fields.update(internal, request);
          fields.delete(internal, ORDINARY);
          fields.delete(external, ORDINARY);
          definition("private-key", true);
          status.setRollbackOnly();
        });
    assertThat(stored(id)).isEqualTo(before);
    assertThat(counts()).isEqualTo(beforeCounts);
    assertThat(
            jdbc.queryForList(
                "select * from t_cast_field_definitions where store_id=? order by id", storeId))
        .isEqualTo(beforeDefinitions);
  }

  @Test
  void elevatedActorAndSnapshotAreConsistentAcrossCreationUpdateAndDeletion() {
    var jwt =
        Jwt.withTokenValue(UUID.randomUUID().toString())
            .header("alg", "HS256")
            .subject(ELEVATED)
            .claim("elevationId", 77L)
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    String definition = definition("private-key", false);
    var request = new CastCreateRequest();
    request.setName("秘密の名前");
    String id = casts.create(request, ELEVATED).getId();
    var write = new CastUpdateRequest();
    write.setCustomFields(Map.of("private-key", "秘密の値"));
    casts.update(id, write, ELEVATED);
    var edit = new CastFieldDefinitionUpdateRequest();
    edit.setDisplayOrder(4);
    fields.update(definition, edit);
    fields.delete(definition, ELEVATED);
    var rows =
        jdbc.queryForList(
            "select actor_id,actor_type,store_id,after_values ->> 'emergency_elevation_id' as elevation from t_audit_events where target_id=? or source_id=? order by id",
            definition,
            definition);
    assertThat(rows)
        .hasSize(4)
        .allSatisfy(
            row ->
                assertThat(row)
                    .containsEntry("actor_id", elevatedId)
                    .containsEntry("actor_type", "STAFF")
                    .containsEntry("store_id", storeId)
                    .containsEntry("elevation", "77"));
    assertThat(lifecycle.snapshots(id, null, 20).content())
        .hasSize(2)
        .allSatisfy(snapshot -> assertThat(snapshot.actorId()).isEqualTo(elevatedId));
    assertNoSecrets();
  }

  @Test
  void storeIsolationProtectsSameKeyAndRefusesForeignDefinition() {
    String ownDefinition = definition("private-key", false);
    String ownCast = cast(Map.of("private-key", "秘密の自店値"));
    Long otherStore = store();
    authenticate(otherStore, ORDINARY);
    String foreignDefinition = definition("private-key", false);
    String foreignCast = cast(Map.of("private-key", "秘密の他店値"));
    var foreignBefore = stored(foreignCast);
    long foreignAudits =
        jdbc.queryForObject(
            "select count(*) from t_audit_events where store_id=?", Long.class, otherStore);
    authenticate(storeId, ORDINARY);
    assertThatThrownBy(
            () -> fields.update(foreignDefinition, new CastFieldDefinitionUpdateRequest()))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> fields.delete(foreignDefinition, ORDINARY))
        .isInstanceOf(NotFoundException.class);
    fields.delete(ownDefinition, ORDINARY);
    assertThat(stored(ownCast)).containsEntry("internal_values", "{}");
    assertThat(stored(foreignCast)).isEqualTo(foreignBefore);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_audit_events where store_id=?", Long.class, otherStore))
        .isEqualTo(foreignAudits);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_cast_field_definitions where id=?",
                Long.class,
                foreignDefinition))
        .isEqualTo(1);
  }

  @Test
  void rejectedDuplicateCapacityAndVisibilityChangesLeaveNoAudit() {
    String id = definition("private-key", false);
    long count = count("t_audit_events");
    assertThatThrownBy(() -> definition("private-key", false))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("既に登録");
    var request = new CastFieldDefinitionUpdateRequest();
    request.setIsPublic(true);
    assertThatThrownBy(() -> fields.update(id, request))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("公開区分");
    assertThat(count("t_audit_events")).isEqualTo(count);
    for (int i = 1; i < 20; i++) definition("key-" + i, false);
    assertThatThrownBy(() -> definition("excess", false))
        .isInstanceOf(ServiceException.class)
        .hasMessageContaining("最大20件");
    assertThat(count("t_cast_field_definitions")).isEqualTo(20);
    assertThat(count("t_audit_events")).isEqualTo(20);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void concurrentValueWriteAndDeletionSerializeBeforePublicRecreation(boolean writerFirst)
      throws Exception {
    String definition = definition("private-key", false);
    String id = cast(Map.of("private-key", "秘密の既存値"));
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var attempting = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var leader =
          executor.submit(
              () -> {
                authenticate(storeId, ORDINARY);
                try {
                  transactions.executeWithoutResult(
                      status -> {
                        if (writerFirst) write(id, Map.of("private-key", "秘密の並行更新値"));
                        else fields.delete(definition, ORDINARY);
                        locked.countDown();
                        await(release);
                      });
                } finally {
                  clear();
                }
              });
      try {
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        var follower =
            executor.submit(
                () -> {
                  authenticate(storeId, ORDINARY);
                  try {
                    attempting.countDown();
                    if (writerFirst) fields.delete(definition, ORDINARY);
                    else
                      assertThatThrownBy(() -> write(id, Map.of("private-key", "秘密の遅延入力")))
                          .isInstanceOf(ServiceException.class)
                          .hasMessageContaining("未知のカスタムフィールドキー");
                  } finally {
                    clear();
                  }
                });
        assertThat(attempting.await(10, TimeUnit.SECONDS)).isTrue();
        assertDatabaseLockWait();
        release.countDown();
        leader.get(15, TimeUnit.SECONDS);
        follower.get(15, TimeUnit.SECONDS);
      } finally {
        release.countDown();
      }
    }
    assertThat(stored(id))
        .containsEntry("internal_values", "{}")
        .containsEntry("public_values", "{}");
    String rebuilt = definition("private-key", true);
    assertThat(rebuilt).isNotEqualTo(definition);
    assertThat(casts.get(id).getCustomFields()).isEmpty();
    assertThat(stored(id))
        .containsEntry("internal_values", "{}")
        .containsEntry("public_values", "{}");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_audit_events where source_id=?", Long.class, definition))
        .isEqualTo(1);
    var removed =
        jdbc.queryForMap(
            "select before_values ->> 'version' as old,after_values ->> 'version' as new from t_audit_events where source_id=?",
            definition);
    assertThat(removed)
        .containsEntry("old", writerFirst ? "2" : "1")
        .containsEntry("new", writerFirst ? "3" : "2");
    assertThat(lifecycle.snapshots(id, null, 20).content()).hasSize(writerFirst ? 3 : 2);
    assertNoSecrets();
  }

  @Test
  void concurrentCreatorsCannotExceedTwentyDefinitions() throws Exception {
    for (int i = 0; i < 19; i++) definition("field-" + i, false);
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> concurrentCreate("first", ready, start));
      var second = executor.submit(() -> concurrentCreate("second", ready, start));
      try {
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      } finally {
        start.countDown();
      }
      assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(fields.list()).hasSize(20);
    assertThat(count("t_audit_events")).isEqualTo(20);
  }

  private boolean concurrentCreate(String key, CountDownLatch ready, CountDownLatch start) {
    authenticate(storeId, ORDINARY);
    try {
      ready.countDown();
      await(start);
      try {
        definition(key, false);
        return true;
      } catch (ServiceException rejected) {
        assertThat(rejected).hasMessageContaining("最大20件");
        return false;
      }
    } finally {
      clear();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("並行操作の同期が完了しませんでした");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private void assertDatabaseLockWait() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      if (jdbc.queryForObject(
              "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'",
              Long.class)
          > 0) return;
      Thread.sleep(10);
    }
    throw new AssertionError("後続操作が実DBの店舗行ロックで待機しませんでした");
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
    CastInvitationService.class,
    CastFieldDefinitionService.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
          System.getenv("KIZUNA_CAST_FIELD_AUDIT_TEST_JDBC_URL"), "postgres", "");
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
    CastFieldDefinitionMapper fieldMapper() {
      return Mappers.getMapper(CastFieldDefinitionMapper.class);
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
