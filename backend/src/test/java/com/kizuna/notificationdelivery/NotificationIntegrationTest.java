package com.kizuna.notificationdelivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.audit.recording.AuditActor;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.customer.contact.BusinessContactRestrictions;
import com.kizuna.customer.contact.GuestContactImports;
import com.kizuna.customer.contact.MarketingContacts;
import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactPurpose;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerContact;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.member.application.MemberLoginAddress;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.membernotification.application.MemberNotificationSettingService;
import com.kizuna.membernotification.contact.MemberContacts;
import com.kizuna.notification.transport.ContactTransport;
import com.kizuna.notificationdelivery.api.dto.DeliveryResponse;
import com.kizuna.notificationdelivery.application.ContactDecision;
import com.kizuna.notificationdelivery.application.DeliveryAttempts;
import com.kizuna.notificationdelivery.application.DeliveryContact;
import com.kizuna.notificationdelivery.application.DeliveryDispatch;
import com.kizuna.notificationdelivery.application.NotificationService;
import com.kizuna.notificationdelivery.domain.DeliveryChannel;
import com.kizuna.notificationdelivery.domain.DeliveryContent;
import com.kizuna.notificationdelivery.domain.DeliveryPurpose;
import com.kizuna.notificationdelivery.domain.DeliveryResolution;
import com.kizuna.notificationdelivery.domain.DeliverySourceType;
import com.kizuna.notificationdelivery.domain.NotificationDelivery;
import com.kizuna.order.application.BusinessContactPermissions;
import com.kizuna.order.application.GuestApplicationConsent;
import com.kizuna.order.contact.BusinessContactPolicy;
import com.kizuna.order.contact.OrderApplicationBusinessContact;
import com.kizuna.order.contact.OrderBusinessContact;
import com.kizuna.order.contact.SourceCustomers;
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
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.store.StoreShortNames;
import com.kizuna.task.application.ServiceIdentityCheckTask;
import com.kizuna.task.application.TaskLifecycle;
import com.kizuna.task.application.TaskOptions;
import com.kizuna.task.application.TaskRegistry;
import com.kizuna.task.execution.TaskCommand;
import com.kizuna.task.execution.TaskExecutor;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationIntegrationTest {
  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private NotificationService notifications;
  private TransactionTemplate tx;
  private FakeTransport transport;
  private Long storeId, otherStore, operatorId, serviceId, serviceRole;
  private String email;

  @BeforeAll
  void connect() {
    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                IntegrationTestDatabase.springDataSourceProperties(
                    NotificationIntegrationTest.class))
            .properties(
                Map.ofEntries(
                    Map.entry("spring.config.name", "notification-test"),
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
    // 送信の定期処理はグループ全体を掃くので、前の試験が残した送信待ちを次の試験に持ち越さない。
    jdbc.update("delete from t_notification_deliveries");
    transport.results.clear();
    transport.outcome = ContactTransport.Result.SENT;
    transport.crash = false;
    transport.afterSend = () -> {};
    String suffix = UUID.randomUUID().toString();
    email = suffix + "@example.invalid";
    storeId =
        jdbc.queryForObject(
            "insert into t_stores(name,short_name,domain) values (?,?,?) returning id",
            Long.class,
            "通知検証",
            StoreShortNames.next(),
            suffix + ".invalid");
    otherStore =
        jdbc.queryForObject(
            "insert into t_stores(name,short_name,domain) values (?,?,?) returning id",
            Long.class,
            "別店舗",
            StoreShortNames.next(),
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
        "insert into t_user_roles(user_id,role_id) values (?,?),(?,?)",
        operatorId,
        role,
        serviceId,
        serviceRole);
  }

  private String guest() {
    return tx.execute(
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
          row.setStoreId(storeId);
          return context.getBean(OrderApplicationRepository.class).saveAndFlush(row).getId();
        });
  }

  private DeliveryContent content(String source) {
    return new DeliveryContent(
        DeliverySourceType.APPLICATION,
        source,
        DeliveryChannel.EMAIL,
        DeliveryPurpose.BUSINESS,
        "受付確認",
        "機密な本文",
        OffsetDateTime.now().minusMinutes(1),
        UUID.randomUUID().toString());
  }

  private DeliveryResponse draft() {
    var source = guest();
    return DeliveryResponse.of(notifications.create(storeId, email, content(source)).delivery());
  }

  private DeliveryResponse queue(DeliveryResponse row) {
    return DeliveryResponse.of(
        notifications.queue(storeId, email, row.id(), row.version(), "内容確認済み", false));
  }

  private void submitTask() {
    var command =
        new TaskCommand(
            "NOTIFICATION_DELIVER",
            UUID.randomUUID().toString(),
            serviceId,
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
    return DeliveryResponse.of(notifications.get(storeId, email, id));
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
    var history = notifications.history(storeId, email, row.id(), PageRequest.of(0, 10));
    assertThat(history.getContent()).hasSize(1);
    assertThat(history.getContent().getFirst().getStatus().name()).isEqualTo("SENT");
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
            () -> notifications.queue(storeId, email, row.id(), sent.version(), "再送", true))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void storeDenialAfterQueueBlocksAndExplicitRetryRechecks() throws Exception {
    var row = queue(draft());
    var contact =
        tx.execute(
            status -> {
              var newCustomer = Customer.builder().name("検証顧客").build();
              newCustomer.setStoreId(storeId);
              var customer = context.getBean(CustomerRepository.class).saveAndFlush(newCustomer);
              var c =
                  CustomerContact.create(
                      storeId, customer.getId(), ContactType.EMAIL, "permitted@example.invalid");
              c.changePermission(ContactPurpose.BUSINESS, ContactPermissionStatus.DENIED);
              return context.getBean(CustomerContactRepository.class).saveAndFlush(c).getId();
            });
    runTask();
    var blocked = await(row.id(), "BLOCKED");
    assertThat(transport.results).isEmpty();
    jdbc.update("update t_customer_contacts set business_status='ALLOWED' where id=?", contact);
    notifications.queue(storeId, email, row.id(), blocked.version(), "同意再確認", true);
    runTask();
    await(row.id(), "SENT");
    assertThat(transport.results).hasSize(1);
    assertThat(notifications.history(storeId, email, row.id(), PageRequest.of(0, 10)).getContent())
        .hasSize(2);
  }

  @Test
  void unavailableAndUnknownAreNotSentAndUnknownCannotRetry() throws Exception {
    transport.outcome = ContactTransport.Result.UNAVAILABLE;
    var row = queue(draft());
    runTask();
    await(row.id(), "FAILED");
    assertThat(
            notifications
                .history(storeId, email, row.id(), PageRequest.of(0, 10))
                .getContent()
                .getFirst()
                .getFailureCode())
        .isEqualTo("UNAVAILABLE");
    transport.outcome = ContactTransport.Result.UNKNOWN;
    var next = queue(draft());
    runTask();
    var unknown = await(next.id(), "UNKNOWN");
    assertThatThrownBy(
            () -> notifications.queue(storeId, email, next.id(), unknown.version(), "再送", true))
        .isInstanceOf(ConflictException.class);
  }

  private DeliveryResponse unknownDelivery() throws Exception {
    transport.outcome = ContactTransport.Result.UNKNOWN;
    var row = queue(draft());
    runTask();
    var unknown = await(row.id(), "UNKNOWN");
    transport.outcome = ContactTransport.Result.SENT;
    return unknown;
  }

  private DeliveryResponse resolve(
      DeliveryResponse row, DeliveryResolution outcome, String reason) {
    return DeliveryResponse.of(
        notifications.resolve(storeId, email, row.id(), row.version(), outcome, reason));
  }

  @Test
  void undeliveredJudgmentIsAHumanAttemptAuditedWithoutContentAndCanBeRetried() throws Exception {
    var unknown = unknownDelivery();
    var failed = resolve(unknown, DeliveryResolution.FAILED, "提供元の管理画面で未配信を確認");
    assertThat(failed.status()).isEqualTo("FAILED");
    assertThat(failed.attemptCount()).isEqualTo(2);
    var judged =
        notifications
            .history(
                storeId, email, unknown.id(), PageRequest.of(0, 10, Sort.by("id").descending()))
            .getContent()
            .getFirst();
    assertThat(judged.getAttemptNumber()).isEqualTo(2);
    assertThat(judged.getStatus().name()).isEqualTo("FAILED");
    assertThat(judged.getFailureCode()).isEqualTo("MANUAL_RESOLUTION");
    assertThat(judged.getTaskExecutionId()).isNull();
    assertThat(judged.getReason()).isEqualTo("提供元の管理画面で未配信を確認");
    assertThat(
            jdbc.queryForObject(
                "select actor_user_id from t_notification_attempts where id=?",
                Long.class,
                judged.getId()))
        .isEqualTo(operatorId);
    var audit =
        jdbc.queryForMap(
            "select actor_id,actor_name,originating_store_id,after_values::text as after from t_audit_events where target_id=? and action='NOTIFICATION_RESOLVED'",
            unknown.id());
    assertThat(audit.get("actor_id")).isEqualTo(operatorId);
    assertThat(audit.get("actor_name")).isEqualTo("通知担当");
    assertThat(audit.get("originating_store_id")).isEqualTo(storeId);
    assertThat((String) audit.get("after"))
        .contains("FAILED")
        .doesNotContain("機密な本文", "受付確認", "未配信を確認", "example.invalid");

    notifications.queue(storeId, email, unknown.id(), failed.version(), "未送達の判定にもとづき再送", true);
    runTask();
    await(unknown.id(), "SENT");
    assertThat(transport.results).hasSize(2);
    assertThat(
            notifications.history(storeId, email, unknown.id(), PageRequest.of(0, 10)).getContent())
        .extracting(a -> a.getAttemptNumber())
        .containsExactlyInAnyOrder(1, 2, 3);
  }

  @Test
  void deliveredJudgmentCannotBeRetriedAndOnlyUnknownCanBeJudgedOnce() throws Exception {
    var unknown = unknownDelivery();
    var sent = resolve(unknown, DeliveryResolution.SENT, "提供元で配信済みを確認");
    assertThat(sent.status()).isEqualTo("SENT");
    assertThatThrownBy(
            () -> notifications.queue(storeId, email, unknown.id(), sent.version(), "再送", true))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> resolve(sent, DeliveryResolution.FAILED, "判定の上書き"))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> resolve(unknown, DeliveryResolution.FAILED, "古い版で判定"))
        .isInstanceOf(ConflictException.class);

    var draft = draft();
    assertThatThrownBy(() -> resolve(draft, DeliveryResolution.FAILED, "下書きを判定"))
        .isInstanceOf(ConflictException.class);
    transport.outcome = ContactTransport.Result.FAILED;
    var definite = queue(draft());
    runTask();
    var failed = await(definite.id(), "FAILED");
    assertThatThrownBy(() -> resolve(failed, DeliveryResolution.SENT, "未送信を送達済みに"))
        .isInstanceOf(ConflictException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_audit_events where action='NOTIFICATION_RESOLVED' and target_id in (?,?,?)",
                Long.class,
                unknown.id(),
                draft.id(),
                definite.id()))
        .isEqualTo(1L);
  }

  @Test
  void judgmentIsScopedToTheCurrentStoreAndRequiresSendPermission() throws Exception {
    var unknown = unknownDelivery();
    assertThatThrownBy(
            () ->
                notifications.resolve(
                    otherStore,
                    email,
                    unknown.id(),
                    unknown.version(),
                    DeliveryResolution.FAILED,
                    "別店舗から判定"))
        .isInstanceOf(NotFoundException.class);
    jdbc.update(
        "delete from t_role_permissions where permission_id=(select id from t_permissions where code='NOTIFICATION_SEND') and role_id in (select role_id from t_user_roles where user_id=?)",
        operatorId);
    assertThatThrownBy(() -> resolve(unknown, DeliveryResolution.FAILED, "権限なしで判定"))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(get(unknown.id()).status()).isEqualTo("UNKNOWN");
  }

  @Test
  void onlyHumanJudgmentsMayLackATaskExecution() throws Exception {
    var unknown = unknownDelivery();
    for (var row :
        List.of(
            new Object[] {"FAILED", "UNAVAILABLE"},
            new Object[] {"UNKNOWN", "MANUAL_RESOLUTION"},
            new Object[] {"BLOCKED", "MANUAL_RESOLUTION"}))
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "insert into t_notification_attempts(id,delivery_id,attempt_number,actor_user_id,actor_name,reason,started_at,status,failure_code) values (?,?,9,?,'職員','理由',now(),?,?)",
                      UUID.randomUUID().toString().replace("-", ""),
                      unknown.id(),
                      operatorId,
                      row[0],
                      row[1]))
          .hasMessageContaining("ck_t_notification_attempts_manual_resolution");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update t_notification_attempts set failure_code='MANUAL_RESOLUTION' where delivery_id=?",
                    unknown.id()))
        .hasMessageContaining("ck_t_notification_attempts_manual_resolution");
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
            () -> notifications.queue(storeId, email, first.id(), sent.version(), "再送", true))
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
            () -> notifications.queue(storeId, email, row.id(), unknown.version(), "結果を再確認", true))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void otherStoreAndDuplicateActionsAreRejected() {
    var row = draft();
    assertThatThrownBy(() -> notifications.get(otherStore, email, row.id()))
        .isInstanceOf(NotFoundException.class);
    queue(row);
    assertThatThrownBy(() -> queue(row)).isInstanceOf(ConflictException.class);
    String source = guest();
    var input = content(source);
    var a = notifications.create(storeId, email, input);
    var b = notifications.create(storeId, email, input);
    assertThat(a.delivery().row().getId()).isEqualTo(b.delivery().row().getId());
    assertThat(b.created()).isFalse();
  }

  @Test
  void rolledBackDispatchPublishesNoVisibleEventOrAttempt() {
    var row = queue(draft());
    long before = jdbc.queryForObject("select count(*) from event_publication", Long.class);
    tx.execute(
        status -> {
          context.publishEvent(new DeliveryDispatch(row.id(), "123", serviceId, 1L));
          status.setRollbackOnly();
          return null;
        });
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
            notifications
                .history(storeId, email, row.id(), PageRequest.of(0, 10))
                .getContent()
                .getFirst()
                .getFailureCode())
        .isEqualTo("AUTHORIZATION_DENIED");
  }

  @Test
  void revocationAfterCommittedClaimIsRecheckedBeforeTransport() throws Exception {
    var row = queue(draft());
    submitTask();
    var event =
        jdbc.queryForObject(
            "select id,task_execution_id from t_notification_attempts where delivery_id=?",
            (rs, n) -> new DeliveryDispatch(row.id(), rs.getString(1), serviceId, rs.getLong(2)),
            row.id());
    var attempts = context.getBean(DeliveryAttempts.class);
    assertThat(attempts.claim(event)).isTrue();
    jdbc.update("update t_users set enabled=false where id=?", serviceId);
    assertThat(attempts.prepare(event)).isNull();
    drain();
    await(row.id(), "BLOCKED");
    assertThat(transport.results).isEmpty();
  }

  @Test
  void groupWideManualExecutionRequiresAdditionalBusinessPermission() {
    var command =
        new TaskCommand(
            "NOTIFICATION_DELIVER",
            UUID.randomUUID().toString(),
            serviceId,
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
    var row = DeliveryResponse.of(notifications.create(storeId, email, future).delivery());
    queue(row);
    runTask();
    assertThat(get(row.id()).status()).isEqualTo("QUEUED");
    assertThat(transport.results).isEmpty();
  }

  private String order() {
    return tx.execute(
        status -> {
          var item =
              context
                  .getBean(ServiceItemRepository.class)
                  .saveAndFlush(
                      ServiceItem.create(
                          storeId,
                          new ServiceTerms(ServiceKind.COURSE, "検証コース", 60, null, 1000, 500)));
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
          row.setStoreId(storeId);
          var saved = context.getBean(OrderRepository.class).saveAndFlush(row);
          context
              .getBean(BusinessContactHistoryRepository.class)
              .saveAndFlush(
                  BusinessContactHistory.record(
                      storeId,
                      saved.getId(),
                      ContactType.EMAIL,
                      "RECORDED",
                      null,
                      new BusinessContactState(
                          "order@example.invalid",
                          ContactPermissionStatus.ALLOWED,
                          "受付",
                          "業務連絡への同意"),
                      operatorId,
                      OffsetDateTime.now()));
          return saved.getId();
        });
  }

  @Test
  void orderUsesCurrentConsentAndChangedAddressInvalidatesOldPermission() throws Exception {
    var source = order();
    var input =
        new DeliveryContent(
            DeliverySourceType.ORDER,
            source,
            DeliveryChannel.EMAIL,
            DeliveryPurpose.BUSINESS,
            "受付確認",
            "本文",
            OffsetDateTime.now().minusMinutes(1),
            UUID.randomUUID().toString());
    var row = DeliveryResponse.of(notifications.create(storeId, email, input).delivery());
    queue(row);
    runTask();
    await(row.id(), "SENT");
    assertThat(transport.results).containsExactly("order@example.invalid");
    jdbc.update("update t_orders set contact_email='changed@example.invalid' where id=?", source);
    tx.execute(
        status ->
            context
                .getBean(BusinessContactHistoryRepository.class)
                .saveAndFlush(
                    BusinessContactHistory.record(
                        storeId,
                        source,
                        ContactType.EMAIL,
                        "CONTACT_CHANGED",
                        null,
                        BusinessContactState.unknown("changed@example.invalid"),
                        operatorId,
                        OffsetDateTime.now())));
    var nextInput =
        new DeliveryContent(
            input.sourceType(),
            source,
            DeliveryChannel.EMAIL,
            DeliveryPurpose.BUSINESS,
            "確認",
            "本文",
            OffsetDateTime.now().minusMinutes(1),
            UUID.randomUUID().toString());
    var next = DeliveryResponse.of(notifications.create(storeId, email, nextInput).delivery());
    queue(next);
    runTask();
    await(next.id(), "BLOCKED");
    assertThat(transport.results).hasSize(1);
  }

  @Test
  void serviceCandidatesForGroupWideDeliveryRequireAllStoresWithoutGrantingExecution() {
    var options = context.getBean(TaskOptions.class);
    assertThat(options.candidates("NOTIFICATION_DELIVER", PageRequest.of(0, 100)).getContent())
        .extracting(ServiceExecutionIdentityService.Candidate::id)
        .contains(serviceId);
    assertThat(options.taskTypes(email)).extracting(TaskOptions.TaskType::name).isSorted();
    var delivery =
        options.taskTypes(email).stream()
            .filter(t -> t.name().equals("NOTIFICATION_DELIVER"))
            .findFirst()
            .orElseThrow();
    assertThat(delivery.manualAllowed()).isFalse();
    assertThatThrownBy(() -> options.candidates("MISSING", PageRequest.of(0, 10)))
        .isInstanceOf(ServiceException.class);
    jdbc.update("update t_users set store_scope_type='SPECIFIC_STORES' where id=?", serviceId);
    jdbc.update("insert into t_user_stores(user_id,store_id) values (?,?)", serviceId, storeId);
    assertThat(options.candidates("NOTIFICATION_DELIVER", PageRequest.of(0, 100)).getContent())
        .extracting(ServiceExecutionIdentityService.Candidate::id)
        .doesNotContain(serviceId);
    assertThat(options.candidates("SERVICE_IDENTITY_CHECK", PageRequest.of(0, 100)).getContent())
        .extracting(ServiceExecutionIdentityService.Candidate::id)
        .doesNotContain(serviceId);
  }

  @Test
  void oneStorelessRunDeliversDueRowsOfEveryStore() throws Exception {
    var here = queue(draft());
    var source =
        tx.execute(
            status -> {
              var row =
                  OrderApplication.builder()
                      .status(OrderApplicationStatus.PENDING)
                      .businessDate(LocalDate.now())
                      .contactEmail("other@example.invalid")
                      .consentContact(
                          new ContactSnapshot(null, null, "other@example.invalid", null))
                      .contactConsent(
                          new GuestContactConsent(
                              "v1", "業務連絡に同意", "販促同意", true, false, OffsetDateTime.now()))
                      .build();
              row.setStoreId(otherStore);
              return context.getBean(OrderApplicationRepository.class).saveAndFlush(row).getId();
            });
    var there =
        DeliveryResponse.of(notifications.create(otherStore, email, content(source)).delivery());
    notifications.queue(otherStore, email, there.id(), there.version(), "内容確認済み", false);
    runTask();
    await(here.id(), "SENT");
    assertThat(DeliveryResponse.of(notifications.get(otherStore, email, there.id())).status())
        .isEqualTo("SENT");
    assertThat(transport.results)
        .containsExactlyInAnyOrder("permitted@example.invalid", "other@example.invalid");
  }

  private String customerWithEmail(String address) {
    return tx.execute(
        status -> {
          var newCustomer = Customer.builder().name("販促検証").build();
          newCustomer.setStoreId(storeId);
          var customer = context.getBean(CustomerRepository.class).saveAndFlush(newCustomer);
          var contact =
              CustomerContact.create(storeId, customer.getId(), ContactType.EMAIL, address);
          contact.prefer(true);
          contact.changePermission(ContactPurpose.BUSINESS, ContactPermissionStatus.ALLOWED);
          context.getBean(CustomerContactRepository.class).saveAndFlush(contact);
          return customer.getId();
        });
  }

  private DeliveryContent marketing(DeliverySourceType type, String source) {
    return new DeliveryContent(
        type,
        source,
        DeliveryChannel.EMAIL,
        DeliveryPurpose.MARKETING,
        "ご案内",
        "本文",
        OffsetDateTime.now(),
        UUID.randomUUID().toString());
  }

  @Test
  void marketingFollowsCustomerLedgerConsentAndIsNotInferredFromOrderBusinessPermission() {
    var resolver = context.getBean(DeliveryContact.class);
    var order = order();
    var business = resolver.resolve(storeId, marketing(DeliverySourceType.ORDER, order));
    assertThat(business.decision()).isEqualTo(ContactDecision.NOT_ALLOWED);
    var customer = customerWithEmail("ledger@example.invalid");
    jdbc.update("update t_orders set customer_id=? where id=?", customer, order);
    var unknown = resolver.resolve(storeId, marketing(DeliverySourceType.ORDER, order));
    assertThat(unknown.decision()).isEqualTo(ContactDecision.NOT_ALLOWED);
    jdbc.update(
        "update t_customer_contacts set marketing_status='ALLOWED' where customer_id=?", customer);
    var allowed = resolver.resolve(storeId, marketing(DeliverySourceType.ORDER, order));
    assertThat(allowed)
        .extracting(r -> r.decision(), r -> r.address())
        .containsExactly(ContactDecision.ALLOWED, "ledger@example.invalid");
    assertThat(
            resolver
                .resolve(storeId, marketing(DeliverySourceType.APPLICATION, guest()))
                .decision())
        .isEqualTo(ContactDecision.NOT_ALLOWED);
    var application = guest();
    jdbc.update("update t_order_applications set order_id=? where id=?", order, application);
    assertThat(
            resolver
                .resolve(storeId, marketing(DeliverySourceType.APPLICATION, application))
                .address())
        .isEqualTo("ledger@example.invalid");
  }

  @Test
  void dedupeKeyStaysUniqueAmongGroupLevelRows() {
    var insert =
        "insert into t_notification_deliveries(id,attributed_store_id,source_type,source_id,channel,purpose,subject,body,scheduled_at,dedupe_key,status,attempt_count) values (?,null,'ORDER','1','EMAIL','BUSINESS','件名','本文',now(),'group-key','DRAFT',0)";
    jdbc.update(insert, UUID.randomUUID().toString().substring(0, 32));
    assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID().toString().substring(0, 32)))
        .hasMessageContaining("uq_t_notification_deliveries_key");
    jdbc.update("delete from t_notification_deliveries where dedupe_key='group-key'");
  }

  @Test
  void deliveryTableHoldsAPolymorphicSourceAndEnumeratesNoChannelOrPurpose() {
    assertThat(
            jdbc.queryForList(
                "select column_name from information_schema.columns where table_schema=current_schema() and table_name='t_notification_deliveries'",
                String.class))
        .contains("source_type", "source_id", "attributed_store_id")
        .doesNotContain("order_id", "application_id", "store_id");
    assertThat(
            jdbc.queryForList(
                "select conname from pg_constraint where conrelid='t_notification_deliveries'::regclass and contype in ('c','f')",
                String.class))
        .containsExactlyInAnyOrder(
            "ck_t_notification_deliveries_status",
            "fk_t_notification_deliveries_attributed_store_id");
  }

  private long member(String address, String business, String marketing, boolean withdrawn) {
    var user =
        jdbc.queryForObject(
            "insert into t_users(display_name,user_type,store_scope_type,email,password) values ('検証会員','MEMBER','SPECIFIC_STORES',?,'x') returning id",
            Long.class,
            address);
    var member =
        jdbc.queryForObject(
            "insert into t_members(member_code,user_id,withdrawn_at,withdrawal_reason,withdrawn_by) values (?,?,?,?,?) returning id",
            Long.class,
            UUID.randomUUID().toString().substring(0, 20),
            user,
            withdrawn ? OffsetDateTime.now() : null,
            withdrawn ? "検証" : null,
            withdrawn ? user : null);
    if (business != null)
      jdbc.update(
          "insert into t_member_notification_settings(member_id,purpose,status) values (?,'BUSINESS',?)",
          member,
          business);
    if (marketing != null)
      jdbc.update(
          "insert into t_member_notification_settings(member_id,purpose,status) values (?,'MARKETING',?)",
          member,
          marketing);
    return member;
  }

  private DeliveryContent memberContent(long member, DeliveryPurpose purpose) {
    return new DeliveryContent(
        DeliverySourceType.MEMBER,
        Long.toString(member),
        DeliveryChannel.EMAIL,
        purpose,
        "期限のご案内",
        "会員宛の本文",
        OffsetDateTime.now().minusMinutes(1),
        UUID.randomUUID().toString());
  }

  private DeliveryResponse groupQueued(long member) {
    var row =
        DeliveryResponse.of(
            notifications
                .create(null, email, memberContent(member, DeliveryPurpose.BUSINESS))
                .delivery());
    return DeliveryResponse.of(
        notifications.queue(null, email, row.id(), row.version(), "内容確認済み", false));
  }

  private DeliveryResponse awaitGroup(String id, String status) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
    DeliveryResponse row;
    do {
      row = DeliveryResponse.of(notifications.get(null, email, id));
      if (row.status().equals(status)) return row;
      Thread.sleep(20);
    } while (System.nanoTime() < deadline);
    assertThat(row.status()).isEqualTo(status);
    return row;
  }

  private String lastFailure(String id) {
    return notifications
        .history(null, email, id, PageRequest.of(0, 1, Sort.by("id").descending()))
        .getContent()
        .getFirst()
        .getFailureCode();
  }

  @Test
  void memberNoticeIsSentOnlyToMembersWhoAllowedBusinessContact() throws Exception {
    var allowed = groupQueued(member("allowed@example.invalid", "ALLOWED", null, false));
    var unconfirmed = groupQueued(member("unknown@example.invalid", null, null, false));
    var denied = groupQueued(member("denied@example.invalid", "DENIED", "ALLOWED", false));
    var withdrawn = groupQueued(member("left@example.invalid", "ALLOWED", null, true));
    runTask();

    assertThat(awaitGroup(allowed.id(), "SENT").contactDecision()).isEqualTo("ALLOWED");
    for (var row : List.of(unconfirmed, denied, withdrawn)) {
      assertThat(awaitGroup(row.id(), "BLOCKED").contactDecision()).isEqualTo("NOT_ALLOWED");
      assertThat(lastFailure(row.id())).isEqualTo("CONTACT_NOT_ALLOWED");
    }
    assertThat(transport.results).containsExactly("allowed@example.invalid");
  }

  @Test
  void memberMarketingFollowsOnlyTheMarketingSetting() {
    var resolver = context.getBean(DeliveryContact.class);
    var businessOnly = member("biz@example.invalid", "ALLOWED", null, false);
    assertThat(
            resolver
                .resolve(null, marketing(DeliverySourceType.MEMBER, Long.toString(businessOnly)))
                .decision())
        .isEqualTo(ContactDecision.NOT_ALLOWED);
    var marketingOnly = member("promo@example.invalid", "DENIED", "ALLOWED", false);
    assertThat(
            resolver.resolve(
                null, marketing(DeliverySourceType.MEMBER, Long.toString(marketingOnly))))
        .extracting(r -> r.decision(), r -> r.address())
        .containsExactly(ContactDecision.ALLOWED, "promo@example.invalid");
    var refused = member("no-promo@example.invalid", "ALLOWED", "DENIED", false);
    assertThat(
            resolver
                .resolve(null, marketing(DeliverySourceType.MEMBER, Long.toString(refused)))
                .decision())
        .isEqualTo(ContactDecision.NOT_ALLOWED);
  }

  @Test
  void groupRowsAreSeparateFromStoreRowsAndNeedAllStores() {
    var group = groupQueued(member("group@example.invalid", "ALLOWED", null, false));
    var store = draft();
    assertThat(notifications.list(storeId, email, PageRequest.of(0, 50)).getContent())
        .extracting(NotificationDelivery::getId)
        .containsExactly(store.id());
    assertThat(notifications.list(null, email, PageRequest.of(0, 50)).getContent())
        .extracting(NotificationDelivery::getId)
        .containsExactly(group.id());
    assertThatThrownBy(() -> notifications.get(storeId, email, group.id()))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> notifications.get(null, email, store.id()))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () -> notifications.queue(null, email, store.id(), store.version(), "グループから操作", false))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () ->
                notifications.resolve(
                    null, email, store.id(), store.version(), DeliveryResolution.SENT, "グループから判定"))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> notifications.history(null, email, store.id(), PageRequest.of(0, 10)))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () -> notifications.history(storeId, email, group.id(), PageRequest.of(0, 10)))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () -> notifications.queue(storeId, email, group.id(), group.version(), "店舗から操作", true))
        .isInstanceOf(NotFoundException.class);

    var scoped =
        jdbc.queryForObject(
            "insert into t_users(email,display_name,user_type,store_scope_type) values (?,'店舗の通知担当','STAFF','SPECIFIC_STORES') returning id",
            Long.class,
            "scoped-" + email);
    jdbc.update("insert into t_user_stores(user_id,store_id) values (?,?)", scoped, storeId);
    jdbc.update(
        "insert into t_user_roles(user_id,role_id) select ?,role_id from t_user_roles where user_id=?",
        scoped,
        operatorId);
    assertThat(notifications.list(storeId, "scoped-" + email, PageRequest.of(0, 50))).hasSize(1);
    assertThatThrownBy(() -> notifications.list(null, "scoped-" + email, PageRequest.of(0, 50)))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> notifications.get(null, "scoped-" + email, group.id()))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void memberSourceIsGroupOnlyAndMustExist() {
    var member = member("exists@example.invalid", null, null, false);
    assertThatThrownBy(
            () ->
                notifications.create(
                    storeId, email, memberContent(member, DeliveryPurpose.BUSINESS)))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(() -> notifications.create(null, email, content(guest())))
        .isInstanceOf(ServiceException.class);
    assertThatThrownBy(
            () ->
                notifications.create(
                    null, email, memberContent(Long.MAX_VALUE, DeliveryPurpose.BUSINESS)))
        .isInstanceOf(NotFoundException.class);
    assertThat(jdbc.queryForObject("select count(*) from t_notification_deliveries", Long.class))
        .isZero();
  }

  @Test
  void groupRowsCanBeRetriedAndJudgedWithoutLeakingTheAddress() throws Exception {
    var member = member("later@example.invalid", null, null, false);
    var blocked = groupQueued(member);
    runTask();
    blocked = awaitGroup(blocked.id(), "BLOCKED");
    jdbc.update(
        "insert into t_member_notification_settings(member_id,purpose,status) values (?,'BUSINESS','ALLOWED')",
        member);
    notifications.queue(null, email, blocked.id(), blocked.version(), "本人が許可したので再送", true);
    transport.outcome = ContactTransport.Result.UNKNOWN;
    runTask();
    var unknown = awaitGroup(blocked.id(), "UNKNOWN");
    var judged =
        DeliveryResponse.of(
            notifications.resolve(
                null, email, unknown.id(), unknown.version(), DeliveryResolution.SENT, "受信を確認"));
    assertThat(judged.status()).isEqualTo("SENT");
    assertThat(transport.results).containsExactly("later@example.invalid");

    var audits =
        jdbc.queryForList(
            "select originating_store_id,source_type,after_values::text as after from t_audit_events where target_id=?",
            blocked.id());
    assertThat(audits).isNotEmpty();
    for (var audit : audits) {
      assertThat(audit.get("originating_store_id")).isNull();
      assertThat((String) audit.get("after"))
          .doesNotContain("later@example.invalid", "会員宛の本文", "期限のご案内");
    }
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
    volatile Result outcome = Result.SENT;
    volatile boolean crash;
    Runnable afterSend = () -> {};

    public Channel type() {
      return Channel.EMAIL;
    }

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
  @EntityScan("com.kizuna")
  @EnableJpaRepositories("com.kizuna")
  @ComponentScan(basePackageClasses = NotificationService.class)
  @Import({
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
    GuestContactImports.class,
    SourceCustomers.class,
    MarketingContacts.class,
    MemberContacts.class,
    MemberLoginAddress.class,
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
