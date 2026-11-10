package com.kizuna.pointsexpiry.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.customer.contact.BusinessContactRestrictions;
import com.kizuna.customer.contact.GuestContactImports;
import com.kizuna.customer.contact.MarketingContacts;
import com.kizuna.member.application.EmailVerificationConfirmation;
import com.kizuna.member.application.EmailVerificationMail;
import com.kizuna.member.application.MemberDisplayNames;
import com.kizuna.member.application.MemberLoginAddress;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberRegistrationCommand;
import com.kizuna.member.application.MemberRegistrationService;
import com.kizuna.membernotification.application.MemberNotificationSettingService;
import com.kizuna.membernotification.contact.MemberContacts;
import com.kizuna.notification.transport.ContactTransport;
import com.kizuna.notificationdelivery.application.NotificationService;
import com.kizuna.notificationdelivery.intake.MemberNotices;
import com.kizuna.order.application.BusinessContactPermissions;
import com.kizuna.order.application.GuestApplicationConsent;
import com.kizuna.order.contact.BusinessContactPolicy;
import com.kizuna.order.contact.OrderApplicationBusinessContact;
import com.kizuna.order.contact.OrderBusinessContact;
import com.kizuna.order.contact.SourceCustomers;
import com.kizuna.point.application.PointExpiryLedger;
import com.kizuna.point.domain.PointAllocation;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.settings.application.SystemConfigServiceImpl;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.task.application.TaskLifecycle;
import com.kizuna.task.application.TaskOptions;
import com.kizuna.task.application.TaskRegistry;
import com.kizuna.task.execution.TaskCommand;
import com.kizuna.task.execution.TaskExecutor;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 実際の台帳・システム設定・送信正本・送信の定期処理を通し、案内の起票から送信の判定までを検証する。
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PointExpiryNoticeIntegrationTest {
  private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
  private static final int DAYS = 5;
  private static final Pattern LINK = Pattern.compile("/auth/verify-email#([A-Za-z0-9_-]{43})");
  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private TransactionTemplate tx;
  private FakeTransport transport;
  private LocalDate today;
  private LocalDate target;
  private String suffix;
  private long noticeService;
  private long deliverService;
  private long noticeRole;
  private AuditActor operator;

  @BeforeAll
  void connect() {
    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                IntegrationTestDatabase.springDataSourceProperties(
                    PointExpiryNoticeIntegrationTest.class))
            .properties(
                Map.ofEntries(
                    Map.entry("spring.config.name", "point-expiry-notice-test"),
                    Map.entry(
                        "spring.liquibase.change-log",
                        "classpath:db/changelog/db.changelog-master.yaml"),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                    Map.entry("spring.jpa.open-in-view", "false"),
                    Map.entry(
                        "spring.modulith.events.republish-outstanding-events-on-restart", "false"),
                    Map.entry("logging.level.root", "WARN")))
            .run();
    jdbc = context.getBean(JdbcTemplate.class);
    tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    transport = context.getBean(FakeTransport.class);
    long executeRole = role("TASK_EXECUTE");
    noticeRole = role("POINT_EXPIRE");
    noticeService = service("案内処理", executeRole, noticeRole);
    deliverService = service("通知送信", executeRole, role("NOTIFICATION_DELIVER"));
    operator =
        new AuditActor(
            jdbc.queryForObject(
                "insert into t_users(email,display_name,user_type) values (?,'検証担当','STAFF') returning id",
                Long.class,
                UUID.randomUUID() + "@example.test"),
            "STAFF",
            "検証担当");
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @BeforeEach
  void prepare() {
    jdbc.update("delete from t_notification_attempts");
    jdbc.update("delete from t_notification_deliveries");
    jdbc.update("delete from t_point_usage_allocations");
    jdbc.update("delete from t_point_entries");
    jdbc.update(
        "update t_system_configs set config_value=? where config_key='point_expiry_notice_days'",
        Integer.toString(DAYS));
    jdbc.update(
        "insert into t_user_roles(user_id,role_id) values (?,?) on conflict do nothing",
        noticeService,
        noticeRole);
    jdbc.update(
        "insert into t_user_roles(user_id,role_id) values (?,?) on conflict do nothing",
        operator.id(),
        noticeRole);
    transport.results.clear();
    transport.bodies.clear();
    suffix = UUID.randomUUID().toString().substring(0, 8);
    today = LocalDate.now(ZONE);
    target = today.plusDays(DAYS);
  }

  @Test
  void issuesOneNoticePerMemberAndExpiryDayOnlyForTheTargetDayAndNeverTwice() {
    long allowed = member("allowed", "ALLOWED");
    credit(allowed, 100, target);
    credit(allowed, 50, target);
    credit(allowed, 30, target.plusDays(1));
    credit(allowed, 70, null);
    long unconfirmed = member("unconfirmed", null);
    credit(unconfirmed, 40, target);
    long earlier = member("earlier", "ALLOWED");
    credit(earlier, 60, target.minusDays(1));
    long consumed = member("consumed", "ALLOWED");
    long spent = credit(consumed, 80, target);
    consume(consumed, spent, 80);
    long partly = member("partly", "ALLOWED");
    long half = credit(partly, 90, target);
    consume(partly, half, 25);
    var ledger = ledger();

    var run = noticeRun();
    assertThat(run.execution().status()).isEqualTo("SUCCEEDED");
    assertThat(run.execution().processedCount()).isEqualTo(3);
    assertThat(
            jdbc.queryForList(
                "select source_id from t_notification_deliveries where attributed_store_id is null and source_type='MEMBER' and purpose='BUSINESS' and channel='EMAIL' and status='QUEUED' order by source_id",
                String.class))
        .containsExactlyInAnyOrder(
            Long.toString(allowed), Long.toString(unconfirmed), Long.toString(partly));
    assertThat(
            jdbc.queryForObject(
                "select dedupe_key from t_notification_deliveries where source_id=?",
                String.class,
                Long.toString(allowed)))
        .isEqualTo("point-expiry-notice:" + allowed + ":" + target);
    assertThat(body(allowed)).contains("allowed 様", "150 ポイント");
    assertThat(body(partly)).contains("65 ポイント");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_audit_events where action='NOTIFICATION_CREATED' and actor_id=? and target_type='NOTIFICATION_DELIVERY'",
                Long.class,
                noticeService))
        .isEqualTo(3);

    var replay = noticeRun();
    assertThat(replay.execution().status()).isEqualTo("SUCCEEDED");
    assertThat(replay.execution().processedCount()).isZero();
    assertThat(jdbc.queryForObject("select count(*) from t_notification_deliveries", Long.class))
        .isEqualTo(3);
    assertThat(ledger()).isEqualTo(ledger);
  }

  @Test
  void deliveryTaskSendsOnlyToMembersWhoAllowedBusinessContact() {
    long allowed = member("allowed", "ALLOWED");
    credit(allowed, 100, target);
    long unconfirmed = member("unconfirmed", null);
    credit(unconfirmed, 100, target);
    long denied = member("denied", "DENIED");
    credit(denied, 100, target);
    assertThat(noticeRun().execution().processedCount()).isEqualTo(3);

    var deliver =
        new TaskCommand(
            "NOTIFICATION_DELIVER", UUID.randomUUID().toString(), deliverService, today, today);
    assertThat(context.getBean(TaskExecutor.class).executeScheduled(deliver).execution().status())
        .isEqualTo("SUCCEEDED");
    context.getBean(DeferredEvents.class).drain();

    assertThat(status(allowed)).isEqualTo("SENT");
    assertThat(status(unconfirmed)).isEqualTo("BLOCKED");
    assertThat(status(denied)).isEqualTo("BLOCKED");
    assertThat(lastFailure(unconfirmed)).isEqualTo("CONTACT_NOT_ALLOWED");
    assertThat(transport.results).containsExactly(address("allowed"));
  }

  @Test
  void registeredMemberWhoConfirmedTheVerificationMailReceivesTheNoticeWithoutChoosing() {
    long verified = registered("verified", true);
    credit(verified, 100, target);
    long unverified = registered("unverified", false);
    credit(unverified, 100, target);
    long refused = registered("refused", true);
    jdbc.update(
        "insert into t_member_notification_settings(member_id,purpose,status) values (?,'BUSINESS','DENIED')",
        refused);
    credit(refused, 100, target);
    transport.results.clear();
    assertThat(noticeRun().execution().processedCount()).isEqualTo(3);

    var deliver =
        new TaskCommand(
            "NOTIFICATION_DELIVER", UUID.randomUUID().toString(), deliverService, today, today);
    assertThat(context.getBean(TaskExecutor.class).executeScheduled(deliver).execution().status())
        .isEqualTo("SUCCEEDED");
    context.getBean(DeferredEvents.class).drain();

    assertThat(status(verified)).isEqualTo("SENT");
    assertThat(status(unverified)).isEqualTo("BLOCKED");
    assertThat(lastFailure(unverified)).isEqualTo("CONTACT_NOT_ALLOWED");
    assertThat(status(refused)).isEqualTo("BLOCKED");
    assertThat(lastFailure(refused)).isEqualTo("CONTACT_NOT_ALLOWED");
    assertThat(transport.results).containsExactly(address("verified"));
  }

  @Test
  void onlyServicesAndStaffHoldingPointExpireCanRunIt() {
    long member = member("allowed", "ALLOWED");
    credit(member, 100, target);
    var executor = context.getBean(TaskExecutor.class);
    var options = context.getBean(TaskOptions.class);
    assertThat(context.getBean(TaskRegistry.class).requireName(PointExpiryNoticeTask.NAME))
        .isInstanceOf(PointExpiryNoticeTask.class);
    assertThat(
            options
                .candidates(PointExpiryNoticeTask.NAME, PageRequest.of(0, 100))
                .map(candidate -> candidate.id())
                .getContent())
        .contains(noticeService)
        .doesNotContain(deliverService);

    assertThatThrownBy(() -> executor.executeScheduled(command(deliverService)))
        .isInstanceOf(AccessDeniedException.class);
    jdbc.update(
        "delete from t_user_roles where user_id=? and role_id=?", noticeService, noticeRole);
    assertThatThrownBy(() -> executor.executeScheduled(command(noticeService)))
        .isInstanceOf(AccessDeniedException.class);
    jdbc.update(
        "insert into t_user_roles(user_id,role_id) values (?,?)", noticeService, noticeRole);

    jdbc.update(
        "delete from t_user_roles where user_id=? and role_id=?", operator.id(), noticeRole);
    assertThatThrownBy(() -> executor.execute(command(noticeService), operator))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(jdbc.queryForObject("select count(*) from t_notification_deliveries", Long.class))
        .isZero();
    jdbc.update(
        "insert into t_user_roles(user_id,role_id) values (?,?)", operator.id(), noticeRole);
    assertThat(executor.execute(command(noticeService), operator).execution().status())
        .isEqualTo("SUCCEEDED");
    assertThat(jdbc.queryForObject("select count(*) from t_notification_deliveries", Long.class))
        .isEqualTo(1);
  }

  private TaskExecutor.Submission noticeRun() {
    return context.getBean(TaskExecutor.class).executeScheduled(command(noticeService));
  }

  private TaskCommand command(long service) {
    return new TaskCommand(
        PointExpiryNoticeTask.NAME, UUID.randomUUID().toString(), service, today, today);
  }

  private String body(long member) {
    return jdbc.queryForObject(
        "select body from t_notification_deliveries where source_id=?",
        String.class,
        Long.toString(member));
  }

  private String status(long member) {
    return jdbc.queryForObject(
        "select status from t_notification_deliveries where source_id=?",
        String.class,
        Long.toString(member));
  }

  private String lastFailure(long member) {
    return jdbc.queryForObject(
        "select a.failure_code from t_notification_attempts a join t_notification_deliveries d on d.id=a.delivery_id where d.source_id=? order by a.id desc limit 1",
        String.class,
        Long.toString(member));
  }

  private List<Map<String, Object>> ledger() {
    return jdbc.queryForList(
        "select e.id, e.entry_type, e.amount, e.expires_on, (select coalesce(sum(a.amount),0) from t_point_usage_allocations a where a.entry_id=e.id) as allocated from t_point_entries e order by e.id");
  }

  private String address(String name) {
    return name + "." + suffix + "@example.invalid";
  }

  private long member(String name, String business) {
    long user =
        jdbc.queryForObject(
            "insert into t_users(display_name,user_type,email,password) values (?,'MEMBER',?,'x') returning id",
            Long.class,
            name,
            address(name));
    long member =
        jdbc.queryForObject(
            "insert into t_members(member_code,user_id) values (?,?) returning id",
            Long.class,
            UUID.randomUUID().toString().substring(0, 20),
            user);
    if (business != null)
      jdbc.update(
          "insert into t_member_notification_settings(member_id,purpose,status) values (?,'BUSINESS',?)",
          member,
          business);
    return member;
  }

  private long registered(String name, boolean confirm) {
    String email = address(name);
    context
        .getBean(MemberRegistrationService.class)
        .register(new MemberRegistrationCommand(email, "pass12345", name));
    if (confirm) {
      Matcher link = LINK.matcher(transport.bodies.getLast());
      assertThat(link.find()).isTrue();
      context.getBean(EmailVerificationConfirmation.class).confirm(link.group(1));
    }
    return jdbc.queryForObject(
        "select m.id from t_members m join t_users u on u.id=m.user_id where u.email=?",
        Long.class,
        email);
  }

  private long credit(long member, int amount, LocalDate expiry) {
    return tx.execute(
        status ->
            context
                .getBean(PointEntryRepository.class)
                .saveAndFlush(
                    PointEntry.manualAdjust(
                        member,
                        null,
                        amount,
                        "検証付与",
                        expiry,
                        List.of(),
                        operator.id(),
                        UUID.randomUUID().toString()))
                .getId());
  }

  private void consume(long member, long lot, int amount) {
    tx.executeWithoutResult(
        status ->
            context
                .getBean(PointEntryRepository.class)
                .saveAndFlush(
                    PointEntry.manualAdjust(
                        member,
                        null,
                        -amount,
                        "検証消費",
                        null,
                        List.of(PointAllocation.of(lot, amount)),
                        operator.id(),
                        UUID.randomUUID().toString())));
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

  private long service(String name, long... roles) {
    long id =
        jdbc.queryForObject(
            "insert into t_users(display_name,user_type) values (?,'SERVICE') returning id",
            Long.class,
            name);
    for (long role : roles)
      jdbc.update("insert into t_user_roles(user_id,role_id) values (?,?)", id, role);
    return id;
  }

  static class DeferredEvents implements Executor {
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    public void execute(Runnable work) {
      tasks.add(work);
    }

    void drain() {
      Runnable next;
      while ((next = tasks.poll()) != null) next.run();
    }
  }

  static class FakeTransport implements ContactTransport {
    final List<String> results = new CopyOnWriteArrayList<>();
    final List<String> bodies = new CopyOnWriteArrayList<>();

    public Channel type() {
      return Channel.EMAIL;
    }

    public boolean available() {
      return true;
    }

    public Result deliver(String recipient, String subject, String body) {
      results.add(recipient);
      bodies.add(body);
      return Result.SENT;
    }
  }

  @Configuration
  @EnableAutoConfiguration
  @EnableAsync
  @EntityScan("com.kizuna")
  @EnableJpaRepositories("com.kizuna")
  @ComponentScan(basePackageClasses = NotificationService.class)
  @Import({
    PointExpiryNoticeTask.class,
    MemberNotices.class,
    PointExpiryLedger.class,
    MemberDisplayNames.class,
    SystemConfigServiceImpl.class,
    AuditWriter.class,
    TaskExecutor.class,
    TaskLifecycle.class,
    TaskRegistry.class,
    TaskOptions.class,
    ServiceExecutionIdentityService.class,
    ActorIdentityService.class,
    OrderBusinessContact.class,
    OrderApplicationBusinessContact.class,
    BusinessContactPermissions.class,
    GuestApplicationConsent.class,
    BusinessContactPolicy.class,
    BusinessContactRestrictions.class,
    GuestContactImports.class,
    SourceCustomers.class,
    MarketingContacts.class,
    MemberContacts.class,
    MemberLoginAddress.class,
    MemberRegistrationService.class,
    EmailVerificationMail.class,
    EmailVerificationConfirmation.class,
    MemberNotificationSettingService.class,
    MemberLookupService.class,
    BusinessAudit.class
  })
  static class Config {
    @Bean
    DeferredEvents taskExecutor() {
      return new DeferredEvents();
    }

    @Bean
    Clock clock() {
      return Clock.system(ZONE);
    }

    @Bean
    AppProperties properties() {
      return new AppProperties();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder(4);
    }

    @Bean
    FakeTransport transport() {
      return new FakeTransport();
    }
  }
}
