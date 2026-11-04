package com.kizuna.remuneration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastEnrollmentStatus;
import com.kizuna.cast.domain.CastProfile;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.cast.remuneration.RemunerationPersonLookup;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCourse;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.infrastructure.RemunerationQuery;
import com.kizuna.order.remuneration.OrderRemunerationFacts;
import com.kizuna.remuneration.api.dto.StatementAcceptanceRequest;
import com.kizuna.remuneration.api.dto.StatementAcceptanceResponse;
import com.kizuna.remuneration.api.me.SelfStatementAcceptanceController;
import com.kizuna.remuneration.application.RemunerationStatement.Day;
import com.kizuna.remuneration.domain.AcceptanceState;
import com.kizuna.remuneration.domain.BonusAward;
import com.kizuna.remuneration.domain.GuaranteeState;
import com.kizuna.remuneration.domain.GuaranteeTerm;
import com.kizuna.remuneration.domain.NotAcceptableReason;
import com.kizuna.remuneration.domain.StatementAcceptanceRepository;
import com.kizuna.remuneration.infrastructure.RemunerationRecords;
import com.kizuna.settings.application.BusinessDateService;
import com.kizuna.settings.application.SystemConfigService;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.shift.domain.Attendance;
import com.kizuna.shift.remuneration.AttendanceFacts;
import com.kizuna.store.StoreShortNames;
import com.kizuna.store.domain.Store;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManager;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 実 PG（Liquibase の baseline）で、明細の承諾の判定・追加型の行・一意制約・監査を確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatementAcceptanceIntegrationTest {
  private static final LocalDate DAY = LocalDate.of(2026, 9, 30);
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);
  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private EntityManager em;
  private TransactionTemplate transactions;
  private RemunerationStatementService statements;
  private StatementAcceptanceService acceptances;
  private SelfStatementAcceptanceController controller;
  private Long storeId, personId, actorId;
  private String email, enrollmentId;

  @BeforeAll
  void connect() {
    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                IntegrationTestDatabase.springDataSourceProperties(
                    StatementAcceptanceIntegrationTest.class))
            .properties(
                Map.ofEntries(
                    Map.entry("spring.config.name", "statement-acceptance-test"),
                    Map.entry(
                        "spring.liquibase.change-log",
                        "classpath:db/changelog/db.changelog-master.yaml"),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                    Map.entry("spring.jpa.open-in-view", "false"),
                    Map.entry("logging.level.root", "WARN")))
            .run();
    jdbc = context.getBean(JdbcTemplate.class);
    em = context.getBean(EntityManager.class);
    transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    statements = context.getBean(RemunerationStatementService.class);
    acceptances = context.getBean(StatementAcceptanceService.class);
    controller = context.getBean(SelfStatementAcceptanceController.class);
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @BeforeEach
  void fixture() {
    transactions.executeWithoutResult(
        tx -> {
          var store =
              new Store("承諾検証店", StoreShortNames.next(), UUID.randomUUID() + ".example.test");
          em.persist(store);
          storeId = store.getId();
          email = UUID.randomUUID() + "@example.test";
          var actor =
              User.builder()
                  .email(email)
                  .password(UUID.randomUUID().toString())
                  .displayName("本人")
                  .userType(UserType.CAST)
                  .enabled(true)
                  .roleIds(Set.of())
                  .storeScopeType(StoreScopeType.SPECIFIC_STORES)
                  .storeIds(Set.of(storeId))
                  .build();
          em.persist(actor);
          actorId = actor.getId();
          var person = Cast.builder().userId(actor.getId()).build();
          em.persist(person);
          personId = person.getId();
          // 退店済みの在籍でも、その店舗の過去日は承諾できる。
          var enrollment =
              CastEnrollment.builder()
                  .castId(personId)
                  .status(CastEnrollmentStatus.WITHDRAWN)
                  .startedOn(LocalDate.of(2026, 1, 1))
                  .build();
          enrollment.setStoreId(storeId);
          em.persist(enrollment);
          enrollmentId = enrollment.getId();
          var profile = CastProfile.builder().enrollmentId(enrollmentId).name("源氏名").build();
          profile.setStoreId(storeId);
          em.persist(profile);
          var term = new GuaranteeTerm(personId, DAY, GuaranteeState.ACTIVE, 10000L, "日額");
          term.setStoreId(storeId);
          em.persist(term);
        });
    order(DAY, 7000);
    attendance(DAY, 5);
  }

  @Test
  void acceptanceAddsOneRowAndOneAuditAndBothStatementsShowIt() {
    var before = day(DAY);
    assertThat(before.acceptanceState()).isEqualTo(AcceptanceState.NOT_ACCEPTED);
    assertThat(before.latestAcceptance()).isNull();

    var result = accept(DAY, before.contentDigest());

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(result.getBody().orderAmount()).isEqualTo(7000);
    assertThat(result.getBody().guaranteeAmount()).isEqualTo(3000);
    assertThat(result.getBody().bonusAmount()).isZero();
    assertThat(rows()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select after_values::text from t_audit_events where action='STATEMENT_ACCEPTED' and target_id=?",
                String.class,
                result.getBody().id()))
        .isEqualTo(
            "{\"total\": \"10000\", \"bonus_amount\": \"0\", \"order_amount\": \"7000\", \"guarantee_amount\": \"3000\"}");
    var self =
        statements.self(email, storeId, "2026-09", PageRequest.of(0, 20), PageRequest.of(0, 20));
    var store =
        statements.store(
            storeId, personId, "2026-09", PageRequest.of(0, 20), PageRequest.of(0, 20));
    for (var statement : List.of(self, store)) {
      var day =
          statement.days().stream()
              .filter(d -> d.businessDate().equals(DAY))
              .findFirst()
              .orElseThrow();
      assertThat(day.acceptanceState()).isEqualTo(AcceptanceState.ACCEPTED);
      assertThat(day.latestAcceptance().id()).isEqualTo(result.getBody().id());
      assertThat(statement.acceptedDays()).isEqualTo(1);
      assertThat(statement.acceptableDays()).isEqualTo(1);
    }
  }

  @Test
  void resendingTheSameContentReturnsTheExistingRow() {
    String digest = day(DAY).contentDigest();
    var first = accept(DAY, digest);
    var second = accept(DAY, digest);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(second.getBody().id()).isEqualTo(first.getBody().id());
    assertThat(rows()).isEqualTo(1);
    assertThat(audits()).isEqualTo(1);
  }

  @Test
  void correctionShowsChangedAndReturningToAnAcceptedVersionIsAcceptedAgain() {
    String a = day(DAY).contentDigest();
    var first = accept(DAY, a);
    String bonus = bonus(DAY, 500);

    var changed = day(DAY);
    assertThat(changed.acceptanceState()).isEqualTo(AcceptanceState.CHANGED_AFTER_ACCEPTANCE);
    assertThat(changed.latestAcceptance().id()).isEqualTo(first.getBody().id());
    assertThat(changed.bonusAmount()).isEqualTo(500);
    // 古い要約での承諾要求は差し戻す。
    assertThatThrownBy(() -> acceptances.accept(email, command(DAY, a)))
        .isInstanceOf(ConflictException.class);

    var second = accept(DAY, changed.contentDigest());
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(rows()).isEqualTo(2);

    jdbc.update("update t_bonus_awards set cancelled_at=now() where id=?", bonus);
    var back = day(DAY);
    assertThat(back.contentDigest()).isEqualTo(a);
    assertThat(back.acceptanceState()).isEqualTo(AcceptanceState.ACCEPTED);
    assertThat(back.latestAcceptance().id()).isEqualTo(second.getBody().id());
    var again = accept(DAY, a);
    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(again.getBody().id()).isEqualTo(first.getBody().id());
    assertThat(rows()).isEqualTo(2);
  }

  @Test
  void daysWithoutDeterminedContentAreRefusedAndShownWithTheirReason() {
    order(TODAY, 5000);
    attendance(TODAY, 5);
    var open = attendanceOpen(DAY.minusDays(1));
    var september =
        statements.self(email, storeId, "2026-09", PageRequest.of(0, 20), PageRequest.of(0, 20));
    var pending =
        september.days().stream()
            .filter(d -> d.businessDate().equals(DAY.minusDays(1)))
            .findFirst()
            .orElseThrow();
    assertThat(pending.acceptanceState()).isEqualTo(AcceptanceState.NOT_ACCEPTABLE);
    assertThat(pending.notAcceptableReason()).isEqualTo(NotAcceptableReason.GUARANTEE_UNDETERMINED);
    assertThat(pending.contentDigest()).isNull();
    var zero = september.days().getFirst();
    assertThat(zero.notAcceptableReason()).isEqualTo(NotAcceptableReason.ALL_ZERO);
    var october =
        statements.self(email, storeId, "2026-10", PageRequest.of(0, 20), PageRequest.of(0, 20));
    var today =
        october.days().stream()
            .filter(d -> d.businessDate().equals(TODAY))
            .findFirst()
            .orElseThrow();
    assertThat(today.notAcceptableReason()).isEqualTo(NotAcceptableReason.BUSINESS_DAY_NOT_ENDED);

    for (var date : List.of(TODAY, DAY.minusDays(1), september.days().getFirst().businessDate()))
      assertThatThrownBy(() -> acceptances.accept(email, command(date, "0".repeat(64))))
          .isInstanceOf(ServiceException.class)
          .isNotInstanceOf(ConflictException.class);
    assertThat(open).isNotNull();
    assertThat(rows()).isZero();
  }

  @Test
  void aStoreWithoutHistoricalEnrollmentIsInvisible() {
    long other =
        jdbc.queryForObject(
            "insert into t_stores(name,short_name,domain) values ('他店',?,?) returning id",
            Long.class,
            StoreShortNames.next(),
            UUID.randomUUID() + ".example.test");
    assertThatThrownBy(
            () -> acceptances.accept(email, new AcceptanceCommand(other, DAY, "0".repeat(64))))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void concurrentSameContentKeepsOneRowAndTheLoserGetsIt() throws Exception {
    String digest = day(DAY).contentDigest();
    jdbc.execute(
        """
        CREATE OR REPLACE FUNCTION test_hold_statement_acceptance() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN PERFORM pg_advisory_xact_lock(1344); RETURN NEW; END; $$
        """);
    jdbc.execute(
        "CREATE TRIGGER tr_test_hold BEFORE INSERT ON t_statement_acceptances FOR EACH ROW EXECUTE FUNCTION test_hold_statement_acceptance()");
    var gate = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    try (var pool = Executors.newFixedThreadPool(2)) {
      var results =
          gate.execute(
              tx -> {
                jdbc.queryForObject("select pg_advisory_xact_lock(1344)::text", String.class);
                var first = pool.submit(() -> accept(DAY, digest));
                var second = pool.submit(() -> accept(DAY, digest));
                // pg_stat_activity は取引内で写しが固定されるので、毎回捨ててから数える。
                await(
                    () ->
                        jdbc.queryForObject(
                                "select count(*) from pg_stat_activity, pg_stat_clear_snapshot() where datname=current_database() and wait_event='advisory'",
                                Integer.class)
                            == 2);
                return List.of(first, second);
              });
      var statuses =
          List.of(
              results.get(0).get(10, TimeUnit.SECONDS), results.get(1).get(10, TimeUnit.SECONDS));
      assertThat(statuses)
          .extracting(r -> r.getStatusCode())
          .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.OK);
      assertThat(statuses.get(0).getBody().id()).isEqualTo(statuses.get(1).getBody().id());
    } finally {
      jdbc.execute("DROP TRIGGER tr_test_hold ON t_statement_acceptances");
      jdbc.execute("DROP FUNCTION test_hold_statement_acceptance()");
    }
    assertThat(rows()).isEqualTo(1);
    assertThat(audits()).isEqualTo(1);
  }

  @Test
  void theDatabaseRefusesToRewriteOrDeleteAnAcceptance() {
    String id = accept(DAY, day(DAY).contentDigest()).getBody().id();
    assertThatThrownBy(
            () -> jdbc.update("update t_statement_acceptances set order_amount=0 where id=?", id))
        .hasMessageContaining("明細の承諾は変更できません");
    assertThatThrownBy(() -> jdbc.update("delete from t_statement_acceptances where id=?", id))
        .hasMessageContaining("明細の承諾は変更できません");
    assertThat(rows()).isEqualTo(1);
  }

  private ResponseEntity<StatementAcceptanceResponse> accept(LocalDate date, String digest) {
    Principal principal = () -> email;
    return controller.accept(principal, new StatementAcceptanceRequest(storeId, date, digest));
  }

  private AcceptanceCommand command(LocalDate date, String digest) {
    return new AcceptanceCommand(storeId, date, digest);
  }

  private Day day(LocalDate date) {
    return statements
        .self(
            email,
            storeId,
            date.toString().substring(0, 7),
            PageRequest.of(0, 20),
            PageRequest.of(0, 20))
        .days()
        .stream()
        .filter(d -> d.businessDate().equals(date))
        .findFirst()
        .orElseThrow();
  }

  private int rows() {
    return jdbc.queryForObject(
        "select count(*) from t_statement_acceptances where store_id=?", Integer.class, storeId);
  }

  private int audits() {
    return jdbc.queryForObject(
        "select count(*) from t_audit_events where originating_store_id=? and action='STATEMENT_ACCEPTED'",
        Integer.class,
        storeId);
  }

  private void order(LocalDate day, int amount) {
    String service = UUID.randomUUID().toString(), revision = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_services(id,store_id,kind,name,duration_minutes,price,remuneration,revision_number) values (?,?,'COURSE','検証コース',60,15000,?,1)",
        service,
        storeId,
        amount);
    jdbc.update(
        "insert into t_service_revisions(id,store_id,service_id,revision_number,operation,actor_id,occurred_at,after_kind,after_name,after_duration_minutes,after_price,after_remuneration) values (?,?,?,1,'CREATED',?,current_timestamp,'COURSE','検証コース',60,15000,?)",
        revision,
        storeId,
        service,
        actorId,
        amount);
    transactions.executeWithoutResult(
        tx -> {
          var order =
              Order.builder()
                  .course(
                      new OrderCourse(
                          service,
                          revision,
                          1L,
                          "検証コース",
                          60,
                          15000,
                          amount,
                          "CURRENT_SETTING",
                          OffsetDateTime.parse("2026-09-30T10:00:00+09:00")))
                  .castId(enrollmentId)
                  .businessDate(day)
                  .status(OrderStatus.COMPLETED)
                  .accruedRemuneration(amount)
                  .completionInvalidated(false)
                  .completedAt(OffsetDateTime.parse("2026-10-01T01:00:00+09:00"))
                  .build();
          order.setStoreId(storeId);
          em.persist(order);
        });
  }

  private void attendance(LocalDate day, int hours) {
    transactions.executeWithoutResult(
        tx ->
            em.persist(
                Attendance.record(
                    storeId,
                    enrollmentId,
                    null,
                    day,
                    day.atTime(18, 0),
                    day.atTime(18, 0).plusHours(hours),
                    null,
                    actorId)));
  }

  private String attendanceOpen(LocalDate day) {
    return transactions.execute(
        tx -> {
          var a =
              Attendance.record(
                  storeId, enrollmentId, null, day, day.atTime(18, 0), null, null, actorId);
          em.persist(a);
          return a.getId();
        });
  }

  private String bonus(LocalDate day, long amount) {
    return transactions.execute(
        tx -> {
          var b =
              BonusAward.builder().personId(personId).date(day).amount(amount).reason("指名").build();
          b.setStoreId(storeId);
          em.persist(b);
          return b.getId();
        });
  }

  private static void await(BooleanSupplier condition) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) throw new IllegalStateException("待機が完了しません");
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
  }

  @Configuration
  @EnableAutoConfiguration
  @EntityScan("com.kizuna")
  @EnableJpaRepositories(
      basePackageClasses = {
        AuditEventRepository.class,
        StatementAcceptanceRepository.class,
        UserRepository.class,
        CastRepository.class
      })
  @Import({
    SelfStatementAcceptanceController.class,
    StatementAcceptanceService.class,
    RemunerationStatementService.class,
    StatementContents.class,
    RemunerationRecords.class,
    RemunerationPersonLookup.class,
    OrderRemunerationFacts.class,
    RemunerationQuery.class,
    AttendanceFacts.class,
    BusinessDateService.class,
    ActorIdentityService.class,
    BusinessAudit.class,
    AuditWriter.class
  })
  static class Config {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC);
    }

    @Bean
    SystemConfigService settings() {
      var settings = mock(SystemConfigService.class);
      when(settings.getConfigValue(anyString())).thenReturn(Optional.empty());
      return settings;
    }
  }
}
