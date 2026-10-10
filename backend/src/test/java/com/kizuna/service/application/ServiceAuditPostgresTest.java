package com.kizuna.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.audit.domain.AuditEvent;
import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.order.application.OrderSpecialServices;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCourse;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.domain.SpecialServiceSnapshot;
import com.kizuna.service.api.dto.OwnConsentRequest;
import com.kizuna.service.api.dto.ServiceCreateRequest;
import com.kizuna.service.api.dto.ServiceMapper;
import com.kizuna.service.api.dto.ServiceUpdateRequest;
import com.kizuna.service.domain.ChargeType;
import com.kizuna.service.domain.ConsentDecision;
import com.kizuna.service.domain.ServiceItemRepository;
import com.kizuna.service.domain.ServiceKind;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.StoreScopeStampListener;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreFilterEnable;
import com.kizuna.store.domain.Store;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
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

/** 実店舗ロックと注文側の拒否処理を含め、監査と業務の同時確定を確認する。 */
@EnabledIfEnvironmentVariable(named = "KIZUNA_SERVICE_AUDIT_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceAuditPostgresTest {
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private ServiceSettingsService settings;
  private OwnServiceConditionService own;
  private TransactionTemplate transactions;
  private Long storeId;
  private Long staffId;
  private Long castId;
  private String enrollment;
  private static final String STAFF = "operator@example.test";
  private static final String CAST = "cast@example.test";
  private static final List<String> TABLES =
      List.of(
          "t_services",
          "t_service_revisions",
          "t_service_consents",
          "t_service_consent_events",
          "t_orders",
          "t_order_special_service_events",
          "t_audit_events");

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    settings = context.getBean(ServiceSettingsService.class);
    own = context.getBean(OwnServiceConditionService.class);
    transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    staffId = actor(STAFF, UserType.STAFF);
    castId = actor(CAST, UserType.CAST);
    jdbc.execute(
        "alter table t_service_consents add constraint uq_test_consent unique(store_id,enrollment_id,service_id)");
    jdbc.execute(
        "alter table t_service_revisions add constraint uq_test_revision unique(store_id,service_id,revision_number)");
    jdbc.execute(
        "alter table t_service_consent_events add constraint uq_test_decision unique(store_id,consent_id,revision_number)");
    jdbc.execute(
        "alter table t_service_revisions add constraint fk_test_service foreign key(service_id) references t_services(id)");
    jdbc.execute(
        "alter table t_service_consents add constraint fk_test_consent_service foreign key(service_id) references t_services(id)");
    jdbc.execute(
        "alter table t_service_consents add constraint fk_test_consent_enrollment foreign key(enrollment_id) references t_cast_enrollments(id)");
    jdbc.execute(
        "alter table t_service_consents add constraint fk_test_consent_revision foreign key(service_revision_id) references t_service_revisions(id)");
    jdbc.execute(
        "alter table t_service_consent_events add constraint fk_test_event_consent foreign key(consent_id) references t_service_consents(id)");
    jdbc.execute(
        "alter table t_order_special_service_events add constraint fk_test_order_consent foreign key(consent_event_id) references t_service_consent_events(id)");
    jdbc.execute(
        "alter table t_audit_events add constraint fk_test_audit_actor foreign key(actor_id) references t_users(id)");
  }

  private Long actor(String email, UserType type) {
    return transactions.execute(
        status ->
            context
                .getBean(PlatformUserRepository.class)
                .saveAndFlush(
                    PlatformUser.builder()
                        .email(email)
                        .password(UUID.randomUUID().toString())
                        .displayName("検証担当")
                        .userType(type)
                        .enabled(true)
                        .roleIds(type == UserType.STAFF ? Set.of(1L) : Set.of())
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
  void setup() {
    storeId = store();
    authenticate(storeId, STAFF);
    enrollment =
        transactions.execute(
            status -> {
              var repo = context.getBean(CastRepository.class);
              var person =
                  repo.findByPlatformUserId(castId)
                      .orElseGet(
                          () -> repo.saveAndFlush(Cast.builder().platformUserId(castId).build()));
              return context
                  .getBean(CastEnrollmentRepository.class)
                  .saveAndFlush(CastEnrollment.builder().castId(person.getId()).build())
                  .getId();
            });
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
    context.close();
  }

  private String special() {
    return settings.create(
        new ServiceCreateRequest(
            ServiceKind.SPECIAL_SERVICE, "複写しない秘密", null, ChargeType.PAID, 2000, 1000),
        STAFF);
  }

  private ServiceUpdateRequest changed(long revision) {
    return new ServiceUpdateRequest("変えた秘密", null, ChargeType.PAID, 3000, 1500, revision);
  }

  private void decide(String id, long terms, long revision, ConsentDecision decision) {
    own.decide(CAST, id, new OwnConsentRequest(terms, revision, decision));
  }

  private List<AuditEvent> audits() {
    return transactions.execute(
        status ->
            context.getBean(AuditEventRepository.class).findAll().stream()
                .filter(e -> storeId.equals(e.getStoreId()))
                .sorted(Comparator.comparing(AuditEvent::getId))
                .toList());
  }

  private List<List<Map<String, Object>>> stored() {
    return TABLES.stream()
        .map(
            table ->
                jdbc.queryForList(
                    "select * from " + table + " where store_id=? order by id", storeId))
        .toList();
  }

  private String order(String serviceId) {
    return transactions.execute(
        status -> {
          var terms =
              context.getBean(OrderSpecialServiceCatalog.class).current(enrollment, serviceId);
          var order =
              Order.builder()
                  .businessDate(LocalDate.now())
                  .status(OrderStatus.CONFIRMED)
                  .castId(enrollment)
                  .build();
          order.adoptServices(
              new OrderCourse(
                  "course",
                  "course-revision",
                  1,
                  "コース",
                  60,
                  12000,
                  6000,
                  "CURRENT_SETTING",
                  OffsetDateTime.now()),
              List.of(
                  new SpecialServiceSnapshot(
                      serviceId,
                      terms.revisionId(),
                      terms.revisionNumber(),
                      terms.termsVersion(),
                      terms.name(),
                      terms.chargeType(),
                      terms.price(),
                      terms.remuneration(),
                      "ACCEPTED_TERMS",
                      OffsetDateTime.now(),
                      enrollment,
                      terms.consentEventId(),
                      terms.consentVersion())),
              List.of());
          return context.getBean(OrderRepository.class).saveAndFlush(order).getId();
        });
  }

  @Test
  void committedVersionsAndExistingHistoryReferencesMatchEveryChange() {
    String id = special();
    var created = audits().getFirst();
    assertThat(created.getAfterValues())
        .containsOnlyKeys("exists", "deleted", "version", "revision_number", "terms_version")
        .containsEntry("version", "0")
        .containsEntry("revision_number", "1");
    settings.update(
        id, new ServiceUpdateRequest("複写しない秘密", null, ChargeType.PAID, 2000, 1000, 1L), STAFF);
    assertThat(audits()).hasSize(1);
    settings.update(
        id, new ServiceUpdateRequest("名称だけの秘密", null, ChargeType.PAID, 2000, 1000, 1L), STAFF);
    assertThat(audits().getLast().getAfterValues())
        .containsEntry("version", "1")
        .containsEntry("terms_version", "1")
        .containsEntry("redacted_fields_changed", "name");
    authenticate(storeId, CAST);
    decide(id, 1, 0, ConsentDecision.ACCEPTED);
    var initial = audits().getLast();
    assertThat(initial.getActorId()).isEqualTo(castId);
    assertThat(initial.getBeforeValues()).isEmpty();
    assertThat(initial.getAfterValues())
        .containsOnlyKeys(
            "exists",
            "version",
            "revision_number",
            "terms_version",
            "decision",
            "service_id",
            "enrollment_id",
            "service_revision_id")
        .containsEntry("decision", "ACCEPTED")
        .containsEntry("version", "0");
    decide(id, 1, 1, ConsentDecision.ACCEPTED);
    assertThat(audits()).hasSize(3);
    settings.update(id, changed(2), STAFF);
    decide(id, 2, 1, ConsentDecision.ACCEPTED);
    assertThat(audits().getLast().getAfterValues())
        .containsEntry("version", "1")
        .containsEntry("terms_version", "2")
        .containsEntry("revision_number", "2");
    String orderId = order(id);
    decide(id, 2, 2, ConsentDecision.REJECTED);
    var rejected = audits().getLast();
    assertThat(rejected.getBeforeValues()).containsEntry("decision", "ACCEPTED");
    assertThat(rejected.getAfterValues())
        .containsEntry("decision", "REJECTED")
        .containsEntry("version", "2");
    assertThat(
            jdbc.queryForObject(
                "select consent_event_id from t_order_special_service_events where order_id=?",
                String.class,
                orderId))
        .isEqualTo(rejected.getSourceId());
    assertThat(
            jdbc.queryForObject(
                "select total_fee from t_orders where id=?", Integer.class, orderId))
        .isEqualTo(15000);
    settings.delete(id, 3, STAFF);
    assertThat(audits()).hasSize(7);
    var deleted = audits().getLast();
    assertThat(deleted.getAfterValues())
        .containsEntry("exists", "true")
        .containsEntry("deleted", "true")
        .containsEntry("version", "3");
    assertThat(jdbc.queryForObject("select version from t_services where id=?", Long.class, id))
        .isEqualTo(3L);
    for (var event : audits()) {
      assertThat(event.getBeforeValues().toString() + event.getAfterValues())
          .doesNotContain("秘密", "2000", "3000", "1500", "hash");
      String table =
          event.getSourceType().equals("SERVICE_REVISION")
              ? "t_service_revisions"
              : "t_service_consent_events";
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from " + table + " where id=?", Long.class, event.getSourceId()))
          .isEqualTo(1L);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "SERVICE_CREATED",
        "SERVICE_UPDATED",
        "SERVICE_DELETED",
        "SERVICE_CONSENT_CHANGED"
      })
  void auditFailureRollsBackBusinessHistoryAndOrderRejection(String action) {
    String id = special();
    decide(id, 1, 0, ConsentDecision.ACCEPTED);
    order(id);
    var before = stored();
    jdbc.execute(
        "alter table t_audit_events add constraint ck_service_audit_test check(action <> '"
            + action
            + "') not valid");
    try {
      assertThatThrownBy(
              () -> {
                switch (action) {
                  case "SERVICE_CREATED" -> special();
                  case "SERVICE_UPDATED" -> settings.update(id, changed(1), STAFF);
                  case "SERVICE_DELETED" -> settings.delete(id, 1, STAFF);
                  case "SERVICE_CONSENT_CHANGED" -> decide(id, 1, 1, ConsentDecision.REJECTED);
                  default -> throw new AssertionError(action);
                }
              })
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(stored()).isEqualTo(before);
    } finally {
      jdbc.execute("alter table t_audit_events drop constraint ck_service_audit_test");
    }
  }

  @Test
  void outerRollbackRestoresAllOperationsAndOrderSideEffects() {
    String id = special();
    decide(id, 1, 0, ConsentDecision.ACCEPTED);
    order(id);
    var before = stored();
    transactions.executeWithoutResult(
        status -> {
          special();
          settings.update(id, changed(1), STAFF);
          decide(id, 2, 1, ConsentDecision.REJECTED);
          settings.delete(id, 2, STAFF);
          assertThat(audits()).hasSize(6);
          status.setRollbackOnly();
        });
    assertThat(stored()).isEqualTo(before);
  }

  @Test
  void downstreamFailureCannotLeaveSuccessfulConsentOrAudit() {
    String id = special();
    decide(id, 1, 0, ConsentDecision.ACCEPTED);
    order(id);
    var before = stored();
    jdbc.execute(
        "alter table t_order_special_service_events add constraint ck_service_order_test check(kind <> 'REJECTED') not valid");
    try {
      assertThatThrownBy(() -> decide(id, 1, 1, ConsentDecision.REJECTED))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(stored()).isEqualTo(before);
    } finally {
      jdbc.execute(
          "alter table t_order_special_service_events drop constraint ck_service_order_test");
    }
  }

  @Test
  void elevationUsesActualActorWithoutChangingOwnChoiceAuthority() {
    var jwt =
        Jwt.withTokenValue(UUID.randomUUID().toString())
            .header("alg", "HS256")
            .subject(STAFF)
            .claim("elevationId", 77L)
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of(), STAFF));
    String id = special();
    settings.update(id, changed(1), STAFF);
    settings.delete(id, 2, STAFF);
    assertThat(audits())
        .hasSize(3)
        .allSatisfy(
            event -> {
              assertThat(event.getActorId()).isEqualTo(staffId);
              assertThat(event.getAfterValues()).containsEntry("emergency_elevation_id", "77");
            });
    authenticate(storeId, CAST);
    String ownId = special();
    decide(ownId, 1, 0, ConsentDecision.ACCEPTED);
    assertThat(audits().getLast().getActorId()).isEqualTo(castId);
    assertThat(audits().getLast().getAfterValues()).doesNotContainKey("emergency_elevation_id");
  }

  @Test
  void foreignRowsMissingEnrollmentInvalidTermsAndStaleVersionsLeaveNoSuccess() {
    String id = special();
    var before = stored();
    assertThatThrownBy(() -> settings.update(id, changed(2), STAFF))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> settings.delete(id, 2, STAFF)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> decide(id, 2, 0, ConsentDecision.ACCEPTED))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> decide(id, 1, 2, ConsentDecision.ACCEPTED))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(
            () -> own.decide(STAFF, id, new OwnConsentRequest(1L, 0L, ConsentDecision.ACCEPTED)))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                settings.update(
                    id, new ServiceUpdateRequest("秘密", null, ChargeType.FREE, 1, 0, 1L), STAFF))
        .isInstanceOf(ServiceException.class);
    Long foreign = store();
    authenticate(foreign, STAFF);
    assertThatThrownBy(() -> settings.update(id, changed(1), STAFF))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> settings.delete(id, 1, STAFF)).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> decide(id, 1, 0, ConsentDecision.ACCEPTED))
        .isInstanceOf(NotFoundException.class);
    authenticate(storeId, STAFF);
    assertThat(stored()).isEqualTo(before);
    decide(id, 1, 0, ConsentDecision.REJECTED);
    var rejected = stored();
    decide(id, 1, 1, ConsentDecision.REJECTED);
    assertThat(stored()).isEqualTo(rejected);
    transactions.executeWithoutResult(
        status ->
            context
                .getBean(CastEnrollmentRepository.class)
                .findById(enrollment)
                .orElseThrow()
                .withdraw(OffsetDateTime.now()));
    assertThatThrownBy(() -> decide(id, 1, 1, ConsentDecision.ACCEPTED))
        .isInstanceOf(NotFoundException.class);
    settings.delete(id, 1, STAFF);
    var deleted = stored();
    assertThatThrownBy(() -> settings.delete(id, 2, STAFF)).isInstanceOf(ServiceException.class);
    assertThat(stored()).isEqualTo(deleted);
  }

  @ParameterizedTest
  @ValueSource(strings = {"update", "delete", "decision"})
  void concurrentStaleWriterWaitsForDatabaseLockAndCannotAddSuccess(String operation)
      throws Exception {
    String id = special();
    var ready = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    Long capturedStore = storeId;
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          pool.submit(
              () -> {
                authenticate(capturedStore, STAFF);
                try {
                  transactions.executeWithoutResult(
                      status -> {
                        concurrentOperation(operation, id);
                        ready.countDown();
                        await(release);
                      });
                } finally {
                  clear();
                }
              });
      try {
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        var second =
            pool.submit(
                () -> {
                  authenticate(capturedStore, STAFF);
                  try {
                    assertThatThrownBy(() -> concurrentOperation(operation, id))
                        .isInstanceOf(
                            operation.equals("delete")
                                ? ServiceException.class
                                : ConflictException.class);
                  } finally {
                    clear();
                  }
                });
        assertDatabaseLockWait();
        release.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
      } finally {
        release.countDown();
      }
    }
    assertThat(audits()).hasSize(2);
    assertThat(audits().getLast().getAfterValues())
        .containsEntry("revision_number", operation.equals("decision") ? "1" : "2");
  }

  private void concurrentOperation(String operation, String id) {
    switch (operation) {
      case "update" -> settings.update(id, changed(1), STAFF);
      case "delete" -> settings.delete(id, 1, STAFF);
      case "decision" -> decide(id, 1, 0, ConsentDecision.ACCEPTED);
      default -> throw new AssertionError(operation);
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("先行取引の解放が間に合いません");
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
        ServiceItemRepository.class,
        OrderRepository.class,
        StoreRepository.class
      })
  @Import({
    AuditWriter.class,
    BusinessAudit.class,
    StoreScopeStampListener.class,
    StoreFilterEnable.class,
    ServiceSettingsService.class,
    OwnServiceConditionService.class,
    ActorIdentityService.class,
    OrderSpecialServices.class,
    OrderSpecialServiceCatalog.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
          System.getenv("KIZUNA_SERVICE_AUDIT_TEST_JDBC_URL"), "postgres", "");
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource source, ConfigurableListableBeanFactory beans) {
      var f = new LocalContainerEntityManagerFactoryBean();
      f.setDataSource(source);
      f.setPackagesToScan("com.kizuna");
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
    ServiceMapper mapper() {
      return Mappers.getMapper(ServiceMapper.class);
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
