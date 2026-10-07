package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.customer.api.dto.ContactPermissionRequest;
import com.kizuna.customer.api.dto.ContactRequest;
import com.kizuna.customer.api.dto.CustomerCreateRequest;
import com.kizuna.customer.api.dto.CustomerMapperImpl;
import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactPurpose;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerCandidateRepository;
import com.kizuna.customer.domain.CustomerContactHistoryRepository;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.customer.domain.CustomerListRepository;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerMergeRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.shared.persistence.StoreScopeStampListener;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.PlatformUser;
import com.kizuna.user.domain.PlatformUserRepository;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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

/** 専用DBの実際の連絡先・業務履歴・共通監査を使って、関連行と親取引の原子性を検証する。 */
@EnabledIfEnvironmentVariable(named = "KIZUNA_CONTACT_AUDIT_TEST_JDBC_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerContactAuditPostgresTest {
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private TransactionTemplate transactions;
  private CustomerContactService service;
  private CustomerService customers;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    service = context.getBean(CustomerContactService.class);
    customers = context.getBean(CustomerService.class);
    jdbc.execute(
        "create unique index uq_contact_test_preference on t_customer_contacts(customer_id, type) where preferred = true and deleted = false");
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
    context.getBean(StoreContext.class).clear();
    SecurityContextHolder.clearContext();
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void initialContactAndHistoryAndAuditCommitWithTheirParentCustomer() {
    var request = request();
    var customer = customers.create(request);
    String contactId =
        jdbc.queryForObject(
            "select id from t_customer_contacts where customer_id = ?",
            String.class,
            customer.getId());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_customer_contact_history where contact_id = ?",
                Long.class,
                contactId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_audit_events where target_id = ?", Long.class, contactId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select after_values ->> 'version' from t_audit_events where target_id = ?",
                String.class,
                contactId))
        .isEqualTo("0");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_audit_events a join t_customer_contact_history h on a.source_id = h.id where a.target_id = ?",
                Long.class,
                contactId))
        .isEqualTo(1);
  }

  @Test
  void auditInsertionFailureRollsBackInitialContactsAndTheirParentCustomer() {
    var request = request();
    long contactsBefore = count("t_customer_contacts");
    long historiesBefore = count("t_customer_contact_history");
    long auditsBefore = count("t_audit_events");
    reject("action <> 'CUSTOMER_CONTACT_CREATED'");
    try {
      assertThatThrownBy(() -> customers.create(request)).isInstanceOf(RuntimeException.class);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from t_customers where name = ?", Long.class, request.getName()))
          .isZero();
      assertThat(count("t_customer_contacts")).isEqualTo(contactsBefore);
      assertThat(count("t_customer_contact_history")).isEqualTo(historiesBefore);
      assertThat(count("t_audit_events")).isEqualTo(auditsBefore);
    } finally {
      allow();
    }
  }

  @Test
  void finalOuterFailureRollsBackAlreadyFlushedInitialContactsAndTheirParent() {
    var request = request();
    long contactsBefore = count("t_customer_contacts");
    long historiesBefore = count("t_customer_contact_history");
    long auditsBefore = count("t_audit_events");
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    transaction -> {
                      customers.create(request);
                      assertThat(count("t_audit_events")).isEqualTo(auditsBefore + 1);
                      throw new IllegalStateException("親取引の後続失敗");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_customers where name = ?", Long.class, request.getName()))
        .isZero();
    assertThat(count("t_customer_contacts")).isEqualTo(contactsBefore);
    assertThat(count("t_customer_contact_history")).isEqualTo(historiesBefore);
    assertThat(count("t_audit_events")).isEqualTo(auditsBefore);
  }

  @Test
  void preferenceSwitchKeepsTheUniqueIndexAndRecordsBothCommittedVersions() {
    var customer = customers.create(request());
    String first = firstContact(customer.getId());
    String second =
        service
            .create(customer.getId(), new ContactRequest(ContactType.LINE, "other-private"))
            .id();
    service.prefer(customer.getId(), ContactType.LINE, first);
    service.prefer(customer.getId(), ContactType.LINE, second);
    assertThat(
            jdbc.queryForList(
                "select id from t_customer_contacts where customer_id = ? and preferred",
                String.class,
                customer.getId()))
        .containsExactly(second);
    var events =
        jdbc.queryForList(
            "select target_id, before_values ->> 'version' as old_version, after_values ->> 'version' as new_version, after_values ->> 'operation_id' as operation_id from t_audit_events where action = 'CUSTOMER_CONTACT_PREFERENCE_CHANGED' and target_id in (?,?) order by id desc limit 2",
            second,
            first);
    assertThat(events).hasSize(2);
    assertThat(events.getFirst())
        .containsEntry("target_id", second)
        .containsEntry("old_version", "0")
        .containsEntry("new_version", "1");
    assertThat(events.getLast())
        .containsEntry("target_id", first)
        .containsEntry("old_version", "1")
        .containsEntry("new_version", "2");
    assertThat(events.getFirst().get("operation_id"))
        .isEqualTo(events.getLast().get("operation_id"));
    long auditCount = count("t_audit_events");
    service.prefer(customer.getId(), ContactType.LINE, second);
    assertThat(count("t_audit_events")).isEqualTo(auditCount);
  }

  @Test
  void secondPreferenceAuditFailureRollsBackBothSidesAndTheFirstAudit() {
    var customer = customers.create(request());
    String first = firstContact(customer.getId());
    String second =
        service
            .create(customer.getId(), new ContactRequest(ContactType.LINE, "other-private"))
            .id();
    service.prefer(customer.getId(), ContactType.LINE, first);
    long auditsBefore = count("t_audit_events");
    long historiesBefore = count("t_customer_contact_history");
    reject("action <> 'CUSTOMER_CONTACT_PREFERENCE_CHANGED' or target_id <> '" + second + "'");
    try {
      assertThatThrownBy(() -> service.prefer(customer.getId(), ContactType.LINE, second))
          .isInstanceOf(RuntimeException.class);
      assertThat(
              jdbc.queryForList(
                  "select id from t_customer_contacts where customer_id = ? and preferred",
                  String.class,
                  customer.getId()))
          .containsExactly(first);
      assertThat(count("t_audit_events")).isEqualTo(auditsBefore);
      assertThat(count("t_customer_contact_history")).isEqualTo(historiesBefore);
      assertThat(
              jdbc.queryForObject(
                  "select version from t_customer_contacts where id = ?", Long.class, first))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "select version from t_customer_contacts where id = ?", Long.class, second))
          .isZero();
    } finally {
      allow();
    }
  }

  @Test
  void deletionAuditFailureAlsoRollsBackInheritedRestrictionsAndTheirAudits() {
    var request = request();
    var customer = customers.create(request);
    String source = firstContact(customer.getId());
    String duplicate = service.create(customer.getId(), request.getContacts().getFirst()).id();
    service.changePermission(
        customer.getId(),
        source,
        ContactPurpose.BUSINESS,
        new ContactPermissionRequest(
            ContactPermissionStatus.DENIED, "private-source", "private-reason"));
    long auditsBefore = count("t_audit_events");
    long historiesBefore = count("t_customer_contact_history");
    reject("action <> 'CUSTOMER_CONTACT_DELETED'");
    try {
      assertThatThrownBy(() -> service.delete(customer.getId(), source))
          .isInstanceOf(RuntimeException.class);
      assertThat(
              jdbc.queryForObject(
                  "select deleted from t_customer_contacts where id = ?", Boolean.class, source))
          .isFalse();
      assertThat(
              jdbc.queryForObject(
                  "select business_status from t_customer_contacts where id = ?",
                  String.class,
                  duplicate))
          .isEqualTo("UNKNOWN");
      assertThat(
              jdbc.queryForObject(
                  "select version from t_customer_contacts where id = ?", Long.class, duplicate))
          .isZero();
      assertThat(count("t_audit_events")).isEqualTo(auditsBefore);
      assertThat(count("t_customer_contact_history")).isEqualTo(historiesBefore);
    } finally {
      allow();
    }
  }

  @Test
  void valueChangeWithInheritanceRecordsVersionsBeforeAndAfterQueryAutoFlush() {
    var request = request();
    var customer = customers.create(request);
    String source = firstContact(customer.getId());
    String duplicate = service.create(customer.getId(), request.getContacts().getFirst()).id();
    service.changePermission(
        customer.getId(),
        source,
        ContactPurpose.BUSINESS,
        new ContactPermissionRequest(
            ContactPermissionStatus.DENIED, "private-source", "private-reason"));
    service.update(
        customer.getId(), source, new ContactRequest(ContactType.EMAIL, "private@example.test"));
    var sourceAudit =
        jdbc.queryForMap(
            "select before_values ->> 'version' as old_version, after_values ->> 'version' as new_version, after_values ->> 'business_status' as status from t_audit_events where target_id = ? and action = 'CUSTOMER_CONTACT_UPDATED'",
            source);
    assertThat(sourceAudit)
        .containsEntry("old_version", "1")
        .containsEntry("new_version", "2")
        .containsEntry("status", "UNKNOWN");
    var inherited =
        jdbc.queryForMap(
            "select before_values ->> 'version' as old_version, after_values ->> 'version' as new_version, after_values ->> 'business_status' as status from t_audit_events where target_id = ? and action = 'CUSTOMER_CONTACT_RESTRICTION_INHERITED'",
            duplicate);
    assertThat(inherited)
        .containsEntry("old_version", "0")
        .containsEntry("new_version", "1")
        .containsEntry("status", "DENIED");
  }

  private String firstContact(String customerId) {
    return jdbc.queryForObject(
        "select id from t_customer_contacts where customer_id = ? order by id limit 1",
        String.class,
        customerId);
  }

  private long count(String table) {
    return jdbc.queryForObject("select count(*) from " + table, Long.class);
  }

  private void reject(String predicate) {
    jdbc.execute(
        "alter table t_audit_events add constraint ck_contact_audit_test_reject check ("
            + predicate
            + ") not valid");
  }

  private void allow() {
    jdbc.execute("alter table t_audit_events drop constraint ck_contact_audit_test_reject");
  }

  private CustomerCreateRequest request() {
    var request = new CustomerCreateRequest();
    request.setName("取引検証-" + UUID.randomUUID());
    request.setContacts(
        List.of(new ContactRequest(ContactType.LINE, "private-" + UUID.randomUUID())));
    return request;
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackageClasses = AuditEventRepository.class)
  @Import({
    AuditWriter.class,
    BusinessAudit.class,
    StoreScopeStampListener.class,
    CustomerContactService.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return new DriverManagerDataSource(
          System.getenv("KIZUNA_CONTACT_AUDIT_TEST_JDBC_URL"), "postgres", "");
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource source, ConfigurableListableBeanFactory beans) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(source);
      factory.setPackagesToScan("com.kizuna.customer.domain", "com.kizuna.audit.domain");
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
    CustomerContactRepository contacts(EntityManager em) {
      return new JpaRepositoryFactory(em).getRepository(CustomerContactRepository.class);
    }

    @Bean
    CustomerContactHistoryRepository histories(EntityManager em) {
      return new JpaRepositoryFactory(em).getRepository(CustomerContactHistoryRepository.class);
    }

    @Bean
    CustomerRepository customerRepository(EntityManager em) {
      var repository = mock(CustomerRepository.class);
      when(repository.findByIdForUpdate(anyString()))
          .thenAnswer(
              call ->
                  Optional.ofNullable(
                      em.find(
                          Customer.class, call.getArgument(0), LockModeType.PESSIMISTIC_WRITE)));
      when(repository.saveAndFlush(any(Customer.class)))
          .thenAnswer(
              call -> {
                Customer customer = call.getArgument(0);
                em.persist(customer);
                em.flush();
                return customer;
              });
      return repository;
    }

    @Bean
    PlatformUserRepository users() {
      var users = mock(PlatformUserRepository.class);
      var actor =
          PlatformUser.builder()
              .email("operator@example.test")
              .password(UUID.randomUUID().toString())
              .displayName("連絡先担当")
              .enabled(true)
              .userType(UserType.STAFF)
              .roleIds(Set.of(1L))
              .storeScopeType(StoreScopeType.ALL_STORES)
              .build();
      actor.setId(9L);
      when(users.findByEmail(anyString())).thenReturn(Optional.of(actor));
      when(users.findById(anyLong())).thenReturn(Optional.of(actor));
      return users;
    }

    @Bean
    CustomerService customerService(
        CustomerRepository repository,
        CustomerContactService contacts,
        CustomerContactRepository contactRepository) {
      return new CustomerService(
          repository,
          mock(CustomerListRepository.class),
          mock(CustomerCandidateRepository.class),
          contacts,
          contactRepository,
          mock(CustomerMemberLinkRepository.class),
          mock(CustomerMergeRepository.class),
          new CustomerMapperImpl());
    }
  }
}
