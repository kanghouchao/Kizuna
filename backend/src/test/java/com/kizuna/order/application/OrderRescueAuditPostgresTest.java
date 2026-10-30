package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderCourses;
import com.kizuna.order.domain.OrderReceiptToken;
import com.kizuna.order.domain.OrderReceiptTokenRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.infrastructure.ReceiptTokenGenerator;
import com.kizuna.point.application.PointLedgerService;
import com.kizuna.point.application.PointLedgerService.PointRollbackHistory;
import com.kizuna.point.application.PointLedgerService.PointRollbackPreview;
import com.kizuna.point.application.PointLedgerService.PointRollbackResult;
import com.kizuna.shared.persistence.EnabledIfPostgres;
import com.kizuna.shared.persistence.PostgresTestDatabase;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** 専用の使い捨て DB で、救済 4 入口の業務行と監査行が同じ取引で確定・巻戻しされ、実 version が記録されることを検証する。 */
@EnabledIfPostgres
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderRescueAuditPostgresTest {
  private static final long STORE_ID = 1L;
  private static final String ACTOR = "operator@example.test";
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private TransactionTemplate transactions;
  private EntityManager entityManager;
  private OrderAttributionService attributionService;
  private OrderPointRollbackService rollbackService;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    entityManager = context.getBean(EntityManager.class);
    attributionService = context.getBean(OrderAttributionService.class);
    rollbackService = context.getBean(OrderPointRollbackService.class);
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void invalidationRecordsTheFlushedVersionAndRollsBackWithAFailingAudit() {
    var orderId = createOrder();
    var attributionId = createAttribution(orderId);

    attributionService.invalidate(STORE_ID, orderId, attributionId, "理由", ACTOR);

    assertThat(auditValue(attributionId.toString(), "before_values", "version")).isEqualTo("0");
    assertThat(auditValue(attributionId.toString(), "after_values", "version")).isEqualTo("1");
    assertThat(
            jdbc.queryForObject(
                "select status from t_order_attributions where id = ?",
                String.class,
                attributionId))
        .isEqualTo("INVALIDATED");

    var orderId2 = createOrder();
    var attributionId2 = createAttribution(orderId2);
    withRejectedAction(
        "ORDER_ATTRIBUTION_INVALIDATED",
        () -> {
          assertThatThrownBy(
                  () ->
                      attributionService.invalidate(
                          STORE_ID, orderId2, attributionId2, "理由", ACTOR))
              .isInstanceOf(RuntimeException.class);
          assertThat(
                  jdbc.queryForObject(
                      "select status from t_order_attributions where id = ?",
                      String.class,
                      attributionId2))
              .isEqualTo("ACTIVE");
          assertThat(auditCount(attributionId2.toString())).isZero();
        });
  }

  @Test
  void reissueRevokesThePreviousTokenAndAuditsTheNewRowAtomically() {
    var orderId = createOrder();
    var attributionId = createAttribution(orderId);
    attributionService.invalidate(STORE_ID, orderId, attributionId, "理由", ACTOR);

    attributionService.reissueReceiptToken(STORE_ID, orderId, ACTOR);

    var tokenId =
        jdbc.queryForObject(
            "select id from t_order_receipt_tokens where order_id = ? and status = 'ISSUED'",
            Long.class,
            orderId);
    assertThat(auditValue(orderId, "source_id")).isEqualTo(tokenId.toString());

    withRejectedAction(
        "ORDER_RECEIPT_TOKEN_REISSUED",
        () -> {
          assertThatThrownBy(() -> attributionService.reissueReceiptToken(STORE_ID, orderId, ACTOR))
              .isInstanceOf(RuntimeException.class);
          assertThat(
                  jdbc.queryForObject(
                      "select status from t_order_receipt_tokens where id = ?",
                      String.class,
                      tokenId))
              .isEqualTo("ISSUED");
        });
  }

  @Test
  void rollbackAuditsTheRealOrderVersionAndFailsTogetherWithTheAudit() {
    var orderId = createOrder();
    createAttribution(orderId);
    withRejectedAction(
        "ORDER_POINTS_ROLLED_BACK",
        () -> {
          assertThatThrownBy(
                  () ->
                      rollbackService.rollback(
                          STORE_ID, orderId, new OrderPointRollbackCommand(1000, 0, "理由"), ACTOR))
              .isInstanceOf(RuntimeException.class);
          assertThat(auditCount(orderId)).isZero();
        });

    rollbackService.rollback(
        STORE_ID, orderId, new OrderPointRollbackCommand(1000, 0, "理由"), ACTOR);

    assertThat(auditCount(orderId)).isEqualTo(1);
    assertThat(auditValue(orderId, "source_id")).isEqualTo("77");
  }

  private void withRejectedAction(String action, Runnable check) {
    jdbc.execute(
        "alter table t_audit_events add constraint ck_audit_test_reject check (action <> '"
            + action
            + "') not valid");
    try {
      check.run();
    } finally {
      jdbc.execute("alter table t_audit_events drop constraint ck_audit_test_reject");
    }
  }

  private String createOrder() {
    return transactions.execute(
        transaction -> {
          var order =
              Order.builder()
                  .status(OrderStatus.CONFIRMED)
                  .businessDate(LocalDate.of(2026, 10, 7))
                  .autoGrantPoints(10)
                  .build();
          order.setStoreId(STORE_ID);
          order.adoptCourse(OrderCourses.course("検証コース", 60, 1000), List.of());
          entityManager.persist(order);
          entityManager.flush();
          jdbc.update("update t_orders set status = 'COMPLETED' where id = ?", order.getId());
          entityManager.clear();
          return order.getId();
        });
  }

  private Long createAttribution(String orderId) {
    return transactions.execute(
        transaction -> {
          var row =
              OrderAttribution.onCompletion(orderId, 5L, "123456789012", OffsetDateTime.now());
          entityManager.persist(row);
          entityManager.flush();
          return row.getId();
        });
  }

  private String auditValue(String targetId, String column, String key) {
    return jdbc.queryForObject(
        "select " + column + " ->> '" + key + "' from t_audit_events where target_id = ?",
        String.class,
        targetId);
  }

  private String auditValue(String targetId, String column) {
    return jdbc.queryForObject(
        "select " + column + " from t_audit_events where target_id = ?", String.class, targetId);
  }

  private long auditCount(String targetId) {
    return jdbc.queryForObject(
        "select count(*) from t_audit_events where target_id = ?", Long.class, targetId);
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackageClasses = AuditEventRepository.class)
  @Import({AuditWriter.class, BusinessAudit.class})
  static class Config {
    @Bean
    DataSource dataSource() {
      return PostgresTestDatabase.dataSource(OrderRescueAuditPostgresTest.class);
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource source, ConfigurableListableBeanFactory beans) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(source);
      factory.setPackagesToScan("com.kizuna.order.domain", "com.kizuna.audit.domain");
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
    UserRepository users() {
      var users = mock(UserRepository.class);
      var actor =
          User.builder()
              .email(ACTOR)
              .password(UUID.randomUUID().toString())
              .displayName("受注担当")
              .enabled(true)
              .userType(UserType.STAFF)
              .roleIds(Set.of(1L))
              .storeScopeType(StoreScopeType.ALL_STORES)
              .build();
      actor.setId(9L);
      when(users.findByEmail(ACTOR)).thenReturn(Optional.of(actor));
      when(users.findById(9L)).thenReturn(Optional.of(actor));
      return users;
    }

    @Bean
    OrderAttributionService attributionService(EntityManager em, BusinessAudit audit) {
      var orders = mock(OrderRepository.class);
      when(orders.findByIdAndStoreId(anyString(), eq(STORE_ID)))
          .thenAnswer(call -> Optional.ofNullable(em.find(Order.class, call.getArgument(0))));
      when(orders.findByIdAndStoreIdForUpdate(anyString(), eq(STORE_ID)))
          .thenAnswer(
              call ->
                  Optional.ofNullable(
                      em.find(Order.class, call.getArgument(0), LockModeType.PESSIMISTIC_WRITE)));
      var tokens = mock(OrderReceiptTokenRepository.class);
      when(tokens.findByOrderIdForUpdate(anyString()))
          .thenAnswer(
              call ->
                  em.createQuery(
                          "select t from OrderReceiptToken t where t.orderId = :id order by t.id desc",
                          OrderReceiptToken.class)
                      .setParameter("id", call.getArgument(0))
                      .getResultList());
      when(tokens.saveAndFlush(any(OrderReceiptToken.class)))
          .thenAnswer(
              call -> {
                em.persist(call.getArgument(0));
                em.flush();
                return call.getArgument(0);
              });
      when(tokens.save(any(OrderReceiptToken.class)))
          .thenAnswer(
              call -> {
                em.persist(call.getArgument(0));
                em.flush();
                return call.getArgument(0);
              });
      var rows = attributions(em);
      var generator = mock(ReceiptTokenGenerator.class);
      when(generator.generate())
          .thenAnswer(
              call ->
                  new ReceiptTokenGenerator.GeneratedToken(
                      "raw", UUID.randomUUID().toString().replace("-", "")));
      return new OrderAttributionService(
          orders, rows, tokens, generator, new ActorIdentityService(users()), audit);
    }

    @Bean
    OrderPointRollbackService rollbackService(
        EntityManager em, BusinessAudit audit, OrderAttributionService attributionService) {
      var orders = mock(OrderRepository.class);
      when(orders.findByIdAndStoreIdForUpdate(anyString(), eq(STORE_ID)))
          .thenAnswer(
              call ->
                  Optional.ofNullable(
                      em.find(Order.class, call.getArgument(0), LockModeType.PESSIMISTIC_WRITE)));
      doAnswer(
              call -> {
                em.flush();
                return null;
              })
          .when(orders)
          .flush();
      var ledger = mock(PointLedgerService.class);
      when(ledger.isRolledBack(anyString())).thenReturn(false);
      when(ledger.previewRollbackForOrder(anyString()))
          .thenReturn(new PointRollbackPreview(false, 0, 0));
      when(ledger.rollbackForOrder(anyString(), anyString(), any(), any(Integer.class)))
          .thenReturn(new PointRollbackResult(0, 0));
      when(ledger.rollbackHistory(anyString()))
          .thenReturn(
              Optional.of(
                  new PointRollbackHistory("77", "理由", 9L, OffsetDateTime.now(), 0, 0, 0, 0)));
      return new OrderPointRollbackService(
          orders,
          attributionService,
          attributions(em),
          mock(OrderReceiptTokenRepository.class),
          ledger,
          new ActorIdentityService(users()),
          audit);
    }

    private static OrderAttributionRepository attributions(EntityManager em) {
      var rows = mock(OrderAttributionRepository.class);
      when(rows.findByStoreIdAndOrderIdOrderByIdDesc(eq(STORE_ID), anyString()))
          .thenAnswer(
              call ->
                  em.createQuery(
                          "select a from OrderAttribution a where a.orderId = :id order by a.id desc",
                          OrderAttribution.class)
                      .setParameter("id", call.getArgument(1))
                      .getResultList());
      when(rows.findByStoreIdAndOrderIdOrderByIdDesc(eq(STORE_ID), anyString(), any(Limit.class)))
          .thenAnswer(
              call ->
                  em.createQuery(
                          "select a from OrderAttribution a where a.orderId = :id order by a.id desc",
                          OrderAttribution.class)
                      .setParameter("id", call.getArgument(1))
                      .setMaxResults(1)
                      .getResultList());
      when(rows.saveAndFlush(any(OrderAttribution.class)))
          .thenAnswer(
              call -> {
                em.flush();
                return call.getArgument(0);
              });
      return rows;
    }
  }
}
