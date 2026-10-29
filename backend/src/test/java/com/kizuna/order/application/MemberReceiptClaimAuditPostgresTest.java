package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.application.MemberLookupService.MemberLookup;
import com.kizuna.member.application.MemberRankService;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderAttribution;
import com.kizuna.order.domain.OrderAttributionRepository;
import com.kizuna.order.domain.OrderCourses;
import com.kizuna.order.domain.OrderReceiptToken;
import com.kizuna.order.domain.OrderReceiptTokenRepository;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.infrastructure.ReceiptTokenGenerator;
import com.kizuna.point.application.BenefitGrantService;
import com.kizuna.point.application.PointLedgerService;
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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 専用の使い捨て DB で、申領の帰属・付与仕訳・ランク同期・監査が同じ取引で確定し、監査の失敗で全て巻き戻ることを検証する。
 * 仕訳とランクは補助表への書き込みで代用し、取引に参加していることだけを見る。
 */
@EnabledIfEnvironmentVariable(named = "KIZUNA_ORDER_AUDIT_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberReceiptClaimAuditPostgresTest {
  private static final long STORE_ID = 1L;
  private static final long USER_ID = 11L;
  private static final long MEMBER_ID = 5L;
  private static final String EMAIL = "member@example.test";
  private static final String RAW = "raw-token";
  private static volatile String currentDigest;
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private TransactionTemplate transactions;
  private EntityManager entityManager;
  private MemberReceiptClaimService service;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    jdbc.execute("create table claim_test_effects (kind text, order_id text)");
    transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    entityManager = context.getBean(EntityManager.class);
    service = context.getBean(MemberReceiptClaimService.class);
  }

  @AfterAll
  void close() {
    if (context != null) {
      jdbc.execute("drop table if exists claim_test_effects");
      context.close();
    }
  }

  @Test
  void claimCommitsAttributionLedgerRankAndAuditTogether() {
    var orderId = createOrder();

    service.claim(EMAIL, RAW);

    assertThat(count("t_order_attributions", orderId)).isEqualTo(1);
    assertThat(effects(orderId)).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select status from t_order_receipt_tokens where order_id = ?",
                String.class,
                orderId))
        .isEqualTo("CLAIMED");
    var attributionId =
        jdbc.queryForObject(
            "select id from t_order_attributions where order_id = ?", Long.class, orderId);
    assertThat(auditValue(orderId, "action")).isEqualTo("RECEIPT_CLAIMED");
    assertThat(auditValue(orderId, "source_id")).isEqualTo(attributionId.toString());
    assertThat(auditValue(orderId, "after_values ->> 'version'")).isEqualTo("0");
    assertThat(auditValue(orderId, "after_values ->> 'rank_changed'")).isEqualTo("true");
  }

  @Test
  void aFailingAuditRollsBackTheAttributionLedgerRankAndToken() {
    var orderId = createOrder();
    jdbc.execute(
        "alter table t_audit_events add constraint ck_audit_test_reject"
            + " check (action <> 'RECEIPT_CLAIMED') not valid");
    try {
      assertThatThrownBy(() -> service.claim(EMAIL, RAW)).isInstanceOf(RuntimeException.class);
    } finally {
      jdbc.execute("alter table t_audit_events drop constraint ck_audit_test_reject");
    }

    assertThat(count("t_order_attributions", orderId)).isZero();
    assertThat(effects(orderId)).isZero();
    assertThat(count("t_audit_events", orderId)).isZero();
    assertThat(
            jdbc.queryForObject(
                "select status from t_order_receipt_tokens where order_id = ?",
                String.class,
                orderId))
        .isEqualTo("ISSUED");
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
          currentDigest = "digest-" + UUID.randomUUID();
          entityManager.persist(
              OrderReceiptToken.issueFor(order.getId(), currentDigest, 120, OffsetDateTime.now()));
          entityManager.flush();
          entityManager.clear();
          return order.getId();
        });
  }

  private long count(String table, String orderId) {
    var column = table.equals("t_audit_events") ? "target_id" : "order_id";
    return jdbc.queryForObject(
        "select count(*) from " + table + " where " + column + " = ?", Long.class, orderId);
  }

  private long effects(String orderId) {
    return jdbc.queryForObject(
        "select count(*) from claim_test_effects where order_id = ?", Long.class, orderId);
  }

  private String auditValue(String orderId, String expression) {
    return jdbc.queryForObject(
        "select " + expression + " from t_audit_events where target_id = ?", String.class, orderId);
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackageClasses = AuditEventRepository.class)
  @Import({AuditWriter.class, BusinessAudit.class})
  static class Config {
    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
          System.getenv("KIZUNA_ORDER_AUDIT_TEST_JDBC_URL"), "postgres", "");
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
      var member =
          User.builder()
              .email(EMAIL)
              .password(UUID.randomUUID().toString())
              .displayName("会員")
              .enabled(true)
              .userType(UserType.MEMBER)
              .storeScopeType(StoreScopeType.SPECIFIC_STORES)
              .storeIds(Set.of())
              .build();
      member.setId(USER_ID);
      when(users.findByEmail(EMAIL)).thenReturn(Optional.of(member));
      when(users.findById(USER_ID)).thenReturn(Optional.of(member));
      return users;
    }

    private static AttributionMaterializer materializer(EntityManager em) {
      var attributions = mock(OrderAttributionRepository.class);
      when(attributions.save(any(OrderAttribution.class)))
          .thenAnswer(
              call -> {
                em.persist(call.getArgument(0));
                em.flush();
                return call.getArgument(0);
              });
      var ledger = mock(PointLedgerService.class);
      when(ledger.grantPlannedForOrder(anyLong(), anyString(), any(), any(Integer.class), any()))
          .thenAnswer(
              call -> {
                effect(em, "LEDGER", call.getArgument(1));
                return 77L;
              });
      var ranks = mock(MemberRankService.class);
      when(ranks.syncOnAttribution(anyLong(), any(), anyLong(), any()))
          .thenAnswer(
              call -> {
                effect(
                    em, "RANK", em.find(OrderAttribution.class, call.getArgument(2)).getOrderId());
                return true;
              });
      return new AttributionMaterializer(
          attributions,
          ledger,
          mock(BenefitGrantService.class),
          ranks,
          mock(MemberLookupService.class));
    }

    @Bean
    MemberReceiptClaimService claimService(EntityManager em, BusinessAudit audit) {
      var tokens = mock(OrderReceiptTokenRepository.class);
      when(tokens.findByTokenDigest(anyString()))
          .thenAnswer(
              call ->
                  em.createQuery(
                          "select t from OrderReceiptToken t where t.tokenDigest = :d"
                              + " order by t.id desc",
                          OrderReceiptToken.class)
                      .setParameter("d", call.getArgument(0))
                      .setMaxResults(1)
                      .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                      .getResultStream()
                      .findFirst());
      var orders = mock(OrderRepository.class);
      when(orders.findByReceiptTokenDigest(anyString()))
          .thenAnswer(
              call ->
                  em.createQuery(
                          "select o from Order o where o.id = (select t.orderId from"
                              + " OrderReceiptToken t where t.tokenDigest = :d order by t.id desc"
                              + " limit 1)",
                          Order.class)
                      .setParameter("d", call.getArgument(0))
                      .getResultStream()
                      .findFirst());
      var generator = mock(ReceiptTokenGenerator.class);
      when(generator.digest(RAW)).thenAnswer(call -> currentDigest);
      var members = mock(MemberLookupService.class);
      when(members.findByUserId(USER_ID))
          .thenReturn(Optional.of(new MemberLookup(MEMBER_ID, "123456789012")));
      return new MemberReceiptClaimService(
          tokens,
          orders,
          generator,
          mock(PointLedgerService.class),
          new ActorIdentityService(users()),
          members,
          materializer(em),
          audit);
    }

    private static void effect(EntityManager em, String kind, Object orderId) {
      em.createNativeQuery("insert into claim_test_effects values (?1, ?2)")
          .setParameter(1, kind)
          .setParameter(2, orderId)
          .executeUpdate();
    }
  }
}
