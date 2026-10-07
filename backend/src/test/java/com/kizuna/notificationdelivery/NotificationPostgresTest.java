package com.kizuna.notificationdelivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.customer.contact.BusinessContactRestrictions;
import com.kizuna.customer.contact.GuestContactImports;
import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactPurpose;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerContact;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.notification.transport.EmailTransport;
import com.kizuna.notificationdelivery.api.dto.DeliveryResponse;
import com.kizuna.notificationdelivery.application.DeliveryAttempts;
import com.kizuna.notificationdelivery.application.DeliveryDispatch;
import com.kizuna.notificationdelivery.application.NotificationService;
import com.kizuna.notificationdelivery.domain.DeliveryContent;
import com.kizuna.order.application.BusinessContactPermissions;
import com.kizuna.order.application.GuestApplicationConsent;
import com.kizuna.order.contact.BusinessContactPolicy;
import com.kizuna.order.contact.OrderApplicationBusinessContact;
import com.kizuna.order.contact.OrderBusinessContact;
import com.kizuna.order.domain.BusinessContactHistory;
import com.kizuna.order.domain.BusinessContactHistoryRepository;
import com.kizuna.order.domain.BusinessContactState;
import com.kizuna.order.domain.ContactSnapshot;
import com.kizuna.order.domain.GuestContactConsent;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderApplication;
import com.kizuna.order.domain.OrderApplicationRepository;
import com.kizuna.order.domain.OrderApplicationStatus;
import com.kizuna.order.domain.OrderCourse;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.service.domain.ServiceItem;
import com.kizuna.service.domain.ServiceItemRepository;
import com.kizuna.service.domain.ServiceKind;
import com.kizuna.service.domain.ServiceRevision;
import com.kizuna.service.domain.ServiceRevisionRepository;
import com.kizuna.service.domain.ServiceTerms;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.StoreScopeStampListener;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreFilterEnable;
import com.kizuna.task.application.ServiceIdentityCheckTask;
import com.kizuna.task.application.TaskLifecycle;
import com.kizuna.task.application.TaskOptions;
import com.kizuna.task.application.TaskRegistry;
import com.kizuna.task.execution.TaskCommand;
import com.kizuna.task.execution.TaskExecutor;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.ServiceExecutionIdentityService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "KIZUNA_NOTIFICATION_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationPostgresTest {
  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private StoreContext store;
  private NotificationService notifications;
  private TransactionTemplate tx;
  private FakeTransport transport;
  private Long storeId, otherStore, operatorId, serviceId, serviceRole;
  private String email;

  @BeforeAll
  void connect() {
    String schema = "notification_" + UUID.randomUUID().toString().replace("-", "");
    var source =
        new DriverManagerDataSource(
            System.getenv("KIZUNA_NOTIFICATION_TEST_JDBC_URL"), "postgres", "");
    new JdbcTemplate(source).execute("create schema " + schema);
    String hash = new BCryptPasswordEncoder(4).encode(UUID.randomUUID().toString());

    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                Map.ofEntries(
                    Map.entry("spring.config.name", "notification-test"),
                    Map.entry(
                        "spring.datasource.url",
                        System.getenv("KIZUNA_NOTIFICATION_TEST_JDBC_URL")
                            + "?currentSchema="
                            + schema),
                    Map.entry("spring.datasource.username", "postgres"),
                    Map.entry(
                        "spring.liquibase.change-log",
                        "classpath:db/changelog/db.changelog-master.yaml"),
                    Map.entry("spring.liquibase.contexts", "production"),
                    Map.entry("spring.liquibase.parameters.initialAdminPasswordHash", hash),
                    Map.entry("spring.liquibase.parameters.demoUserPasswordHash", hash),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                    Map.entry("spring.jpa.open-in-view", "false"),
                    Map.entry(
                        "spring.modulith.events.republish-outstanding-events-on-restart", "false"),
                    Map.entry("logging.level.root", "WARN")))
            .run();
    jdbc = context.getBean(JdbcTemplate.class);
    store = context.getBean(StoreContext.class);
    notifications = context.getBean(NotificationService.class);
    tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    transport = context.getBean(FakeTransport.class);
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @BeforeEach
  void fixtures() {
    transport.results.clear();
    transport.outcome = EmailTransport.Result.SENT;
    transport.crash = false;
    transport.afterSend = () -> {};
    String suffix = UUID.randomUUID().toString();
    email = suffix + "@example.invalid";
    storeId =
        jdbc.queryForObject(
            "insert into t_stores(name,domain) values (?,?) returning id",
            Long.class,
            "通知検証",
            suffix + ".invalid");
    otherStore =
        jdbc.queryForObject(
            "insert into t_stores(name,domain) values (?,?) returning id",
            Long.class,
            "別店舗",
            suffix + "-other.invalid");
    operatorId =
        jdbc.queryForObject(
            "insert into t_users(email,display_name,user_type,store_scope_type) values (?,'通知担当','STAFF','ALL_STORES') returning id",
            Long.class,
            email);
    serviceId =
        jdbc.queryForObject(
            "insert into t_users(display_name,user_type,store_scope_type) values ('通知SERVICE','SERVICE','ALL_STORES') returning id",
            Long.class);
    Long role =
        jdbc.queryForObject(
            "insert into t_roles(name,is_system) values (?,false) returning id",
            Long.class,
            "通知担当" + suffix);
    serviceRole =
        jdbc.queryForObject(
            "insert into t_roles(name,is_system) values (?,false) returning id",
            Long.class,
            "通知実行" + suffix);
    jdbc.update(
        "insert into t_role_permissions(role_id,permission_id) select ?,id from t_permissions where code in ('NOTIFICATION_VIEW','NOTIFICATION_MANAGE','NOTIFICATION_SEND')",
        role);
    jdbc.update(
        "insert into t_role_permissions(role_id,permission_id) select ?,id from t_permissions where code in ('TASK_EXECUTE','NOTIFICATION_DELIVER')",
        serviceRole);
    jdbc.update(
        "insert into t_user_roles(platform_user_id,role_id) values (?,?),(?,?)",
        operatorId,
        role,
        serviceId,
        serviceRole);
  }

  private <T> T inStore(Long id, Supplier<T> work) {
    store.setStoreId(id);
    try {
      return work.get();
    } finally {
      store.clear();
    }
  }

  private String guest() {
    return inStore(
        storeId,
        () ->
            tx.execute(
                status -> {
                  var row =
                      OrderApplication.builder()
                          .status(OrderApplicationStatus.PENDING)
                          .businessDate(LocalDate.now())
                          .contactEmail("permitted@example.invalid")
                          .consentContact(
                              new ContactSnapshot(null, null, "permitted@example.invalid", null))
                          .contactConsent(
                              new GuestContactConsent(
                                  "v1", "業務連絡に同意", "販促同意", true, false, OffsetDateTime.now()))
                          .build();
                  return context
                      .getBean(OrderApplicationRepository.class)
                      .saveAndFlush(row)
                      .getId();
                }));
  }

  private DeliveryContent content(String source) {
    return new DeliveryContent(
        DeliveryContent.SourceType.APPLICATION,
        source,
        "EMAIL",
        "BUSINESS",
        "受付確認",
        "機密な本文",
        OffsetDateTime.now().minusMinutes(1),
        UUID.randomUUID().toString());
  }

  private DeliveryResponse draft() {
    var source = guest();
    return inStore(storeId, () -> notifications.create(email, content(source)).delivery());
  }

  private DeliveryResponse queue(DeliveryResponse row) {
    return inStore(
        storeId, () -> notifications.queue(email, row.id(), row.version(), "内容確認済み", false));
  }

  private void submitTask() {
    var command =
        new TaskCommand(
            "NOTIFICATION_DELIVER",
            UUID.randomUUID().toString(),
            serviceId,
            storeId,
            LocalDate.now(),
            LocalDate.now());
    assertThat(context.getBean(TaskExecutor.class).executeScheduled(command).execution().status())
        .isEqualTo("SUCCEEDED");
  }

  private void drain() {
    context.getBean(DeferredEvents.class).drain();
  }

  private void runTask() {
    submitTask();
    drain();
  }

  private DeliveryResponse get(String id) {
    return inStore(storeId, () -> notifications.get(email, id));
  }

  private DeliveryResponse await(String id, String status) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
    DeliveryResponse row;
    do {
      row = get(id);
      if (row.status().equals(status)) return row;
      Thread.sleep(20);
    } while (System.nanoTime() < deadline);
    assertThat(row.status()).isEqualTo(status);
    return row;
  }

  @Test
  void guestConsentFlowsThroughRealTaskAuditAndTransportOutsideTransaction() throws Exception {
    var row = queue(draft());
    runTask();
    await(row.id(), "SENT");
    assertThat(transport.results).containsExactly("permitted@example.invalid");
    var history = inStore(storeId, () -> notifications.history(email, row.id(), null, 10));
    assertThat(history.content()).hasSize(1);
    assertThat(history.content().getFirst().status()).isEqualTo("SENT");
    assertThat(
            jdbc.queryForObject(
                "select after_values::text from t_audit_events where target_id=? and action='NOTIFICATION_RESULT'",
                String.class,
                row.id()))
        .doesNotContain("example.invalid", "機密な本文");
    assertThat(
            jdbc.queryForList(
                "select actor_name from t_audit_events where target_id=? and action in ('NOTIFICATION_SENDING','NOTIFICATION_RESULT')",
                String.class,
                row.id()))
        .containsOnly(
            jdbc.queryForObject(
                "select display_name from t_users where id=?", String.class, serviceId));
    var sent = get(row.id());
    assertThatThrownBy(
            () ->
                inStore(
                    storeId,
                    () -> notifications.queue(email, row.id(), sent.version(), "再送", true)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void storeDenialAfterQueueBlocksAndExplicitRetryRechecks() throws Exception {
    var row = queue(draft());
    var contact =
        inStore(
            storeId,
            () ->
                tx.execute(
                    status -> {
                      var customer =
                          context
                              .getBean(CustomerRepository.class)
                              .saveAndFlush(Customer.builder().name("検証顧客").build());
                      var c =
                          CustomerContact.create(
                              customer.getId(), ContactType.EMAIL, "permitted@example.invalid");
                      c.changePermission(ContactPurpose.BUSINESS, ContactPermissionStatus.DENIED);
                      return context
                          .getBean(CustomerContactRepository.class)
                          .saveAndFlush(c)
                          .getId();
                    }));
    runTask();
    var blocked = await(row.id(), "BLOCKED");
    assertThat(transport.results).isEmpty();
    jdbc.update("update t_customer_contacts set business_status='ALLOWED' where id=?", contact);
    inStore(storeId, () -> notifications.queue(email, row.id(), blocked.version(), "同意再確認", true));
    runTask();
    await(row.id(), "SENT");
    assertThat(transport.results).hasSize(1);
    assertThat(inStore(storeId, () -> notifications.history(email, row.id(), null, 10)).content())
        .hasSize(2);
  }

  @Test
  void unavailableAndUnknownAreNotSentAndUnknownCannotRetry() throws Exception {
    transport.outcome = EmailTransport.Result.UNAVAILABLE;
    var row = queue(draft());
    runTask();
    await(row.id(), "FAILED");
    assertThat(
            inStore(storeId, () -> notifications.history(email, row.id(), null, 10))
                .content()
                .getFirst()
                .failureCode())
        .isEqualTo("UNAVAILABLE");
    transport.outcome = EmailTransport.Result.UNKNOWN;
    var next = queue(draft());
    runTask();
    var unknown = await(next.id(), "UNKNOWN");
    assertThatThrownBy(
            () ->
                inStore(
                    storeId,
                    () -> notifications.queue(email, next.id(), unknown.version(), "再送", true)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void interruptedListenerReplaysToUnknownWithoutSecondSend() throws Exception {
    transport.crash = true;
    var row = queue(draft());
    runTask();
    await(row.id(), "SENDING");
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (transport.results.isEmpty() && System.nanoTime() < deadline) Thread.sleep(20);
    transport.crash = false;
    context
        .getBean(IncompleteEventPublications.class)
        .resubmitIncompletePublicationsOlderThan(Duration.ZERO);
    drain();
    await(row.id(), "UNKNOWN");
    assertThat(transport.results).hasSize(1);
  }

  @Test
  void serviceRevokedAfterIoStillRecordsSentButBlocksNextDelivery() throws Exception {
    var first = queue(draft());
    var second = queue(draft());
    transport.afterSend =
        () ->
            jdbc.update(
                "update t_users set enabled=false,display_name='停止済み' where id=?", serviceId);
    submitTask();
    jdbc.update("update t_users set display_name='送信前SERVICE' where id=?", serviceId);
    drain();
    await(first.id(), "SENT");
    await(second.id(), "BLOCKED");
    assertThat(transport.results).hasSize(1);
    assertThat(
            jdbc.queryForList(
                "select actor_name from t_audit_events where target_id=? and action='NOTIFICATION_RESULT'",
                String.class,
                first.id()))
        .containsExactly("送信前SERVICE");
    var sent = get(first.id());
    assertThatThrownBy(
            () ->
                inStore(
                    storeId,
                    () -> notifications.queue(email, first.id(), sent.version(), "再送", true)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void failedResultCommitReplaysAsUnknownWithoutRepeatingExternalIo() throws Exception {
    var row = queue(draft());
    jdbc.execute(
        "alter table t_notification_deliveries add constraint test_result_failure check (status <> 'SENT') not valid");
    try {
      runTask();
      await(row.id(), "SENDING");
      assertThat(transport.results).hasSize(1);
    } finally {
      jdbc.execute("alter table t_notification_deliveries drop constraint test_result_failure");
    }
    context
        .getBean(IncompleteEventPublications.class)
        .resubmitIncompletePublicationsOlderThan(Duration.ZERO);
    drain();
    var unknown = await(row.id(), "UNKNOWN");
    assertThat(transport.results).hasSize(1);
    assertThatThrownBy(
            () ->
                inStore(
                    storeId,
                    () -> notifications.queue(email, row.id(), unknown.version(), "結果を再確認", true)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void otherStoreAndDuplicateActionsAreRejected() {
    var row = draft();
    assertThatThrownBy(() -> inStore(otherStore, () -> notifications.get(email, row.id())))
        .isInstanceOf(NotFoundException.class);
    queue(row);
    assertThatThrownBy(() -> queue(row)).isInstanceOf(ConflictException.class);
    String source = guest();
    var input = content(source);
    var a = inStore(storeId, () -> notifications.create(email, input));
    var b = inStore(storeId, () -> notifications.create(email, input));
    assertThat(a.delivery().id()).isEqualTo(b.delivery().id());
    assertThat(b.created()).isFalse();
  }

  @Test
  void rolledBackDispatchPublishesNoVisibleEventOrAttempt() {
    var row = queue(draft());
    long before = jdbc.queryForObject("select count(*) from event_publication", Long.class);
    inStore(
        storeId,
        () ->
            tx.execute(
                status -> {
                  context.publishEvent(
                      new DeliveryDispatch(row.id(), "123", serviceId, storeId, 1L));
                  status.setRollbackOnly();
                  return null;
                }));
    assertThat(jdbc.queryForObject("select count(*) from event_publication", Long.class))
        .isEqualTo(before);
    assertThat(get(row.id()).status()).isEqualTo("QUEUED");
    assertThat(transport.results).isEmpty();
  }

  @Test
  void revocationBetweenDispatchAndClaimPreventsExternalIo() throws Exception {
    var row = queue(draft());
    submitTask();
    jdbc.update(
        "delete from t_role_permissions where role_id=? and permission_id=(select id from t_permissions where code='TASK_EXECUTE')",
        serviceRole);
    drain();
    await(row.id(), "BLOCKED");
    assertThat(transport.results).isEmpty();
    assertThat(
            inStore(storeId, () -> notifications.history(email, row.id(), null, 10))
                .content()
                .getFirst()
                .failureCode())
        .isEqualTo("AUTHORIZATION_DENIED");
  }

  @Test
  void revocationAfterCommittedClaimIsRecheckedBeforeTransport() throws Exception {
    var row = queue(draft());
    submitTask();
    var event =
        jdbc.queryForObject(
            "select id,task_execution_id from t_notification_attempts where delivery_id=?",
            (rs, n) ->
                new DeliveryDispatch(row.id(), rs.getString(1), serviceId, storeId, rs.getLong(2)),
            row.id());
    var attempts = context.getBean(DeliveryAttempts.class);
    assertThat(inStore(storeId, () -> attempts.claim(event))).isTrue();
    jdbc.update("update t_users set enabled=false where id=?", serviceId);
    assertThat(inStore(storeId, () -> attempts.prepare(event))).isNull();
    drain();
    await(row.id(), "BLOCKED");
    assertThat(transport.results).isEmpty();
  }

  @Test
  void platformManualExecutionRequiresAdditionalBusinessPermission() {
    var command =
        new TaskCommand(
            "NOTIFICATION_DELIVER",
            UUID.randomUUID().toString(),
            serviceId,
            storeId,
            LocalDate.now(),
            LocalDate.now());
    var actor = new AuditActor(operatorId, "STAFF", "通知担当");
    assertThatThrownBy(() -> context.getBean(TaskExecutor.class).execute(command, actor))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void futureScheduleDoesNotDispatch() {
    var c = content(guest());
    var future =
        new DeliveryContent(
            c.sourceType(),
            c.sourceId(),
            c.channel(),
            c.purpose(),
            c.subject(),
            c.body(),
            OffsetDateTime.now().plusDays(1),
            c.dedupeKey());
    var row = inStore(storeId, () -> notifications.create(email, future).delivery());
    queue(row);
    runTask();
    assertThat(get(row.id()).status()).isEqualTo("QUEUED");
    assertThat(transport.results).isEmpty();
  }

  private String order() {
    return inStore(
        storeId,
        () ->
            tx.execute(
                status -> {
                  var item =
                      context
                          .getBean(ServiceItemRepository.class)
                          .saveAndFlush(
                              ServiceItem.create(
                                  new ServiceTerms(
                                      ServiceKind.COURSE, "検証コース", 60, null, 1000, 500)));
                  var revision =
                      context
                          .getBean(ServiceRevisionRepository.class)
                          .saveAndFlush(ServiceRevision.record(item, null, operatorId));
                  var row =
                      Order.builder()
                          .status(OrderStatus.CONFIRMED)
                          .businessDate(LocalDate.now())
                          .contactEmail("order@example.invalid")
                          .course(
                              new OrderCourse(
                                  item.getId(),
                                  revision.getId(),
                                  1,
                                  "検証コース",
                                  60,
                                  1000,
                                  500,
                                  "CURRENT_SETTING",
                                  OffsetDateTime.now()))
                          .build();
                  var saved = context.getBean(OrderRepository.class).saveAndFlush(row);
                  context
                      .getBean(BusinessContactHistoryRepository.class)
                      .saveAndFlush(
                          BusinessContactHistory.record(
                              saved.getId(),
                              ContactType.EMAIL,
                              "RECORDED",
                              null,
                              new BusinessContactState(
                                  "order@example.invalid",
                                  ContactPermissionStatus.ALLOWED,
                                  "受付",
                                  "業務連絡への同意"),
                              operatorId));
                  return saved.getId();
                }));
  }

  @Test
  void orderUsesCurrentConsentAndChangedAddressInvalidatesOldPermission() throws Exception {
    var source = order();
    var input =
        new DeliveryContent(
            DeliveryContent.SourceType.ORDER,
            source,
            "EMAIL",
            "BUSINESS",
            "受付確認",
            "本文",
            OffsetDateTime.now().minusMinutes(1),
            UUID.randomUUID().toString());
    var row = inStore(storeId, () -> notifications.create(email, input).delivery());
    queue(row);
    runTask();
    await(row.id(), "SENT");
    assertThat(transport.results).containsExactly("order@example.invalid");
    jdbc.update("update t_orders set contact_email='changed@example.invalid' where id=?", source);
    inStore(
        storeId,
        () ->
            tx.execute(
                status ->
                    context
                        .getBean(BusinessContactHistoryRepository.class)
                        .saveAndFlush(
                            BusinessContactHistory.record(
                                source,
                                ContactType.EMAIL,
                                "CONTACT_CHANGED",
                                null,
                                BusinessContactState.unknown("changed@example.invalid"),
                                operatorId))));
    var nextInput =
        new DeliveryContent(
            input.sourceType(),
            source,
            "EMAIL",
            "BUSINESS",
            "確認",
            "本文",
            OffsetDateTime.now().minusMinutes(1),
            UUID.randomUUID().toString());
    var next = inStore(storeId, () -> notifications.create(email, nextInput).delivery());
    queue(next);
    runTask();
    await(next.id(), "BLOCKED");
    assertThat(transport.results).hasSize(1);
  }

  @Test
  void serviceCandidatesRespectTaskPermissionsAndTargetStoreWithoutGrantingExecution() {
    var options = context.getBean(TaskOptions.class);
    jdbc.update("update t_users set store_scope_type='SPECIFIC_STORES' where id=?", serviceId);
    jdbc.update(
        "insert into t_user_stores(platform_user_id,store_id) values (?,?)", serviceId, storeId);
    assertThat(options.candidates("NOTIFICATION_DELIVER", storeId, 0, 100).getContent())
        .extracting(ServiceExecutionIdentityService.Candidate::id)
        .contains(serviceId);
    assertThat(options.candidates("NOTIFICATION_DELIVER", otherStore, 0, 100).getContent())
        .extracting(ServiceExecutionIdentityService.Candidate::id)
        .doesNotContain(serviceId);
    assertThat(options.candidates("SERVICE_IDENTITY_CHECK", null, 0, 100).getContent())
        .extracting(ServiceExecutionIdentityService.Candidate::id)
        .doesNotContain(serviceId);
    assertThat(options.taskTypes(email)).extracting(TaskOptions.TaskType::name).isSorted();
    assertThat(
            options.taskTypes(email).stream()
                .filter(t -> t.name().equals("NOTIFICATION_DELIVER"))
                .findFirst()
                .orElseThrow()
                .manualAllowed())
        .isFalse();
    assertThat(options.stores(0, 100).getContent())
        .extracting(TaskOptions.StoreOption::id)
        .contains(storeId.toString());
    assertThatThrownBy(() -> options.candidates("MISSING", storeId, 0, 10))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> options.candidates("NOTIFICATION_DELIVER", null, 0, 10))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> options.candidates("NOTIFICATION_DELIVER", Long.MAX_VALUE, 0, 10))
        .isInstanceOf(NotFoundException.class);
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

  static class FakeTransport implements EmailTransport {
    final List<String> results = new CopyOnWriteArrayList<>();
    volatile Result outcome = Result.SENT;
    volatile boolean crash;
    Runnable afterSend = () -> {};

    public boolean available() {
      return outcome != Result.UNAVAILABLE;
    }

    public Result deliver(String recipient, String subject, String body) {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      results.add(recipient);
      afterSend.run();
      if (crash) throw new AssertionError("送信後の停止を模擬");
      return outcome;
    }
  }

  @Configuration
  @EnableAutoConfiguration
  @EnableAsync
  @EnableAspectJAutoProxy
  @EntityScan("com.kizuna")
  @EnableJpaRepositories("com.kizuna")
  @ComponentScan(basePackageClasses = NotificationService.class)
  @Import({
    StoreContext.class,
    StoreFilterEnable.class,
    StoreScopeStampListener.class,
    AuditWriter.class,
    TaskExecutor.class,
    TaskLifecycle.class,
    TaskRegistry.class,
    TaskOptions.class,
    ServiceIdentityCheckTask.class,
    ServiceExecutionIdentityService.class,
    ActorIdentityService.class,
    OrderBusinessContact.class,
    OrderApplicationBusinessContact.class,
    BusinessContactPermissions.class,
    GuestApplicationConsent.class,
    BusinessContactPolicy.class,
    BusinessContactRestrictions.class,
    GuestContactImports.class
  })
  static class Config {
    @Bean
    DeferredEvents taskExecutor() {
      return new DeferredEvents();
    }

    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    AppProperties properties() {
      return new AppProperties();
    }

    @Bean
    FakeTransport transport() {
      return new FakeTransport();
    }
  }
}
