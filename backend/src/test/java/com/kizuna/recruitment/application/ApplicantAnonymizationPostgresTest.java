package com.kizuna.recruitment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.recruitment.domain.Applicant;
import com.kizuna.recruitment.infrastructure.AttachmentObject;
import com.kizuna.recruitment.infrastructure.PrivateAttachmentStorage;
import com.kizuna.settings.application.ApplicantRetentionSettings;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.persistence.EnabledIfPostgres;
import com.kizuna.shared.persistence.PostgresTestDatabase;
import com.kizuna.store.StoreShortNames;
import com.kizuna.task.application.TaskLifecycle;
import com.kizuna.task.application.TaskRegistry;
import com.kizuna.task.execution.TaskCommand;
import com.kizuna.task.execution.TaskExecutor;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import java.sql.Connection;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

/** 実 PG（Liquibase の baseline）で、実行基盤から起動した匿名化の対象選定・一取引の確定・途中失敗後の再処理を確かめる。 */
@EnabledIfPostgres
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicantAnonymizationPostgresTest {
  private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);
  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private TaskExecutor executor;
  private PrivateAttachmentStorage storage;
  private SystemConfigService systemConfigs;
  private long storeId;
  private long serviceId;
  private AuditActor operator;
  private long anonymizeRole;

  @BeforeAll
  void connect() {
    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                PostgresTestDatabase.springDataSourceProperties(
                    ApplicantAnonymizationPostgresTest.class))
            .properties(
                Map.ofEntries(
                    Map.entry("spring.config.name", "applicant-anonymization-test"),
                    Map.entry(
                        "spring.liquibase.change-log",
                        "classpath:db/changelog/db.changelog-master.yaml"),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                    Map.entry("spring.jpa.open-in-view", "false"),
                    Map.entry("logging.level.root", "WARN")))
            .run();
    jdbc = context.getBean(JdbcTemplate.class);
    executor = context.getBean(TaskExecutor.class);
    storage = context.getBean(PrivateAttachmentStorage.class);
    systemConfigs = context.getBean(SystemConfigService.class);
    storeId =
        jdbc.queryForObject(
            "insert into t_stores(name,short_name,domain) values ('匿名化検証',?,?) returning id",
            Long.class,
            StoreShortNames.next(),
            UUID.randomUUID() + ".example.test");
    anonymizeRole = role("APPLICANT_ANONYMIZE");
    serviceId =
        jdbc.queryForObject(
            "insert into t_users(display_name,user_type,store_scope_type) values ('匿名化処理','SERVICE','ALL_STORES') returning id",
            Long.class);
    jdbc.update(
        "insert into t_user_roles(user_id,role_id) values (?,?),(?,?)",
        serviceId,
        role("TASK_EXECUTE"),
        serviceId,
        anonymizeRole);
    operator =
        new AuditActor(
            jdbc.queryForObject(
                "insert into t_users(email,display_name,user_type,store_scope_type) values (?,'運用担当','STAFF','ALL_STORES') returning id",
                Long.class,
                UUID.randomUUID() + "@example.test"),
            "STAFF",
            "運用担当");
  }

  @BeforeEach
  void prepare() {
    jdbc.update("delete from t_applicant_attachment_uploads");
    jdbc.update("delete from t_applicant_status_histories");
    jdbc.update("delete from t_applicants");
    clearInvocations(storage);
    when(systemConfigs.applicantRetentionSettings())
        .thenReturn(new ApplicantRetentionSettings(6, null, 0));
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void anonymizesOnlyDueTerminalApplicantsAndKeepsTheSelectionRecord() {
    String due = applicant("REJECTED", TODAY.minusMonths(6), null);
    attachment(due);
    attachment(due);
    String notYet = applicant("REJECTED", TODAY.minusMonths(6).plusDays(1), null);
    String unset = applicant("WITHDRAWN", TODAY.minusYears(3), null);
    String open = applicant("SCREENING", TODAY.minusYears(3), null);
    String enrollment = enrollment();
    String hiredToday = applicant("HIRED", TODAY, enrollment);

    var run = executor.executeScheduled(command()).execution();

    assertThat(run.status()).isEqualTo("SUCCEEDED");
    assertThat(run.processedCount()).isEqualTo(2);
    for (String kept : new String[] {notYet, unset, open})
      assertThat(row(kept)).containsEntry("name", "非公開の氏名").containsEntry("anonymized_at", null);
    for (String done : new String[] {due, hiredToday}) {
      var row = row(done);
      assertThat(row)
          .containsEntry("name", Applicant.ANONYMIZED_NAME)
          .containsEntry("phone", null)
          .containsEntry("email", null)
          .containsEntry("address", null)
          .containsEntry("experience", null)
          .containsEntry("desired_conditions", null)
          .containsEntry("referrer", null)
          .containsEntry("assignee", "担当者")
          .containsEntry("version", 1L)
          .containsEntry("modified_by", serviceId);
      assertThat(row.get("anonymized_at")).isNotNull();
      assertThat(row.get("interview").toString())
          .contains("面接担当", "2026-")
          .doesNotContain("面接メモ", "希望確認");
      assertThat(
              jdbc.queryForObject(
                  "select reason from t_applicant_status_histories where applicant_id=?",
                  String.class,
                  done))
          .isEqualTo("非公開の確定理由");
      assertThat(
              jdbc.queryForMap(
                  "select before_values::text as before, after_values::text as after, source_type, source_id from t_audit_events where action='APPLICANT_ANONYMIZED' and target_id=?",
                  done))
          .containsEntry("source_type", "TASK_EXECUTION")
          .containsEntry("source_id", run.id().toString())
          .satisfies(
              audit -> {
                assertThat(audit.get("after").toString()).contains("\"version\": \"1\"");
                assertThat(audit.values().toString()).doesNotContain("非公開", "090", "面接");
              });
    }
    assertThat(row(hiredToday))
        .containsEntry("status", "HIRED")
        .containsEntry("enrollment_id", enrollment);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_cast_enrollments where id=?", Long.class, enrollment))
        .isEqualTo(1);
    assertThat(count("t_applicant_attachment_uploads")).isZero();
    verify(storage, times(2)).delete(any(AttachmentObject.class));
  }

  @Test
  void midwayFailureRollsBackTheDatabaseAndTheNextRunCompletesTheSameApplicants() {
    // 対象は id 順に処理されるので、添付のある側を先、監査が失敗する側を後にする。
    String a = applicant("REJECTED", TODAY.minusYears(1), null);
    String b = applicant("REJECTED", TODAY.minusYears(1), null);
    String first = a.compareTo(b) < 0 ? a : b;
    String second = first.equals(a) ? b : a;
    attachment(first);
    jdbc.execute(
        "create function anonymization_reject_audit() returns trigger language plpgsql as $$ begin if NEW.action = 'APPLICANT_ANONYMIZED' and NEW.target_id = '"
            + second
            + "' then raise exception '検証用監査障害'; end if; return NEW; end $$");
    jdbc.execute(
        "create trigger anonymization_reject_audit before insert on t_audit_events for each row execute function anonymization_reject_audit()");
    Long failedId;
    try {
      var failed = executor.executeScheduled(command()).execution();
      assertThat(failed.status()).isEqualTo("FAILED");
      failedId = failed.id();
    } finally {
      jdbc.execute("drop trigger anonymization_reject_audit on t_audit_events");
      jdbc.execute("drop function anonymization_reject_audit()");
    }
    // 実体は先に消えたが、DB 側は一件も確定していない。
    verify(storage).delete(any(AttachmentObject.class));
    assertThat(row(first)).containsEntry("anonymized_at", null).containsEntry("name", "非公開の氏名");
    assertThat(row(second)).containsEntry("anonymized_at", null);
    assertThat(count("t_applicant_attachment_uploads")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_audit_events where action='APPLICANT_ANONYMIZED' and target_id in (?,?)",
                Long.class,
                first,
                second))
        .isZero();

    assertThatThrownBy(() -> executor.retry(failedId, "監査の復旧", operator))
        .isInstanceOf(AccessDeniedException.class);
    jdbc.update(
        "insert into t_user_roles(user_id,role_id) values (?,?)", operator.id(), anonymizeRole);
    var retried = executor.retry(failedId, "監査の復旧", operator);

    assertThat(retried.status()).isEqualTo("SUCCEEDED");
    assertThat(retried.processedCount()).isEqualTo(2);
    assertThat(row(first).get("anonymized_at")).isNotNull();
    assertThat(row(second).get("anonymized_at")).isNotNull();
    assertThat(count("t_applicant_attachment_uploads")).isZero();
    verify(storage, times(2)).delete(any(AttachmentObject.class));
    assertThat(executor.executeScheduled(command()).execution().processedCount()).isZero();
  }

  @Test
  void storageFailureLeavesEverythingForTheNextRun() {
    String due = applicant("REJECTED", TODAY.minusYears(1), null);
    attachment(due);
    doThrow(new IllegalStateException("保存先障害")).when(storage).delete(any(AttachmentObject.class));
    try {
      assertThat(executor.executeScheduled(command()).execution().status()).isEqualTo("FAILED");
    } finally {
      reset(storage);
    }
    assertThat(row(due)).containsEntry("anonymized_at", null);
    assertThat(count("t_applicant_attachment_uploads")).isEqualTo(1);
    assertThat(executor.executeScheduled(command()).execution().processedCount()).isEqualTo(1);
  }

  @Test
  void oldestTargetsAcrossStatesComeFirstSoOneBacklogCannotStarveTheOthers() {
    when(systemConfigs.applicantRetentionSettings())
        .thenReturn(new ApplicantRetentionSettings(6, 6, null));
    var properties = context.getBean(AppProperties.class).getApplicantAnonymization();
    properties.setMaxApplicants(2);
    try {
      String rejectedOld = applicant("REJECTED", TODAY.minusYears(2), null);
      applicant("REJECTED", TODAY.minusYears(1), null);
      applicant("REJECTED", TODAY.minusYears(1), null);
      String withdrawnOldest = applicant("WITHDRAWN", TODAY.minusYears(3), null);

      var run = executor.executeScheduled(command()).execution();

      assertThat(run.processedCount()).isEqualTo(2);
      assertThat(row(withdrawnOldest).get("anonymized_at")).isNotNull();
      assertThat(row(rejectedOld).get("anonymized_at")).isNotNull();
      assertThat(count("t_applicants where anonymized_at is null")).isEqualTo(2);
    } finally {
      properties.setMaxApplicants(100);
    }
  }

  @Test
  void waitsForAnInFlightAttachmentWriteWithoutHoldingTheApplicantLock() throws Exception {
    String due = applicant("REJECTED", TODAY.minusYears(1), null);
    attachment(due);
    var source = context.getBean(DataSource.class);
    try (Connection upload = source.getConnection();
        var pool = Executors.newSingleThreadExecutor()) {
      upload.setAutoCommit(false);
      try (var statement = upload.createStatement()) {
        statement.execute("set local lock_timeout = '3s'");
      }
      // 保存完了処理と同じく、添付行を握ったまま実体を書いている最中を再現する。
      lock(
          upload,
          "select id from t_applicant_attachment_uploads where applicant_id=? for no key update",
          due);
      var run = pool.submit(() -> executor.executeScheduled(command()).execution());
      awaitLockWait();
      // 匿名化が応募者を先に握っていれば、ここで待ち合いになり lock_timeout で失敗する。
      lock(upload, "select id from t_applicants where id=? for update", due);
      verify(storage, never()).delete(any(AttachmentObject.class));
      upload.commit();
      assertThat(run.get(20, TimeUnit.SECONDS).status()).isEqualTo("SUCCEEDED");
    }
    assertThat(row(due).get("anonymized_at")).isNotNull();
    assertThat(count("t_applicant_attachment_uploads")).isZero();
    verify(storage).delete(any(AttachmentObject.class));
  }

  private static void lock(Connection connection, String sql, String id) throws Exception {
    try (var statement = connection.prepareStatement(sql)) {
      statement.setString(1, id);
      statement.executeQuery().close();
    }
  }

  private void awaitLockWait() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      Long waiting =
          jdbc.queryForObject(
              "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'",
              Long.class);
      if (waiting != null && waiting > 0) return;
      Thread.sleep(20);
    }
    throw new AssertionError("匿名化が添付行のロックを待っていません");
  }

  private TaskCommand command() {
    return new TaskCommand(
        "APPLICANT_ANONYMIZATION", UUID.randomUUID().toString(), serviceId, null, TODAY, TODAY);
  }

  private Map<String, Object> row(String id) {
    return jdbc.queryForMap(
        "select name, phone, email, address, experience, desired_conditions, referrer, assignee, status, enrollment_id, version, modified_by, anonymized_at, interview::text as interview from t_applicants where id=?",
        id);
  }

  private long count(String fromWhere) {
    return jdbc.queryForObject("select count(*) from " + fromWhere, Long.class);
  }

  /** 終端への遷移を、業務日の正午（日本時間）に記録した応募者を作る。 */
  private String applicant(String status, LocalDate transitionedOn, String enrollmentId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        """
        insert into t_applicants(id,store_id,created_at,updated_at,version,name,channel,source_type,referrer,
          assignee,phone,email,address,experience,desired_conditions,status,interview,modified_by,edit_sequence,enrollment_id)
        values (?,?,now(),now(),0,'非公開の氏名','PHONE','REFERRAL','非公開の紹介者','担当者','090-0000-0000',
          'private@example.test','非公開の住所','非公開の経歴','非公開の希望',?,
          '{"interviewAt":"2026-01-10T10:00:00+09:00","interviewer":"面接担当","notes":"面接メモ","checklist":{"希望確認":true}}'::jsonb,?,0,?)
        """,
        id,
        storeId,
        status,
        operator.id(),
        enrollmentId);
    OffsetDateTime at = transitionedOn.atTime(12, 0).atZone(ZONE).toOffsetDateTime();
    jdbc.update(
        "insert into t_applicant_status_histories(id,store_id,created_at,updated_at,version,applicant_id,actor_id,previous_status,new_status,reason) values (?,?,?,?,0,?,?,'INTERVIEWED',?,'非公開の確定理由')",
        UUID.randomUUID().toString(),
        storeId,
        at,
        at,
        id,
        operator.id(),
        status);
    return id;
  }

  private void attachment(String applicantId) {
    jdbc.update(
        """
        insert into t_applicant_attachment_uploads(id,store_id,created_at,updated_at,version,applicant_id,idempotency_key,
          object_id,original_sha256,canonical_sha256,media_type,size_bytes,normalizer_version,created_by,completed_by,completed_at,status)
        values (?,?,now(),now(),0,?,?,?,?,?,'image/png',3,'v1',?,?,now(),'READY')
        """,
        UUID.randomUUID().toString(),
        storeId,
        applicantId,
        UUID.randomUUID(),
        UUID.randomUUID(),
        "a".repeat(64),
        "b".repeat(64),
        operator.id(),
        operator.id());
  }

  private String enrollment() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_cast_enrollments(id,store_id,started_on) values (?,?,?)",
        id,
        storeId,
        TODAY.minusMonths(1));
    return id;
  }

  private long role(String permission) {
    long id =
        jdbc.queryForObject(
            "insert into t_roles(name) values (?) returning id",
            Long.class,
            UUID.randomUUID().toString());
    jdbc.update(
        "insert into t_role_permissions(role_id,permission_id) select ?,id from t_permissions where code=?",
        id,
        permission);
    return id;
  }

  @Configuration
  @EnableAutoConfiguration
  @EntityScan("com.kizuna")
  @EnableJpaRepositories("com.kizuna")
  @Import({
    ApplicantAnonymizationTask.class,
    TaskExecutor.class,
    TaskLifecycle.class,
    TaskRegistry.class,
    AuditWriter.class,
    ServiceExecutionIdentityService.class
  })
  static class Config {
    @Bean
    Clock clock() {
      return Clock.fixed(TODAY.atTime(0, 15).atZone(ZONE).toInstant(), ZONE);
    }

    @Bean
    AppProperties properties() {
      return new AppProperties();
    }

    @Bean
    SystemConfigService systemConfigs() {
      return mock(SystemConfigService.class);
    }

    @Bean
    PrivateAttachmentStorage storage() {
      return mock(PrivateAttachmentStorage.class);
    }
  }
}
