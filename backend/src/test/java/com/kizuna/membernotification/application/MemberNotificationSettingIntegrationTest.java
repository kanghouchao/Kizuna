package com.kizuna.membernotification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactPurpose;
import com.kizuna.member.application.MemberLookupService;
import com.kizuna.member.domain.MemberRepository;
import com.kizuna.membernotification.domain.MemberNotificationSettingRepository;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.store.StoreShortNames;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.time.Clock;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** 実 PostgreSQL で、会員の通知設定の既定・変更・業務監査・初回の並行書き込みと、店舗の顧客連絡先からの独立を確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberNotificationSettingIntegrationTest {
  private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private MemberNotificationSettingService service;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = context.getBean(JdbcTemplate.class);
    service = context.getBean(MemberNotificationSettingService.class);
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void memberWithoutChoiceReadsUnknownForBothPurposes() {
    var member = member();

    assertThat(service.view(member.userId()))
        .isEqualTo(
            new MemberNotificationSettings(
                ContactPermissionStatus.UNKNOWN, ContactPermissionStatus.UNKNOWN));
  }

  @Test
  void choiceIsStoredPerPurposeAndAuditedOnlyWhenItChanges() {
    var member = member();

    var allowed =
        service.choose(member.userId(), ContactPurpose.BUSINESS, ContactPermissionStatus.ALLOWED);
    service.choose(member.userId(), ContactPurpose.BUSINESS, ContactPermissionStatus.ALLOWED);
    var denied =
        service.choose(member.userId(), ContactPurpose.BUSINESS, ContactPermissionStatus.DENIED);

    assertThat(allowed)
        .isEqualTo(
            new MemberNotificationSettings(
                ContactPermissionStatus.ALLOWED, ContactPermissionStatus.UNKNOWN));
    assertThat(denied.business()).isEqualTo(ContactPermissionStatus.DENIED);
    assertThat(service.view(member.userId()).marketing())
        .isEqualTo(ContactPermissionStatus.UNKNOWN);
    assertThat(
            jdbc.queryForList(
                "select before_values ->> 'status' as before_status, after_values ->> 'status' as after_status,"
                    + " after_values ->> 'purpose' as purpose, actor_id, originating_store_id"
                    + " from t_audit_events where action = 'MEMBER_NOTIFICATION_SETTING_CHANGED'"
                    + " and target_type = 'MEMBER' and target_id = ? order by id",
                Long.toString(member.memberId())))
        .containsExactly(
            audit("UNKNOWN", "ALLOWED", member.userId()),
            audit("ALLOWED", "DENIED", member.userId()));
  }

  @Test
  void changingMemberSettingLeavesLinkedCustomerContactPermissionsUntouched() {
    var member = member();
    long store = store();
    String customer = UUID.randomUUID().toString();
    jdbc.update("insert into t_customers(id,store_id,name) values (?,?,'検証顧客')", customer, store);
    jdbc.update(
        "insert into t_customer_member_links(id,store_id,customer_id,member_id,member_code,status,reason,linked_by,linked_at) values (?,?,?,?,?,'ACTIVE','MEMBER_CODE',?,current_timestamp)",
        UUID.randomUUID().toString(),
        store,
        customer,
        member.memberId(),
        member.memberCode(),
        member.userId());
    String contact = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_customer_contacts(id,store_id,customer_id,origin_customer_id,type,value,business_status,marketing_status,preferred,deleted,created_at,updated_at)"
            + " values (?,?,?,?,'EMAIL',?,'DENIED','UNKNOWN',true,false,current_timestamp,current_timestamp)",
        contact,
        store,
        customer,
        customer,
        member.email());
    var before = contactRow(contact);

    service.choose(member.userId(), ContactPurpose.BUSINESS, ContactPermissionStatus.ALLOWED);
    service.choose(member.userId(), ContactPurpose.MARKETING, ContactPermissionStatus.ALLOWED);

    assertThat(contactRow(contact)).isEqualTo(before);
    assertThat(before)
        .containsEntry("business_status", "DENIED")
        .containsEntry("marketing_status", "UNKNOWN");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_customer_contact_history where contact_id = ?",
                Long.class,
                contact))
        .isZero();
  }

  // 二つの初回の選択を会員行の待ち行列に並ばせてから一斉に通す。どちらも行の不在を読んでから挿入する順序が
  // 確実に重なるので、直列化が無ければ一意制約違反で片方が落ちる。
  @Test
  void concurrentFirstChoicesForTheSamePurposeBothLand() throws Exception {
    var member = member();
    CompletableFuture<MemberNotificationSettings> first;
    CompletableFuture<MemberNotificationSettings> second;
    try (Connection holder = context.getBean(DataSource.class).getConnection()) {
      holder.setAutoCommit(false);
      try (var lock = holder.prepareStatement("select id from t_members where id = ? for update")) {
        lock.setLong(1, member.memberId());
        lock.executeQuery().close();
      }
      first =
          CompletableFuture.supplyAsync(
              () -> choose(member.userId(), ContactPermissionStatus.ALLOWED));
      second =
          CompletableFuture.supplyAsync(
              () -> choose(member.userId(), ContactPermissionStatus.DENIED));
      awaitLockWaiters(2);
      holder.commit();
    }

    first.get(30, TimeUnit.SECONDS);
    second.get(30, TimeUnit.SECONDS);

    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_member_notification_settings where member_id = ? and purpose = 'BUSINESS'",
                Long.class,
                member.memberId()))
        .isEqualTo(1L);
  }

  @Test
  void databaseRefusesStoringUnknownAsAChoice() {
    var member = member();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into t_member_notification_settings(member_id,purpose,status) values (?,'BUSINESS','UNKNOWN')",
                    member.memberId()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_t_member_notification_settings_status");
  }

  private MemberNotificationSettings choose(long userId, ContactPermissionStatus status) {
    return service.choose(userId, ContactPurpose.BUSINESS, status);
  }

  private void awaitLockWaiters(int expected) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      Long waiting =
          jdbc.queryForObject(
              "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'",
              Long.class);
      if (waiting != null && waiting >= expected) return;
      Thread.sleep(20);
    }
    throw new AssertionError("初回の選択が会員行のロックを待っていません");
  }

  private static Map<String, Object> audit(String before, String after, long actor) {
    var row = new LinkedHashMap<String, Object>();
    row.put("before_status", before);
    row.put("after_status", after);
    row.put("purpose", "BUSINESS");
    row.put("actor_id", actor);
    row.put("originating_store_id", null);
    return row;
  }

  private Map<String, Object> contactRow(String contact) {
    return jdbc.queryForMap(
        "select business_status, marketing_status, updated_at, version from t_customer_contacts where id = ?",
        contact);
  }

  private record MemberFixture(long memberId, long userId, String email, String memberCode) {}

  private MemberFixture member() {
    String key = UUID.randomUUID().toString();
    String email = key + "@example.test";
    long user =
        jdbc.queryForObject(
            "insert into t_users(display_name,user_type,store_scope_type,email,password) values ('検証会員','MEMBER','SPECIFIC_STORES',?,'x') returning id",
            Long.class,
            email);
    String code = key.replace("-", "").substring(0, 12);
    long member =
        jdbc.queryForObject(
            "insert into t_members(member_code,user_id) values (?,?) returning id",
            Long.class,
            code,
            user);
    return new MemberFixture(member, user, email, code);
  }

  private long store() {
    return jdbc.queryForObject(
        "insert into t_stores(name,short_name,domain) values ('検証店舗',?,?) returning id",
        Long.class,
        StoreShortNames.next(),
        UUID.randomUUID() + ".invalid");
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(
      basePackageClasses = {
        UserRepository.class,
        MemberRepository.class,
        MemberNotificationSettingRepository.class,
        AuditEventRepository.class
      })
  @Import({
    MemberNotificationSettingService.class,
    MemberLookupService.class,
    BusinessAudit.class,
    AuditWriter.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return IntegrationTestDatabase.dataSource(MemberNotificationSettingIntegrationTest.class);
    }

    @Bean
    SpringLiquibase liquibase(DataSource source) {
      var migration = new SpringLiquibase();
      migration.setDataSource(source);
      migration.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
      return migration;
    }

    @Bean
    @DependsOn("liquibase")
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource source, ConfigurableListableBeanFactory beans) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(source);
      factory.setPackagesToScan("com.kizuna");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(
          Map.of(
              "hibernate.resource.beans.container",
              new SpringBeanContainer(beans),
              "hibernate.hbm2ddl.auto",
              "validate",
              "hibernate.physical_naming_strategy",
              "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
      return factory;
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
      return new JpaTransactionManager(factory);
    }

    @Bean
    JdbcTemplate jdbc(DataSource source) {
      return new JdbcTemplate(source);
    }

    @Bean
    Clock clock() {
      return Clock.system(ZONE);
    }
  }
}
