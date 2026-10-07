package com.kizuna.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.cast.application.CastService;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastProfileRepository;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.persistence.StoreScopeStampListener;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreFilterEnable;
import com.kizuna.shift.api.dto.AttendanceCancellationRequest;
import com.kizuna.shift.api.dto.AttendanceCorrectionRequest;
import com.kizuna.shift.api.dto.AttendanceCreateRequest;
import com.kizuna.shift.api.dto.AttendanceMapperImpl;
import com.kizuna.shift.api.dto.ShiftChangeRequestCreateRequest;
import com.kizuna.shift.api.dto.ShiftCreateRequest;
import com.kizuna.shift.api.dto.ShiftMapperImpl;
import com.kizuna.shift.api.dto.ShiftRequestCreateRequest;
import com.kizuna.shift.api.dto.ShiftRequestMapperImpl;
import com.kizuna.shift.api.dto.ShiftUpdateRequest;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRepository;
import com.kizuna.shift.domain.ShiftRequest;
import com.kizuna.shift.domain.ShiftStatus;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

/** 同じSpring取引の業務・既存訂正・監査を実PostgreSQLで検証する。 */
@EnabledIfEnvironmentVariable(named = "KIZUNA_SHIFT_AUDIT_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShiftAuditPostgresTest {
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private ShiftService shifts;
  private CastShiftRequestService own;
  private ShiftRequestService decisions;
  private AttendanceService actuals;
  private TransactionTemplate transactions;
  private final LocalDate date = LocalDate.of(2030, 1, 2);

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    shifts = context.getBean(ShiftService.class);
    own = context.getBean(CastShiftRequestService.class);
    decisions = context.getBean(ShiftRequestService.class);
    actuals = context.getBean(AttendanceService.class);
    transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    jdbc.execute(
        "alter table t_shift_requests add constraint fk_shift_audit_request foreign key (shift_id) references t_shifts(id) on delete set null");
    jdbc.execute(
        "alter table t_attendances add constraint fk_t_attendances_shift foreign key (shift_id) references t_shifts(id)");
    jdbc.execute(
        "create unique index uq_t_attendances_active_shift on t_attendances (shift_id) where cancelled_at is null and shift_id is not null");
  }

  @BeforeEach
  void authenticate() {
    context.getBean(StoreContext.class).setStoreId(1L);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("operator@example.test", null, List.of()));
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

  private ShiftCreateRequest shiftRequest() {
    var r = new ShiftCreateRequest();
    r.setCastId("cast");
    r.setWorkDate(date);
    r.setStartTime(LocalTime.of(10, 0));
    r.setEndTime(LocalTime.of(18, 0));
    return r;
  }

  private String submit() {
    var r = new ShiftRequestCreateRequest();
    r.setStoreId(1L);
    r.setWorkDate(date);
    r.setStartTime(LocalTime.of(10, 0));
    r.setEndTime(LocalTime.of(18, 0));
    r.setNote("秘密希望");
    return own.submit("cast@example.test", r).getId();
  }

  private AttendanceCreateRequest attendance(String shiftId) {
    var r = new AttendanceCreateRequest();
    r.setCastId("cast");
    r.setShiftId(shiftId);
    r.setActualStartAt(date.atTime(10, 0));
    r.setWaitingPlace("秘密場所");
    return r;
  }

  private long count(String table) {
    return jdbc.queryForObject("select count(*) from " + table, Long.class);
  }

  private void reject(String action) {
    jdbc.execute(
        "alter table t_audit_events add constraint ck_shift_audit_reject check (action <> '"
            + action
            + "') not valid");
  }

  private void allow() {
    jdbc.execute("alter table t_audit_events drop constraint ck_shift_audit_reject");
  }

  @Test
  void realVersionsAndNoOpsSurvivePhysicalDeletion() {
    var id = shifts.create(shiftRequest(), "operator@example.test").getId();
    var patch = new ShiftUpdateRequest();
    patch.setStartTime(LocalTime.of(11, 0));
    shifts.update(id, patch, "operator@example.test");
    shifts.update(id, patch, "operator@example.test");
    shifts.changePublication(id, false, "operator@example.test");
    shifts.changePublication(id, false, "operator@example.test");
    shifts.delete(id);
    var rows =
        jdbc.queryForList(
            "select action, before_values ->> 'version' as old_version, after_values ->> 'version' as new_version from t_audit_events where target_type='SHIFT' and target_id=? order by id",
            id);
    assertThat(rows).hasSize(4);
    assertThat(rows.get(0)).containsEntry("new_version", "0");
    assertThat(rows.get(1)).containsEntry("old_version", "0").containsEntry("new_version", "1");
    assertThat(rows.get(2)).containsEntry("old_version", "1").containsEntry("new_version", "2");
    assertThat(rows.get(3)).containsEntry("old_version", "2").containsEntry("new_version", null);
    assertThat(jdbc.queryForObject("select count(*) from t_shifts where id=?", Long.class, id))
        .isZero();
  }

  @Test
  void approvalFailureRestoresRequestAndRemovesDerivedShiftAndEarlierAudit() {
    var id = submit();
    long shiftsBefore = count("t_shifts"), audits = count("t_audit_events");
    reject("SHIFT_REQUEST_APPROVED");
    try {
      assertThatThrownBy(() -> decisions.approve(id, false, "operator@example.test"))
          .isInstanceOf(RuntimeException.class);
      assertThat(
              jdbc.queryForMap(
                  "select status, shift_id, version from t_shift_requests where id=?", id))
          .containsEntry("status", "PENDING")
          .containsEntry("shift_id", null)
          .containsEntry("version", 0L);
      assertThat(count("t_shifts")).isEqualTo(shiftsBefore);
      assertThat(count("t_audit_events")).isEqualTo(audits);
    } finally {
      allow();
    }
  }

  @Test
  void deletionUnlinksRequestWithoutInventingVersionAndKeepsSafeSnapshots() {
    var id = submit();
    var approved = decisions.approve(id, false, "operator@example.test");
    var shiftId = approved.getShiftId();
    shifts.delete(shiftId);
    assertThat(jdbc.queryForMap("select shift_id, version from t_shift_requests where id=?", id))
        .containsEntry("shift_id", null)
        .containsEntry("version", 1L);
    var event =
        jdbc.queryForMap(
            "select before_values ->> 'shift_id' as old_shift, after_values ->> 'shift_id' as new_shift, before_values ->> 'version' as old_version, after_values ->> 'version' as new_version, source_id from t_audit_events where action='SHIFT_REQUEST_UNLINKED' and target_id=?",
            id);
    assertThat(event)
        .containsEntry("old_shift", shiftId)
        .containsEntry("new_shift", "")
        .containsEntry("old_version", "1")
        .containsEntry("new_version", "1")
        .containsEntry("source_id", shiftId);
    assertThat(
            jdbc.queryForObject(
                "select actor_type from t_audit_events where action='SHIFT_REQUEST_SUBMITTED' and target_id=?",
                String.class,
                id))
        .isEqualTo("CAST");
  }

  @Test
  void rejectedDeletionRestoresShiftLinkAndBothAudits() {
    var id = submit();
    var shiftId = decisions.approve(id, false, "operator@example.test").getShiftId();
    long audits = count("t_audit_events");
    reject("SHIFT_DELETED");
    try {
      assertThatThrownBy(() -> shifts.delete(shiftId)).isInstanceOf(RuntimeException.class);
      assertThat(
              jdbc.queryForObject(
                  "select shift_id from t_shift_requests where id=?", String.class, id))
          .isEqualTo(shiftId);
      assertThat(
              jdbc.queryForObject("select count(*) from t_shifts where id=?", Long.class, shiftId))
          .isEqualTo(1);
      assertThat(count("t_audit_events")).isEqualTo(audits);
    } finally {
      allow();
    }
  }

  @Test
  void auditFailureRollsBackEachAttendanceWriteAndItsCorrectionHistory() {
    var id = shifts.create(shiftRequest(), "operator@example.test").getId();
    var patch = new ShiftUpdateRequest();
    patch.setStatus(ShiftStatus.CONFIRMED);
    shifts.update(id, patch, "operator@example.test");
    var r = attendance(id);
    long audits = count("t_audit_events"), records = count("t_attendances");
    reject("ATTENDANCE_RECORDED");
    try {
      assertThatThrownBy(() -> actuals.record(r, "operator@example.test"))
          .isInstanceOf(RuntimeException.class);
      assertThat(count("t_attendances")).isEqualTo(records);
      assertThat(count("t_audit_events")).isEqualTo(audits);
    } finally {
      allow();
    }
    var attendanceId = actuals.record(r, "operator@example.test").getId();
    var correction = new AttendanceCorrectionRequest();
    correction.setBusinessDate(date);
    correction.setActualStartAt(date.atTime(11, 0));
    correction.setWaitingPlace("別の秘密");
    long histories = count("t_attendance_corrections");
    audits = count("t_audit_events");
    reject("ATTENDANCE_CORRECTED");
    try {
      assertThatThrownBy(() -> actuals.correct(attendanceId, correction, "operator@example.test"))
          .isInstanceOf(RuntimeException.class);
      assertThat(count("t_attendance_corrections")).isEqualTo(histories);
      assertThat(count("t_audit_events")).isEqualTo(audits);
      assertThat(
              jdbc.queryForObject(
                  "select version from t_attendances where id=?", Long.class, attendanceId))
          .isZero();
    } finally {
      allow();
    }
    actuals.correct(attendanceId, correction, "operator@example.test");
    var source =
        jdbc.queryForObject(
            "select source_id from t_audit_events where action='ATTENDANCE_CORRECTED' and target_id=?",
            String.class,
            attendanceId);
    assertThat(
            jdbc.queryForObject(
                "select attendance_id from t_attendance_corrections where id=?",
                String.class,
                source))
        .isEqualTo(attendanceId);
    var cancel = new AttendanceCancellationRequest();
    cancel.setReason("秘密理由");
    audits = count("t_audit_events");
    reject("ATTENDANCE_CANCELLED");
    try {
      assertThatThrownBy(() -> actuals.cancel(attendanceId, cancel, "operator@example.test"))
          .isInstanceOf(RuntimeException.class);
      assertThat(
              jdbc.queryForObject(
                  "select cancelled_at is null from t_attendances where id=?",
                  Boolean.class,
                  attendanceId))
          .isTrue();
      assertThat(count("t_audit_events")).isEqualTo(audits);
    } finally {
      allow();
    }
    actuals.cancel(attendanceId, cancel, "operator@example.test");
    audits = count("t_audit_events");
    assertThatThrownBy(() -> shifts.delete(id)).isInstanceOf(RuntimeException.class);
    assertThat(count("t_audit_events")).isEqualTo(audits);
    assertThat(
            jdbc.queryForList(
                "select before_values::text || after_values::text as data from t_audit_events where target_type='ATTENDANCE' and target_id=?",
                String.class,
                attendanceId))
        .noneMatch(v -> v.contains("秘密"));
  }

  @Test
  void outerFailureAndStaleVersionDoNotLeaveSuccessEvents() {
    long audits = count("t_audit_events"), rows = count("t_shifts");
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      shifts.create(shiftRequest(), "operator@example.test");
                      throw new IllegalStateException("後続失敗");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(count("t_shifts")).isEqualTo(rows);
    assertThat(count("t_audit_events")).isEqualTo(audits);
    var id = shifts.create(shiftRequest(), "operator@example.test").getId();
    audits = count("t_audit_events");
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      var em = context.getBean(EntityManager.class);
                      em.find(Shift.class, id);
                      jdbc.update("update t_shifts set version=version+1 where id=?", id);
                      shifts.changePublication(id, false, "operator@example.test");
                    }))
        .isInstanceOf(RuntimeException.class);
    assertThat(count("t_audit_events")).isEqualTo(audits);
    assertThat(
            jdbc.queryForObject("select is_published from t_shifts where id=?", Boolean.class, id))
        .isTrue();
  }

  @Test
  void twoApprovalsOfTheSameVersionCommitOnlyOneDerivedShift() throws Exception {
    var id = submit();
    long rows = count("t_shifts"), audits = count("t_audit_events");
    var read = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Callable<Boolean> approve =
          () -> {
            authenticate();
            try {
              transactions.executeWithoutResult(
                  status -> {
                    context.getBean(EntityManager.class).find(ShiftRequest.class, id);
                    read.countDown();
                    try {
                      if (!release.await(10, TimeUnit.SECONDS))
                        throw new IllegalStateException("競合待機が終了しません");
                    } catch (InterruptedException ex) {
                      Thread.currentThread().interrupt();
                      throw new IllegalStateException(ex);
                    }
                    decisions.approve(id, false, "operator@example.test");
                  });
              return true;
            } catch (ObjectOptimisticLockingFailureException ex) {
              return false;
            } finally {
              clear();
            }
          };
      var first = pool.submit(approve);
      var second = pool.submit(approve);
      try {
        assertThat(read.await(10, TimeUnit.SECONDS)).isTrue();
      } finally {
        release.countDown();
      }
      assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(count("t_shifts")).isEqualTo(rows + 1);
    assertThat(count("t_audit_events")).isEqualTo(audits + 2);
    assertThat(
            jdbc.queryForObject("select version from t_shift_requests where id=?", Long.class, id))
        .isEqualTo(1L);
  }

  @Test
  void deletionWinsAgainstAnAlreadyReadChangeApprovalWithoutGhostSuccess() throws Exception {
    var newId = submit();
    var shiftId = decisions.approve(newId, false, "operator@example.test").getShiftId();
    var change = new ShiftChangeRequestCreateRequest();
    change.setShiftId(shiftId);
    change.setWorkDate(date);
    change.setStartTime(LocalTime.of(11, 0));
    change.setEndTime(LocalTime.of(18, 0));
    var changeId = own.submitChange("cast@example.test", change).getId();
    long audits = count("t_audit_events");
    var locked = new CountDownLatch(1);
    var read = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var deletion =
          pool.submit(
              () -> {
                authenticate();
                try {
                  transactions.executeWithoutResult(
                      status -> {
                        context
                            .getBean(ShiftRepository.class)
                            .findScopedByIdForUpdate(shiftId)
                            .orElseThrow();
                        locked.countDown();
                        try {
                          if (!read.await(10, TimeUnit.SECONDS))
                            throw new IllegalStateException("申請読取が終了しません");
                        } catch (InterruptedException ex) {
                          Thread.currentThread().interrupt();
                          throw new IllegalStateException(ex);
                        }
                        shifts.delete(shiftId);
                      });
                } finally {
                  clear();
                }
              });
      var approval =
          pool.submit(
              () -> {
                authenticate();
                try {
                  assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                  assertThatThrownBy(
                          () ->
                              transactions.executeWithoutResult(
                                  status -> {
                                    context
                                        .getBean(EntityManager.class)
                                        .find(ShiftRequest.class, changeId);
                                    read.countDown();
                                    decisions.approve(changeId, null, "operator@example.test");
                                  }))
                      .isInstanceOf(NotFoundException.class);
                } finally {
                  read.countDown();
                  clear();
                }
                return true;
              });
      deletion.get(15, TimeUnit.SECONDS);
      assertThat(approval.get(15, TimeUnit.SECONDS)).isTrue();
    }
    assertThat(
            jdbc.queryForMap(
                "select status,shift_id,version from t_shift_requests where id=?", changeId))
        .containsEntry("status", "PENDING")
        .containsEntry("shift_id", null)
        .containsEntry("version", 0L);
    assertThat(jdbc.queryForObject("select count(*) from t_shifts where id=?", Long.class, shiftId))
        .isZero();
    assertThat(count("t_audit_events")).isEqualTo(audits + 3);
    assertThat(
            jdbc.queryForObject(
                "select after_values ->> 'version' from t_audit_events where action='SHIFT_REQUEST_UNLINKED' and target_id=?",
                String.class,
                changeId))
        .isEqualTo("0");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_audit_events where action='SHIFT_REQUEST_APPROVED' and target_id=?",
                Long.class,
                changeId))
        .isZero();
  }

  @Configuration
  @EnableTransactionManagement
  @EnableAspectJAutoProxy
  @EnableJpaRepositories(basePackageClasses = {AuditEventRepository.class, ShiftRepository.class})
  @Import({
    AuditWriter.class,
    BusinessAudit.class,
    StoreScopeStampListener.class,
    StoreFilterEnable.class,
    ShiftService.class,
    ShiftRequestService.class,
    CastShiftRequestService.class,
    AttendanceService.class,
    ShiftMapperImpl.class,
    ShiftRequestMapperImpl.class,
    AttendanceMapperImpl.class,
    BusinessDateService.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
          System.getenv("KIZUNA_SHIFT_AUDIT_TEST_JDBC_URL"), "postgres", "");
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource source, ConfigurableListableBeanFactory beans) {
      var f = new LocalContainerEntityManagerFactoryBean();
      f.setDataSource(source);
      f.setPackagesToScan(
          "com.kizuna.shift.domain",
          "com.kizuna.audit.domain",
          "com.kizuna.cast.domain",
          "com.kizuna.store.domain");
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
    StoreRepository stores() {
      return mock(StoreRepository.class);
    }

    @Bean
    SystemConfigService settings() {
      return mock(SystemConfigService.class);
    }

    @Bean
    CastProfileRepository profiles() {
      return mock(CastProfileRepository.class);
    }

    @Bean
    CastService casts() {
      var c = mock(CastService.class);
      when(c.existsForCurrentStoreForUpdate("cast")).thenReturn(true);
      return c;
    }

    @Bean
    CastEnrollmentRepository enrollments() {
      var r = mock(CastEnrollmentRepository.class);
      var e = CastEnrollment.builder().build();
      e.setId("cast");
      e.setStoreId(1L);
      when(r.findScopedByIdForUpdate("cast")).thenReturn(Optional.of(e));
      when(r.findByIdForUpdate("cast")).thenReturn(Optional.of(e));
      when(r.findIdsByPlatformUserIdAndStoreId(10L, 1L)).thenReturn(List.of("cast"));
      when(r.findIdsByPlatformUserId(10L)).thenReturn(List.of("cast"));
      return r;
    }

    @Bean
    PlatformUserRepository users() {
      var r = mock(PlatformUserRepository.class);
      var staff =
          PlatformUser.builder()
              .email("operator@example.test")
              .displayName("担当")
              .password(UUID.randomUUID().toString())
              .enabled(true)
              .userType(UserType.STAFF)
              .roleIds(Set.of(1L))
              .storeScopeType(StoreScopeType.ALL_STORES)
              .build();
      staff.setId(9L);
      var cast =
          PlatformUser.builder()
              .email("cast@example.test")
              .displayName("本人")
              .password(UUID.randomUUID().toString())
              .enabled(true)
              .userType(UserType.CAST)
              .build();
      cast.setId(10L);
      when(r.findByEmail(anyString()))
          .thenAnswer(
              call -> Optional.of("cast@example.test".equals(call.getArgument(0)) ? cast : staff));
      when(r.findById(anyLong()))
          .thenAnswer(
              call -> Optional.of(Long.valueOf(10).equals(call.getArgument(0)) ? cast : staff));
      return r;
    }
  }
}
