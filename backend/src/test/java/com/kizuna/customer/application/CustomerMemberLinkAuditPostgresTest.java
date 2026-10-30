package com.kizuna.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerMemberLinkRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.domain.MemberIdentityView;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.persistence.EnabledIfPostgres;
import com.kizuna.shared.persistence.PostgresTestDatabase;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.StoreScopeType;
import com.kizuna.user.domain.User;
import com.kizuna.user.domain.UserRepository;
import com.kizuna.user.domain.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.support.PersistenceExceptionTranslationInterceptor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** 実PGの関連区間・一意制約・共通監査を同じ取引で確定し、失敗時に双方を戻す。 */
@EnabledIfPostgres
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerMemberLinkAuditPostgresTest {
  private static final AtomicLong MEMBERS = new AtomicLong(100);
  private static final Long STORE_ID = 1L;
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private CustomerMemberLinkService links;
  private CustomerRepository customers;
  private TransactionTemplate transactions;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    links = context.getBean(CustomerMemberLinkService.class);
    customers = context.getBean(CustomerRepository.class);
    transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    jdbc.execute(
        "create unique index uq_link_test_customer on t_customer_member_links(store_id,customer_id) where status='ACTIVE'");
    jdbc.execute(
        "create unique index uq_link_test_member on t_customer_member_links(store_id,member_id) where status='ACTIVE'");
    jdbc.execute(
        "alter table t_customer_member_links add constraint fk_link_test_customer foreign key(customer_id) references t_customers(id)");
  }

  @BeforeEach
  void authenticate() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("operator@example.test", null, List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void successfulReplacementAndReleaseKeepRealVersionsAndSafeSnapshots() {
    String customer = customer();
    String code = memberCode();
    String first =
        links.link(STORE_ID, customer, code, null, "秘密の建立理由", "operator@example.test").id();
    String second =
        links
            .link(STORE_ID, customer, memberCode(), first, "秘密の変更理由", "operator@example.test")
            .id();
    links.unlink(STORE_ID, customer, second, "秘密の解除理由", "operator@example.test");
    var events =
        jdbc.queryForList(
            "select action,target_id,source_id,before_values ->> 'version' as old_version,after_values ->> 'version' as new_version from t_audit_events where source_id=? order by id",
            customer);
    assertThat(events).hasSize(4);
    assertThat(events.get(0))
        .containsEntry("target_id", first)
        .containsEntry("old_version", null)
        .containsEntry("new_version", "0");
    assertThat(events.get(1))
        .containsEntry("action", "CUSTOMER_MEMBER_LINK_RELEASED")
        .containsEntry("target_id", first)
        .containsEntry("old_version", "0")
        .containsEntry("new_version", "1");
    assertThat(events.get(2)).containsEntry("target_id", second).containsEntry("new_version", "0");
    assertThat(events.get(3))
        .containsEntry("target_id", second)
        .containsEntry("old_version", "0")
        .containsEntry("new_version", "1");
    assertThat(
            jdbc.queryForList(
                "select status from t_customer_member_links where customer_id=?",
                String.class,
                customer))
        .containsOnly("RELEASED")
        .hasSize(2);
    String payload =
        jdbc.queryForList(
                "select before_values,after_values from t_audit_events where source_id=?", customer)
            .toString();
    assertThat(payload)
        .doesNotContain(
            "秘密",
            code,
            "member_code",
            "operation_reason",
            "release_reason",
            "operator@example.test");
    assertThat(links.history(STORE_ID, customer, null, 20).content()).hasSize(2);
  }

  @Test
  void auditFailureRollsBackInitialLink() {
    String customer = customer();
    reject("CUSTOMER_MEMBER_LINK_CREATED");
    try {
      assertThatThrownBy(
              () ->
                  links.link(STORE_ID, customer, memberCode(), null, "秘密", "operator@example.test"))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      allow();
    }
    assertThat(links.history(STORE_ID, customer, null, 20).content()).isEmpty();
    assertThat(auditCount(customer)).isZero();
  }

  @Test
  void eitherReplacementAuditFailureRestoresOldActiveInterval() {
    for (String action : List.of("CUSTOMER_MEMBER_LINK_RELEASED", "CUSTOMER_MEMBER_LINK_CREATED")) {
      String customer = customer();
      String first =
          links.link(STORE_ID, customer, memberCode(), null, "秘密", "operator@example.test").id();
      reject(action);
      try {
        assertThatThrownBy(
                () ->
                    links.link(
                        STORE_ID, customer, memberCode(), first, "秘密の変更", "operator@example.test"))
            .isInstanceOf(DataIntegrityViolationException.class);
      } finally {
        allow();
      }
      assertActiveUnchanged(customer, first);
    }
  }

  @Test
  void auditFailureRollsBackExplicitRelease() {
    String customer = customer();
    String first =
        links.link(STORE_ID, customer, memberCode(), null, "秘密", "operator@example.test").id();
    reject("CUSTOMER_MEMBER_LINK_RELEASED");
    try {
      assertThatThrownBy(
              () -> links.unlink(STORE_ID, customer, first, "秘密の解除", "operator@example.test"))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      allow();
    }
    assertActiveUnchanged(customer, first);
  }

  @Test
  void outerRollbackRestoresLinkAndBothReplacementEvents() {
    String customer = customer();
    String first =
        links.link(STORE_ID, customer, memberCode(), null, "秘密", "operator@example.test").id();
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      links.link(
                          STORE_ID,
                          customer,
                          memberCode(),
                          first,
                          "秘密の変更",
                          "operator@example.test");
                      throw new IllegalStateException("外側の取引を中止");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertActiveUnchanged(customer, first);
  }

  @Test
  void actorAndStoreComeFromContextForBothReplacementEvents() {
    String customer = customer();
    String first =
        links.link(STORE_ID, customer, memberCode(), null, "秘密", "operator@example.test").id();
    var jwt =
        Jwt.withTokenValue(UUID.randomUUID().toString())
            .header("alg", "HS256")
            .subject("operator@example.test")
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    links.link(STORE_ID, customer, memberCode(), first, "秘密の変更", "operator@example.test");
    var events =
        jdbc.queryForList(
            "select actor_id,store_id from t_audit_events where source_id=? order by id", customer);
    assertThat(events).hasSize(3);
    for (var event : events.subList(1, 3)) {
      assertThat(event).containsEntry("actor_id", 9L).containsEntry("store_id", 1L);
    }
  }

  @Test
  void concurrentReplacementOfOneCustomerRejectsStaleExpectedLink() throws Exception {
    String customer = customer();
    String first =
        links.link(STORE_ID, customer, memberCode(), null, "秘密", "operator@example.test").id();
    var start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var one = workers.submit(() -> replaceAfter(start, customer, memberCode(), first));
      var two = workers.submit(() -> replaceAfter(start, customer, memberCode(), first));
      start.countDown();
      var results = Arrays.asList(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
      assertThat(results.stream().filter(value -> value == null).count()).isEqualTo(1);
      assertThat(results.stream().filter(value -> value instanceof ConflictException).count())
          .isEqualTo(1);
    }
    assertThat(links.history(STORE_ID, customer, null, 20).content()).hasSize(2);
    assertThat(auditCount(customer)).isEqualTo(3);
    assertThat(links.current(STORE_ID, customer).id()).isNotEqualTo(first);
  }

  @Test
  void uniqueMemberRaceRollsBackLosersReleaseAndAudit() throws Exception {
    String customerOne = customer(), customerTwo = customer();
    String oldOne =
        links.link(STORE_ID, customerOne, memberCode(), null, "秘密", "operator@example.test").id();
    String oldTwo =
        links.link(STORE_ID, customerTwo, memberCode(), null, "秘密", "operator@example.test").id();
    String targetCode = memberCode();
    long targetMember = Long.parseLong(targetCode.substring("private-code-".length()));
    jdbc.execute(
        "create function link_test_gate() returns trigger language plpgsql as $$ begin if NEW.member_id = "
            + targetMember
            + " then perform pg_advisory_xact_lock(1009441); end if; return NEW; end $$");
    jdbc.execute(
        "create trigger link_test_gate before insert on t_customer_member_links for each row execute function link_test_gate()");
    try (var blocker = context.getBean(DataSource.class).getConnection();
        var statement = blocker.createStatement();
        var workers = Executors.newFixedThreadPool(2)) {
      statement.execute("select pg_advisory_lock(1009441)");
      var start = new CountDownLatch(1);
      var one = workers.submit(() -> replaceAfter(start, customerOne, targetCode, oldOne));
      var two = workers.submit(() -> replaceAfter(start, customerTwo, targetCode, oldTwo));
      start.countDown();
      try {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (jdbc.queryForObject(
                    "select count(*) from pg_locks where locktype='advisory' and objid=1009441 and not granted",
                    Integer.class)
                < 2
            && System.nanoTime() < until) {
          Thread.sleep(20);
        }
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from pg_locks where locktype='advisory' and objid=1009441 and not granted",
                    Integer.class))
            .isEqualTo(2);
      } finally {
        statement.execute("select pg_advisory_unlock(1009441)");
      }
      Throwable firstResult = one.get(15, TimeUnit.SECONDS),
          secondResult = two.get(15, TimeUnit.SECONDS);
      var results = Arrays.asList(firstResult, secondResult);
      assertThat(results.stream().filter(value -> value == null).count()).isEqualTo(1);
      assertThat(
              results.stream()
                  .filter(value -> value instanceof DataIntegrityViolationException)
                  .count())
          .isEqualTo(1);
      String loser = firstResult == null ? customerTwo : customerOne;
      assertActiveUnchanged(loser, firstResult == null ? oldTwo : oldOne);
      String winner = firstResult == null ? customerOne : customerTwo;
      assertThat(links.history(STORE_ID, winner, null, 20).content()).hasSize(2);
      assertThat(auditCount(winner)).isEqualTo(3);
      assertThat(links.current(STORE_ID, winner).memberCode()).isEqualTo(targetCode);
    } finally {
      jdbc.execute("drop trigger link_test_gate on t_customer_member_links");
      jdbc.execute("drop function link_test_gate()");
    }
  }

  private Throwable replaceAfter(
      CountDownLatch start, String customer, String code, String expected) {
    authenticate();
    try {
      if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("開始待機が時間切れ");
      links.link(STORE_ID, customer, code, expected, "秘密の並行変更", "operator@example.test");
      return null;
    } catch (Throwable failure) {
      return failure;
    } finally {
      clear();
    }
  }

  private void assertActiveUnchanged(String customer, String first) {
    assertThat(links.current(STORE_ID, customer).id()).isEqualTo(first);
    assertThat(links.history(STORE_ID, customer, null, 20).content()).hasSize(1);
    assertThat(
            jdbc.queryForMap(
                "select status,version,released_at,released_by from t_customer_member_links where id=?",
                first))
        .containsEntry("status", "ACTIVE")
        .containsEntry("version", 0L)
        .containsEntry("released_at", null)
        .containsEntry("released_by", null);
    assertThat(auditCount(customer)).isEqualTo(1);
  }

  private String customer() {
    return transactions.execute(
        status -> {
          var customer = Customer.builder().name("秘密の顧客-" + UUID.randomUUID()).build();
          customer.setStoreId(STORE_ID);
          return customers.saveAndFlush(customer).getId();
        });
  }

  private static String memberCode() {
    return "private-code-" + MEMBERS.incrementAndGet();
  }

  private long auditCount(String customer) {
    return jdbc.queryForObject(
        "select count(*) from t_audit_events where source_id=?", Long.class, customer);
  }

  private void reject(String action) {
    jdbc.execute(
        "alter table t_audit_events add constraint ck_link_test_audit check(action <> '"
            + action
            + "') not valid");
  }

  private void allow() {
    jdbc.execute("alter table t_audit_events drop constraint ck_link_test_audit");
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackageClasses = AuditEventRepository.class)
  @Import({
    AuditWriter.class,
    BusinessAudit.class,
    CustomerMemberLinkService.class,
    MemberLookupService.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return PostgresTestDatabase.dataSource(CustomerMemberLinkAuditPostgresTest.class);
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource source, ConfigurableListableBeanFactory beans) {
      var f = new LocalContainerEntityManagerFactoryBean();
      f.setDataSource(source);
      f.setPackagesToScan(
          "com.kizuna.customer.domain",
          "com.kizuna.audit.domain",
          "com.kizuna.user.domain",
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
    CustomerRepository customers(EntityManager em) {
      return repositoryFactory(em).getRepository(CustomerRepository.class);
    }

    @Bean
    CustomerMemberLinkRepository links(EntityManager em) {
      return repositoryFactory(em).getRepository(CustomerMemberLinkRepository.class);
    }

    private JpaRepositoryFactory repositoryFactory(EntityManager em) {
      var factory = new JpaRepositoryFactory(em);
      factory.addRepositoryProxyPostProcessor(
          (proxy, information) ->
              proxy.addAdvice(
                  new PersistenceExceptionTranslationInterceptor(new HibernateJpaDialect())));
      return factory;
    }

    @Bean
    MemberRepository members() {
      var r = mock(MemberRepository.class);
      when(r.findIdentityByMemberCode(anyString()))
          .thenAnswer(
              call -> {
                String code = call.getArgument(0);
                long id = Long.parseLong(code.substring("private-code-".length()));
                return Optional.of(
                    new MemberIdentityView() {
                      public Long getId() {
                        return id;
                      }

                      public String getMemberCode() {
                        return code;
                      }
                    });
              });
      when(r.lockIdForBalanceConfirmation(anyLong()))
          .thenAnswer(call -> Optional.of(call.getArgument(0)));
      return r;
    }

    @Bean
    UserRepository users() {
      var r = mock(UserRepository.class);
      var actor =
          User.builder()
              .email("operator@example.test")
              .password(UUID.randomUUID().toString())
              .displayName("監査担当")
              .userType(UserType.STAFF)
              .enabled(true)
              .roleIds(Set.of(1L))
              .storeScopeType(StoreScopeType.ALL_STORES)
              .build();
      actor.setId(9L);
      when(r.findByEmail(anyString())).thenReturn(Optional.of(actor));
      when(r.findById(anyLong())).thenReturn(Optional.of(actor));
      return r;
    }
  }
}
