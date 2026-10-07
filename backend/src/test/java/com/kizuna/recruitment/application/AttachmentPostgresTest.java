package com.kizuna.recruitment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.recruitment.domain.Applicant;
import com.kizuna.recruitment.domain.ApplicantIntake;
import com.kizuna.recruitment.domain.ApplicantRepository;
import com.kizuna.recruitment.domain.ApplicantSourceType;
import com.kizuna.recruitment.domain.AttachmentUpload;
import com.kizuna.recruitment.domain.ReceptionChannel;
import com.kizuna.recruitment.infrastructure.AttachmentStorageMissingException;
import com.kizuna.recruitment.infrastructure.NormalizedImage;
import com.kizuna.recruitment.infrastructure.PrivateAttachmentFile;
import com.kizuna.recruitment.infrastructure.PrivateAttachmentStorage;
import com.kizuna.recruitment.infrastructure.RasterImageNormalizer;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ResourceBusyException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.ServiceUnavailableException;
import com.kizuna.shared.persistence.StoreScopeStampListener;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreFilterEnable;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "KIZUNA_ATTACHMENT_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttachmentPostgresTest {
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private TransactionTemplate transactions;
  private EntityManager em;
  private AttachmentTransactions service;
  private StoreContext scope;
  private PrivateAttachmentStorage storage;
  @TempDir Path directory;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    em = context.getBean(EntityManager.class);
    service = context.getBean(AttachmentTransactions.class);
    scope = context.getBean(StoreContext.class);
    storage = context.getBean(PrivateAttachmentStorage.class);
    jdbc.execute("create table attachment_test_stores(id bigint primary key)");
    jdbc.execute("insert into attachment_test_stores values(1),(2)");
    jdbc.execute(
        "alter table t_applicants add constraint uq_attachment_test_applicant_store unique(id, store_id)");
    jdbc.execute(
        "alter table t_applicant_attachment_uploads add constraint fk_attachment_test_applicant foreign key(applicant_id,store_id) references t_applicants(id,store_id)");
    jdbc.execute(
        "alter table t_applicant_attachment_uploads add constraint fk_attachment_test_store foreign key(store_id) references attachment_test_stores(id)");
    jdbc.execute(
        "alter table t_applicant_attachment_uploads add constraint uq_attachment_test_key unique(store_id,applicant_id,idempotency_key)");
  }

  @AfterAll
  void close() {
    if (context != null) {
      jdbc.execute("drop table attachment_test_stores cascade");
      context.close();
    }
  }

  @BeforeEach
  void actor() {
    reset(storage);
    authorize(1L);
  }

  private void authorize(Long storeId) {
    scope.setStoreId(storeId);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("operator@example.test", "unused", List.of()));
  }

  private String applicant() {
    return transactions.execute(
        tx -> {
          var applicant =
              Applicant.receive(
                  new ApplicantIntake(
                      "合成応募者",
                      ReceptionChannel.WEB,
                      ApplicantSourceType.DIRECT,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null));
          applicant.setStoreId(1L);
          applicant.recordEditor(9L);
          em.persist(applicant);
          em.flush();
          return applicant.getId();
        });
  }

  private NormalizedImage content() throws Exception {
    var file = Files.createTempFile(directory, "image", ".png");
    Files.write(file, new byte[] {1, 2, 3});
    var privateFile = mock(PrivateAttachmentFile.class);
    when(privateFile.path()).thenReturn(file);
    return new NormalizedImage(
        privateFile, "a".repeat(64), "b".repeat(64), "image/png", 3, RasterImageNormalizer.VERSION);
  }

  private AttachmentTransactions.Completion complete(
      String applicant, AttachmentUpload upload, NormalizedImage image) {
    return service.complete(
        applicant,
        upload.getId(),
        upload.getIdempotencyKey(),
        image.originalSha256(),
        image.mediaType(),
        image,
        "operator@example.test");
  }

  private String status(String uploadId) {
    return jdbc.queryForObject(
        "select status from t_applicant_attachment_uploads where id=?", String.class, uploadId);
  }

  private long auditCount(String id) {
    return jdbc.queryForObject(
        "select count(*) from t_audit_events where target_id=?", Long.class, id);
  }

  @Test
  void normalizerFailuresPersistTheirOriginAndExistingObjectRecoveryClearsIt() throws Exception {
    for (String failure : List.of("version", "busy", "timeout", "reproduction")) {
      var applicant = applicant();
      var normalizer = mock(RasterImageNormalizer.class);
      var orchestration = new AttachmentService(service, storage, normalizer, new AppProperties());
      try (var source = content()) {
        var reservedContent =
            new NormalizedImage(
                source.file(),
                source.originalSha256(),
                source.canonicalSha256(),
                source.mediaType(),
                source.sizeBytes(),
                failure.equals("version") ? "historical-version" : source.normalizerVersion());
        var upload =
            service.reserve(applicant, UUID.randomUUID(), reservedContent, "operator@example.test");
        doThrow(new AttachmentStorageMissingException()).when(storage).readVerified(any());
        if (failure.equals("busy")) {
          when(normalizer.normalize(source.path(), source.mediaType()))
              .thenThrow(new ResourceBusyException("画像処理が混み合っています"));
        } else if (failure.equals("timeout")) {
          when(normalizer.normalize(source.path(), source.mediaType()))
              .thenThrow(new ServiceUnavailableException("画像処理が制限時間を超えました"));
        } else if (failure.equals("reproduction")) {
          when(normalizer.normalize(source.path(), source.mediaType()))
              .thenReturn(
                  new NormalizedImage(
                      source.file(),
                      source.originalSha256(),
                      "c".repeat(64),
                      source.mediaType(),
                      source.sizeBytes(),
                      source.normalizerVersion()));
        }
        assertThatThrownBy(
                () ->
                    orchestration.upload(
                        applicant,
                        upload.getId(),
                        upload.getIdempotencyKey(),
                        source.path(),
                        source.originalSha256(),
                        source.mediaType(),
                        "operator@example.test"))
            .isInstanceOf(RuntimeException.class);
        assertThat(status(upload.getId())).isEqualTo("RECOVERY_REQUIRED");
        assertThat(
                jdbc.queryForObject(
                    "select failure from t_applicant_attachment_uploads where id=?",
                    String.class,
                    upload.getId()))
            .isEqualTo("NORMALIZER_UNAVAILABLE");
        assertThat(auditCount(upload.getId())).isZero();
        doReturn(mock(PrivateAttachmentFile.class)).when(storage).readVerified(any());
        assertThat(
                orchestration
                    .upload(
                        applicant,
                        upload.getId(),
                        upload.getIdempotencyKey(),
                        source.path(),
                        source.originalSha256(),
                        source.mediaType(),
                        "operator@example.test")
                    .created())
            .isTrue();
        assertThat(status(upload.getId())).isEqualTo("READY");
        assertThat(
                jdbc.queryForObject(
                    "select failure from t_applicant_attachment_uploads where id=?",
                    String.class,
                    upload.getId()))
            .isNull();
        assertThat(auditCount(upload.getId())).isEqualTo(1);
      }
    }
  }

  @Test
  void durableReservationAndReadyAuditAreIdempotentAndScoped() throws Exception {
    var applicant = applicant();
    var key = UUID.randomUUID();
    try (var image = content()) {
      var upload = service.reserve(applicant, key, image, "operator@example.test");
      assertThat(status(upload.getId())).isEqualTo("PENDING");
      assertThat(service.reserve(applicant, key, image, "operator@example.test").getId())
          .isEqualTo(upload.getId());
      authorize(2L);
      assertThatThrownBy(
              () -> service.lookup(applicant, null, key, image.originalSha256(), image.mediaType()))
          .isInstanceOf(NotFoundException.class);
      assertThatThrownBy(() -> service.download(applicant, upload.getId()))
          .isInstanceOf(NotFoundException.class);
      authorize(1L);
      assertThat(complete(applicant, upload, image).created()).isTrue();
      assertThat(complete(applicant, upload, image).created()).isFalse();
      assertThat(auditCount(upload.getId())).isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "select after_values::text from t_audit_events where target_id=?",
                  String.class,
                  upload.getId()))
          .contains("applicant_id", "attachment_id")
          .doesNotContain("sha256", "image/png", "object_id");
      assertThat(service.list(applicant, true, null, 20).content()).hasSize(1);
    }
  }

  @Test
  void auditFailureRollsBackReadyButKeepsDurableOwnershipAndCanRecover() throws Exception {
    var applicant = applicant();
    try (var image = content()) {
      var upload = service.reserve(applicant, UUID.randomUUID(), image, "operator@example.test");
      jdbc.execute(
          "alter table t_audit_events add constraint ck_attachment_test_fail check(action <> 'APPLICANT_ATTACHMENT_STORED') not valid");
      try {
        assertThatThrownBy(() -> complete(applicant, upload, image))
            .isInstanceOf(RuntimeException.class);
      } finally {
        jdbc.execute("alter table t_audit_events drop constraint ck_attachment_test_fail");
      }
      assertThat(status(upload.getId())).isEqualTo("PENDING");
      assertThat(auditCount(upload.getId())).isZero();
      assertThat(complete(applicant, upload, image).created()).isTrue();
      assertThat(auditCount(upload.getId())).isEqualTo(1);
    }
  }

  @Test
  void withdrawalDuringRemoteWriteIsObservedBeforeReadyCommit() throws Exception {
    var applicant = applicant();
    try (var image = content()) {
      var upload = service.reserve(applicant, UUID.randomUUID(), image, "operator@example.test");
      doAnswer(
              call -> {
                jdbc.update(
                    "update t_applicants set status='WITHDRAWN', version=version+1 where id=?",
                    applicant);
                return null;
              })
          .when(storage)
          .ensureStored(any(), any());
      assertThatThrownBy(() -> complete(applicant, upload, image))
          .isInstanceOf(ServiceException.class);
      assertThat(status(upload.getId())).isEqualTo("PENDING");
      assertThat(auditCount(upload.getId())).isZero();
    }
  }

  @Test
  void concurrentSameReceiptFailsNowaitWithoutDuplicateStorageWrite() throws Exception {
    var applicant = applicant();
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var image = content();
        var executor = Executors.newSingleThreadExecutor()) {
      var upload = service.reserve(applicant, UUID.randomUUID(), image, "operator@example.test");
      doAnswer(
              call -> {
                started.countDown();
                if (!release.await(5, TimeUnit.SECONDS))
                  throw new IllegalStateException("検証同期の期限超過");
                return null;
              })
          .when(storage)
          .ensureStored(any(), any());
      var first =
          executor.submit(
              () -> {
                authorize(1L);
                try {
                  return complete(applicant, upload, image);
                } finally {
                  scope.clear();
                  SecurityContextHolder.clearContext();
                }
              });
      try {
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> complete(applicant, upload, image))
            .isInstanceOf(PessimisticLockingFailureException.class);
      } finally {
        release.countDown();
      }
      assertThat(first.get(5, TimeUnit.SECONDS).created()).isTrue();
      assertThat(auditCount(upload.getId())).isEqualTo(1);
    }
  }

  @Test
  void ownedUploadsBlockPhysicalApplicantAndStoreDeletion() throws Exception {
    var applicant = applicant();
    try (var image = content()) {
      service.reserve(applicant, UUID.randomUUID(), image, "operator@example.test");
      assertThatThrownBy(() -> jdbc.update("delete from t_applicants where id=?", applicant))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> jdbc.update("delete from attachment_test_stores where id=1"))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Configuration
  @EnableTransactionManagement(order = 0)
  @EnableAspectJAutoProxy
  @EnableJpaRepositories(
      basePackageClasses = {AuditEventRepository.class, ApplicantRepository.class})
  @Import({
    AuditWriter.class,
    BusinessAudit.class,
    StoreScopeStampListener.class,
    StoreFilterEnable.class,
    AttachmentTransactions.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
          System.getenv("KIZUNA_ATTACHMENT_TEST_JDBC_URL"), "postgres", "");
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource source, ConfigurableListableBeanFactory beans) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(source);
      factory.setPackagesToScan("com.kizuna.recruitment.domain", "com.kizuna.audit.domain");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(
          Map.of(
              "hibernate.hbm2ddl.auto", "create-drop",
              "hibernate.resource.beans.container", new SpringBeanContainer(beans),
              "hibernate.physical_naming_strategy",
                  "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
      return factory;
    }

    @Bean
    @Primary
    EntityManager entityManager(EntityManagerFactory factory) {
      return SharedEntityManagerCreator.createSharedEntityManager(factory);
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
      return new JpaTransactionManager(factory);
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
    PlatformUserRepository users() {
      var users = mock(PlatformUserRepository.class);
      var actor =
          PlatformUser.builder()
              .email("operator@example.test")
              .password(UUID.randomUUID().toString())
              .displayName("受注担当")
              .enabled(true)
              .userType(UserType.STAFF)
              .roleIds(Set.of(1L))
              .storeScopeType(StoreScopeType.ALL_STORES)
              .build();
      actor.setId(9L);
      when(users.findByEmail("operator@example.test")).thenReturn(Optional.of(actor));
      return users;
    }

    @Bean
    AppProperties properties() {
      return new AppProperties();
    }

    @Bean
    PrivateAttachmentStorage storage() {
      return mock(PrivateAttachmentStorage.class);
    }

    @Bean
    StoreRepository stores(EntityManager em) {
      var repository = mock(StoreRepository.class);
      when(repository.lockAgainstDeletion(any()))
          .thenAnswer(
              call -> {
                var rows =
                    em.createNativeQuery(
                            "select id from attachment_test_stores where id = :id for key share",
                            Long.class)
                        .setParameter("id", call.getArgument(0))
                        .getResultList();
                return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
              });
      return repository;
    }
  }
}
