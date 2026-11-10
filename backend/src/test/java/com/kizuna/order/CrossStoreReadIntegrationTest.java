package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.order.application.AdminOrderService;
import com.kizuna.order.domain.AdminOrderView;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.reporting.OperationalFacts;
import com.kizuna.order.reporting.OperationalReportReader;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.store.StoreShortNames;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** 実 PostgreSQL で、店舗集合を跨ぐ受注一覧と運営帳票が集合内の店舗だけを返し、空の集合は全店に化けないことを確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrossStoreReadIntegrationTest {
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private AdminOrderService orders;
  private OperationalReportReader reports;
  private long store1;
  private long store2;
  private String order1;
  private String order2;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    orders = context.getBean(AdminOrderService.class);
    reports = context.getBean(OperationalReportReader.class);
    long staff =
        jdbc.queryForObject(
            "insert into t_users(email,display_name,user_type) values (?,'検証担当','STAFF') returning id",
            Long.class,
            UUID.randomUUID() + "@example.test");
    store1 = store("店舗1");
    store2 = store("店舗2");
    order1 = completedOrder(store1, staff);
    order2 = completedOrder(store2, staff);
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  @DisplayName("集合 {1} は店舗 2 の受注・集計に届かない")
  void storeSetOneReachesOnlyStoreOne() {
    var scope = new StoreScope(Set.of(store1));

    assertThat(orderIds(scope)).containsExactly(order1);
    var facts = report(scope);
    assertThat(facts.stores()).extracting(OperationalFacts.Store::storeId).containsExactly(store1);
    assertThat(facts.orders()).extracting(OperationalFacts.Order::orderId).containsExactly(order1);
  }

  @Test
  @DisplayName("集合 {2} は店舗 1 の受注・集計に届かない")
  void storeSetTwoReachesOnlyStoreTwo() {
    var scope = new StoreScope(Set.of(store2));

    assertThat(orderIds(scope)).containsExactly(order2);
    var facts = report(scope);
    assertThat(facts.stores()).extracting(OperationalFacts.Store::storeId).containsExactly(store2);
    assertThat(facts.orders()).extracting(OperationalFacts.Order::orderId).containsExactly(order2);
  }

  @Test
  @DisplayName("空の集合はどの店舗の受注も集計も返さない")
  void emptySetReachesNoStore() {
    var scope = new StoreScope(Set.of());

    var page = orders.list(scope, PageRequest.of(0, 50));
    assertThat(page.getContent()).isEmpty();
    assertThat(page.getTotalElements()).isZero();
    var facts = report(scope);
    assertThat(facts.stores()).isEmpty();
    assertThat(facts.orders()).isEmpty();
  }

  private List<String> orderIds(StoreScope scope) {
    var page = orders.list(scope, PageRequest.of(0, 50));
    assertThat(page.getTotalElements()).isEqualTo(page.getContent().size());
    return page.map(AdminOrderView::getId).getContent();
  }

  private OperationalFacts report(StoreScope scope) {
    return reports.groupWide(scope, TODAY, TODAY);
  }

  private long store(String name) {
    return jdbc.queryForObject(
        "insert into t_stores(name,short_name,domain) values (?,?,?) returning id",
        Long.class,
        name,
        StoreShortNames.next(),
        UUID.randomUUID() + ".example.test");
  }

  private String completedOrder(long storeId, long staff) {
    String order = UUID.randomUUID().toString();
    String course = UUID.randomUUID().toString();
    String revision = UUID.randomUUID().toString();
    jdbc.update(
        "insert into t_services(id,store_id,kind,name,duration_minutes,price,remuneration,revision_number) values (?,?,'COURSE','検証コース',60,100,0,1)",
        course,
        storeId);
    jdbc.update(
        "insert into t_service_revisions(id,store_id,service_id,revision_number,operation,actor_id,occurred_at,after_kind,after_name,after_duration_minutes,after_price,after_remuneration) values (?,?,?,1,'CREATED',?,current_timestamp,'COURSE','検証コース',60,100,0)",
        revision,
        storeId,
        course,
        staff);
    jdbc.update(
        "insert into t_orders(id,store_id,business_date,course_name,course_minutes,course_service_id,course_revision_id,course_revision_number,course_price,course_remuneration,course_adoption_basis,course_adopted_at,status,completed_at,total_fee) values (?,?,?,'検証コース',60,?,?,1,100,0,'CURRENT_SETTING',current_timestamp,'COMPLETED',current_timestamp,100)",
        order,
        storeId,
        TODAY,
        course,
        revision);
    return order;
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackageClasses = OrderRepository.class)
  @Import({AdminOrderService.class, OperationalReportReader.class})
  static class Config {
    @Bean
    DataSource dataSource() {
      return IntegrationTestDatabase.dataSource(CrossStoreReadIntegrationTest.class);
    }

    @Bean
    SpringLiquibase liquibase(DataSource source) {
      var migration = new SpringLiquibase();
      migration.setDataSource(source);
      migration.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
      migration.setContexts("production");
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
    AppProperties appProperties() {
      return new AppProperties();
    }

    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }
  }
}
