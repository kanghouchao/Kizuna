package com.kizuna.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.order.api.dto.OrderCancellationRequest;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCourses;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.shared.persistence.StoreScopeStampListener;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.LocalDate;
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

/** 専用の使い捨て DB で、実際の受注行と監査行が Spring の同じ取引で確定・巻戻しされることを検証する。 */
@EnabledIfEnvironmentVariable(named = "KIZUNA_ORDER_AUDIT_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderAuditPostgresTest {
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private TransactionTemplate transactions;
  private EntityManager entityManager;
  private OrderService service;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    entityManager = context.getBean(EntityManager.class);
    service = context.getBean(OrderService.class);
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void cancellationAndItsAuditCommitTogetherWithDatabaseVersions() {
    var id = createOrder();
    service.cancel(id, cancellation(), "operator@example.test");

    assertThat(status(id)).isEqualTo("CANCELLED");
    assertThat(auditCount(id)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select before_values ->> 'status' from t_audit_events where target_id = ?",
                String.class,
                id))
        .isEqualTo("CONFIRMED");
    assertThat(
            jdbc.queryForObject(
                "select after_values ->> 'status' from t_audit_events where target_id = ?",
                String.class,
                id))
        .isEqualTo("CANCELLED");
    assertThat(
            jdbc.queryForObject(
                "select after_values ->> 'version' from t_audit_events where target_id = ?",
                String.class,
                id))
        .isEqualTo("1");
    assertThatThrownBy(() -> service.cancel(id, cancellation(), "operator@example.test"))
        .isInstanceOf(RuntimeException.class);
    assertThat(auditCount(id)).isEqualTo(1);
  }

  @Test
  void databaseAuditFailureRollsBackTheAlreadyFlushedBusinessChange() {
    var id = createOrder();
    jdbc.execute(
        "alter table t_audit_events add constraint ck_audit_test_reject check (action <> 'ORDER_CANCELLED') not valid");
    try {
      assertThatThrownBy(() -> service.cancel(id, cancellation(), "operator@example.test"))
          .isInstanceOf(RuntimeException.class);
      assertThat(status(id)).isEqualTo("CONFIRMED");
      assertThat(auditCount(id)).isZero();
      assertThat(jdbc.queryForObject("select version from t_orders where id = ?", Long.class, id))
          .isZero();
    } finally {
      jdbc.execute("alter table t_audit_events drop constraint ck_audit_test_reject");
    }
  }

  @Test
  void outerRollbackRemovesBothTheBusinessChangeAndTheWrittenAudit() {
    var id = createOrder();
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    transaction -> {
                      service.cancel(id, cancellation(), "operator@example.test");
                      assertThat(auditCount(id)).isEqualTo(1);
                      throw new IllegalStateException("同じ業務取引の後続失敗");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(status(id)).isEqualTo("CONFIRMED");
    assertThat(auditCount(id)).isZero();
  }

  private String createOrder() {
    return transactions.execute(
        transaction -> {
          var order =
              Order.builder()
                  .status(OrderStatus.CONFIRMED)
                  .businessDate(LocalDate.of(2026, 10, 7))
                  .build();
          order.setStoreId(1L);
          order.adoptCourse(OrderCourses.course("検証コース", 60, 1000), List.of());
          entityManager.persist(order);
          entityManager.flush();
          return order.getId();
        });
  }

  private OrderCancellationRequest cancellation() {
    var request = new OrderCancellationRequest();
    request.setReason("元受注にだけ残す理由");
    return request;
  }

  private String status(String id) {
    return jdbc.queryForObject("select status from t_orders where id = ?", String.class, id);
  }

  private long auditCount(String id) {
    return jdbc.queryForObject(
        "select count(*) from t_audit_events where target_id = ?", Long.class, id);
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackageClasses = AuditEventRepository.class)
  @Import({AuditWriter.class, BusinessAudit.class, StoreScopeStampListener.class})
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
    OrderService orders(EntityManager em, BusinessAudit audit) throws ReflectiveOperationException {
      var repository = mock(OrderRepository.class);
      when(repository.findScopedByIdForUpdate(anyString()))
          .thenAnswer(
              call ->
                  Optional.ofNullable(
                      em.find(Order.class, call.getArgument(0), LockModeType.PESSIMISTIC_WRITE)));
      when(repository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));
      doAnswer(
              call -> {
                em.flush();
                return null;
              })
          .when(repository)
          .flush();
      var actors = mock(ActorIdentityService.class);
      when(actors.requireUserId("operator@example.test")).thenReturn(9L);
      var constructor = OrderService.class.getConstructors()[0];
      var types = constructor.getParameterTypes();
      var arguments = new Object[types.length];
      for (int i = 0; i < types.length; i++) {
        arguments[i] =
            types[i] == OrderRepository.class
                ? repository
                : types[i] == BusinessAudit.class
                    ? audit
                    : types[i] == ActorIdentityService.class ? actors : mock(types[i]);
      }
      return (OrderService) constructor.newInstance(arguments);
    }
  }
}
