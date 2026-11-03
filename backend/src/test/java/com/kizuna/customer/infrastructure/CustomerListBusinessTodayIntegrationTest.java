package com.kizuna.customer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.customer.domain.CustomerListRepository;
import com.kizuna.point.domain.PointEntry;
import com.kizuna.point.domain.PointEntryRepository;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.store.StoreShortNames;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.jdbc.core.JdbcTemplate;

/** 実 PG（Liquibase の baseline）で、顧客一覧のポイント残高が業務の時計の「本日」で期限を判じることを確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerListBusinessTodayIntegrationTest {
  private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
  // 実時間から離れた日付にし、時計を経由しない「本日」では結果が変わるようにする。
  private static final LocalDate TODAY = LocalDate.of(2025, 1, 1);

  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;

  @BeforeAll
  void connect() {
    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                IntegrationTestDatabase.springDataSourceProperties(
                    CustomerListBusinessTodayIntegrationTest.class))
            .properties(
                Map.ofEntries(
                    Map.entry("spring.config.name", "customer-list-test"),
                    Map.entry(
                        "spring.liquibase.change-log",
                        "classpath:db/changelog/db.changelog-master.yaml"),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                    Map.entry("spring.jpa.open-in-view", "false"),
                    Map.entry("logging.level.root", "WARN")))
            .run();
    jdbc = context.getBean(JdbcTemplate.class);
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void pointBalanceCountsLotsExpiringOnOrAfterTheBusinessToday() {
    long store = store();
    long member = member();
    String customer = linkedCustomer(store, member);
    long actor = staff();
    credit(member, 100, TODAY.minusDays(1), actor);
    credit(member, 10, TODAY, actor);
    credit(member, 1, TODAY.plusDays(1), actor);

    var rows =
        context
            .getBean(CustomerListRepository.class)
            .findAll(
                store,
                (root, query, cb) -> cb.equal(root.get("id"), customer),
                PageRequest.of(0, 10));

    assertThat(rows.getContent())
        .singleElement()
        .returns(11L, CustomerListRepository.Row::pointBalance);
  }

  private void credit(long member, int amount, LocalDate expiresOn, long actor) {
    context
        .getBean(PointEntryRepository.class)
        .saveAndFlush(
            PointEntry.manualAdjust(
                member,
                null,
                amount,
                "検証付与",
                expiresOn,
                List.of(),
                actor,
                UUID.randomUUID().toString()));
  }

  private long store() {
    return jdbc.queryForObject(
        "insert into t_stores(name,short_name,domain) values ('一覧検証',?,?) returning id",
        Long.class,
        StoreShortNames.next(),
        UUID.randomUUID() + ".example.test");
  }

  private long staff() {
    return jdbc.queryForObject(
        "insert into t_users(email,display_name,user_type,store_scope_type) values (?,'検証担当','STAFF','ALL_STORES') returning id",
        Long.class,
        UUID.randomUUID() + "@example.test");
  }

  private long member() {
    long user =
        jdbc.queryForObject(
            "insert into t_users(email,display_name,user_type,store_scope_type) values (?,'検証会員','MEMBER','SPECIFIC_STORES') returning id",
            Long.class,
            UUID.randomUUID() + "@example.test");
    return jdbc.queryForObject(
        "insert into t_members(member_code,user_id) values (?,?) returning id",
        Long.class,
        UUID.randomUUID().toString().substring(0, 20),
        user);
  }

  private String linkedCustomer(long store, long member) {
    String customer = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_customers(id,store_id,name) values (?,?,'一覧検証の顧客')", customer, store);
    jdbc.update(
        "insert into t_customer_member_links(id,store_id,customer_id,member_id,member_code,status,reason,linked_at)"
            + " select ?,?,?,id,member_code,'ACTIVE','MEMBER_CODE',now() from t_members where id = ?",
        UUID.randomUUID().toString(),
        store,
        customer,
        member);
    return customer;
  }

  @Configuration
  @EnableAutoConfiguration
  @EntityScan("com.kizuna")
  @EnableJpaRepositories(basePackageClasses = PointEntryRepository.class)
  @Import(JpaCustomerListRepository.class)
  static class Config {
    @Bean
    Clock clock() {
      return Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE);
    }
  }
}
