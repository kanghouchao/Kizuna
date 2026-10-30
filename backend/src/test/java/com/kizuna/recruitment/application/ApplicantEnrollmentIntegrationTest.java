package com.kizuna.recruitment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;

import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.cast.api.dto.CastMapperImpl;
import com.kizuna.cast.application.CastEnrollmentService;
import com.kizuna.cast.application.CastInvitationService;
import com.kizuna.cast.application.CastService;
import com.kizuna.cast.domain.ApplicantReferenceCheck;
import com.kizuna.cast.domain.AttendanceReferenceCheck;
import com.kizuna.cast.domain.OrderReferenceCheck;
import com.kizuna.recruitment.api.dto.ApplicantEnrollmentRequest;
import com.kizuna.recruitment.api.dto.ApplicantMapperImpl;
import com.kizuna.recruitment.domain.ApplicantRepository;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.store.StoreShortNames;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.UserRepository;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.AopTestUtils;

/** 実 PG（Liquibase の baseline）で、在籍登録の原子性と「一つの在籍を指す応募者は一つ」の部分唯一索引を確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicantEnrollmentIntegrationTest {
  private static final String ACTOR = "enroll@example.test";
  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private ApplicantService applicants;
  private BusinessAudit audit;
  private long storeId;
  private long otherStoreId;
  private long actorId;

  @BeforeAll
  void connect() {
    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                IntegrationTestDatabase.springDataSourceProperties(
                    ApplicantEnrollmentIntegrationTest.class))
            .properties(
                Map.ofEntries(
                    Map.entry("spring.config.name", "applicant-enrollment-test"),
                    Map.entry(
                        "spring.liquibase.change-log",
                        "classpath:db/changelog/db.changelog-master.yaml"),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                    Map.entry("spring.jpa.open-in-view", "false"),
                    Map.entry("logging.level.root", "WARN")))
            .run();
    jdbc = context.getBean(JdbcTemplate.class);
    applicants = context.getBean(ApplicantService.class);
    audit = AopTestUtils.getUltimateTargetObject(context.getBean(BusinessAudit.class));
    storeId = store();
    otherStoreId = store();
    actorId =
        jdbc.queryForObject(
            "insert into t_users(email,display_name,user_type,store_scope_type) values (?,'在籍登録担当','STAFF','ALL_STORES') returning id",
            Long.class,
            ACTOR);
  }

  @BeforeEach
  void authenticate() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(ACTOR, null, List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    reset(audit);
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void enrollmentCreatesUnpublishedCastWithoutInvitationOrApplicantPii() {
    String applicant = hiredApplicant(storeId);
    long enrollments = count("t_cast_enrollments where store_id=?", storeId);
    var response = applicants.enroll(storeId, applicant, request(0L), ACTOR);
    String enrollment = response.enrollmentId();
    assertThat(enrollment).isNotNull();
    assertThat(response.version()).isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select enrollment_id from t_applicants where id=?", String.class, applicant))
        .isEqualTo(enrollment);
    assertThat(
            jdbc.queryForMap(
                "select store_id, status, started_on from t_cast_enrollments where id=?",
                enrollment))
        .containsEntry("store_id", storeId)
        .containsEntry("status", "ENROLLED")
        .containsEntry("started_on", Date.valueOf(LocalDate.of(2026, 11, 1)));
    var profile =
        jdbc.queryForMap(
            "select name, introduction, publication_status, custom_fields::text as custom_fields from t_cast_profiles where enrollment_id=?",
            enrollment);
    assertThat(profile)
        .containsEntry("name", "源氏名")
        .containsEntry("introduction", "操作者の紹介文")
        .containsEntry("publication_status", "UNPUBLISHED");
    assertThat(profile.values().toString()).doesNotContain("非公開", "090-0000-0000", "面接");
    assertThat(count("t_cast_invitations where cast_id=?", enrollment)).isZero();
    assertThat(
            jdbc.queryForMap(
                "select before_values::text as before, after_values::text as after from t_audit_events where action='APPLICANT_ENROLLED' and target_id=?",
                applicant))
        .satisfies(
            row -> {
              assertThat(row.get("after").toString()).contains(enrollment, "\"version\": \"1\"");
              assertThat(row.values().toString()).doesNotContain("非公開", "090-0000-0000", "面接");
            });
    assertThatThrownBy(() -> applicants.enroll(storeId, applicant, request(1L), ACTOR))
        .isInstanceOf(ConflictException.class);
    assertThat(count("t_cast_enrollments where store_id=?", storeId)).isEqualTo(enrollments + 1);
  }

  @Test
  void auditFailureAfterCastCreationLeavesNothingBehind() {
    String applicant = hiredApplicant(storeId);
    long enrollments = count("t_cast_enrollments where store_id=?", storeId);
    long audits = count("t_audit_events where store_id=?", storeId);
    doThrow(new IllegalStateException("監査失敗"))
        .when(audit)
        .recordCurrent(eq(storeId), eq("APPLICANT_ENROLLED"), anyString(), any(), anyMap(), any());
    assertThatThrownBy(() -> applicants.enroll(storeId, applicant, request(0L), ACTOR))
        .isInstanceOf(IllegalStateException.class);
    assertThat(count("t_cast_enrollments where store_id=?", storeId)).isEqualTo(enrollments);
    assertThat(count("t_cast_profiles where store_id=?", storeId)).isEqualTo(enrollments);
    assertThat(count("t_audit_events where store_id=?", storeId)).isEqualTo(audits);
    assertThat(
            jdbc.queryForMap(
                "select enrollment_id, version from t_applicants where id=?", applicant))
        .containsEntry("enrollment_id", null)
        .containsEntry("version", 0L);
  }

  @Test
  void oneEnrollmentCannotBeHeldByTwoApplicants() {
    String first = hiredApplicant(storeId);
    String enrollment = applicants.enroll(storeId, first, request(0L), ACTOR).enrollmentId();
    String second = hiredApplicant(storeId);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update t_applicants set enrollment_id=? where id=?", enrollment, second))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uq_t_applicants_enrollment");
  }

  @Test
  void applicantCannotHoldAnotherStoresEnrollment() {
    String enrollment =
        context
            .getBean(CastService.class)
            .create(otherStoreId, request(0L).toCommand(), ACTOR)
            .enrollment()
            .getId();
    String local = hiredApplicant(storeId);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update t_applicants set enrollment_id=? where id=?", enrollment, local))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("fk_t_applicants_enrollment");
  }

  @Test
  void deletingAnEnrollmentHeldByAnApplicantIsRefusedWithConflict() {
    String applicant = hiredApplicant(storeId);
    String enrollment = applicants.enroll(storeId, applicant, request(0L), ACTOR).enrollmentId();
    assertThat(context.getBean(CastService.class).get(storeId, enrollment).isDeletable()).isFalse();
    assertThatThrownBy(() -> context.getBean(CastService.class).delete(storeId, enrollment, ACTOR))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("応募者から在籍登録された");
    assertThat(count("t_cast_enrollments where id=?", enrollment)).isEqualTo(1);
  }

  private static ApplicantEnrollmentRequest request(long version) {
    return new ApplicantEnrollmentRequest(
        version,
        "源氏名",
        null,
        LocalDate.of(2026, 11, 1),
        null,
        "操作者の紹介文",
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private long store() {
    return jdbc.queryForObject(
        "insert into t_stores(name,short_name,domain) values ('在籍登録検証',?,?) returning id",
        Long.class,
        StoreShortNames.next(),
        UUID.randomUUID() + ".example.test");
  }

  private String hiredApplicant(long store) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        """
        insert into t_applicants(id,store_id,created_at,updated_at,version,name,channel,source_type,
          phone,address,status,interview,modified_by,edit_sequence)
        values (?,?,now(),now(),0,'非公開の氏名','PHONE','DIRECT','090-0000-0000','非公開の住所','HIRED',
          '{"interviewAt":"2026-10-01T10:00:00+09:00","interviewer":"面接担当","notes":"面接メモ","checklist":{}}'::jsonb,?,0)
        """,
        id,
        store,
        actorId);
    return id;
  }

  private long count(String fromWhere, Object... args) {
    return jdbc.queryForObject("select count(*) from " + fromWhere, Long.class, args);
  }

  @Configuration
  @EnableAutoConfiguration
  @EntityScan("com.kizuna")
  @EnableJpaRepositories("com.kizuna")
  @Import({
    AuditWriter.class,
    ApplicantService.class,
    ApplicantMapperImpl.class,
    CastService.class,
    CastEnrollmentService.class,
    CastInvitationService.class,
    CastMapperImpl.class
  })
  static class Config {
    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    AppProperties properties() {
      return new AppProperties();
    }

    @Bean
    BusinessAudit businessAudit(UserRepository users, AuditWriter writer) {
      return spy(new BusinessAudit(users, writer));
    }

    @Bean
    SystemConfigService systemConfigs() {
      return mock(SystemConfigService.class);
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
    ApplicantReferenceCheck applicantReferences(ApplicantRepository applicants) {
      return applicants::findReferencedEnrollmentIds;
    }
  }
}
