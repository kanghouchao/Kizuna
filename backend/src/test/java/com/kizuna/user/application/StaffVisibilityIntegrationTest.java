package com.kizuna.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.store.StoreShortNames;
import com.kizuna.user.domain.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** 実 PostgreSQL で、従業員の一覧が集合の交わる者と集合が空の者を返し、一覧に出た行は開けることを確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StaffVisibilityIntegrationTest {
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private AdminStaffService staff;
  private StoreScopeHolder scopes;
  private long role;
  private long storeA;
  private long storeB;
  private String actorA;
  private long inA;
  private long inB;
  private long inBoth;
  private long empty;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    staff = context.getBean(AdminStaffService.class);
    scopes = context.getBean(StoreScopeHolder.class);
    role =
        jdbc.queryForObject(
            "insert into t_roles(name,is_system) values (?,false) returning id",
            Long.class,
            UUID.randomUUID().toString());
    storeA = store();
    storeB = store();
    actorA = UUID.randomUUID() + "@staff.test";
    user(actorA, storeA);
    inA = user(UUID.randomUUID() + "@staff.test", storeA);
    inB = user(UUID.randomUUID() + "@staff.test", storeB);
    inBoth = user(UUID.randomUUID() + "@staff.test", storeA, storeB);
    empty = user(UUID.randomUUID() + "@staff.test");
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  @DisplayName("一覧は行使者の集合と交わる者に加えて集合が空の者を返し、交わらない者は返さない")
  void listReturnsIntersectingAndEmptySetStaff() {
    var ids = listed(Set.of(storeA), null);

    assertThat(ids).contains(inA, inBoth, empty).doesNotContain(inB);
  }

  @Test
  @DisplayName("店舗での絞り込みはその店舗を含む者だけを返し、集合が空の者は含めない")
  void storeFilterExcludesEmptySetStaff() {
    var ids = listed(Set.of(storeA), storeA);

    assertThat(ids).contains(inA, inBoth).doesNotContain(empty, inB);
  }

  @Test
  @DisplayName("集合が空の者は一覧に出て、集合が交わらない行使者でも 404 にならずに開ける")
  void emptySetStaffOpensForEveryActor() {
    assertThat(as(Set.of(storeB), () -> staff.get(empty, actorA)).storeIds()).isEmpty();
    assertThat(as(Set.of(storeA), () -> staff.get(empty, actorA)).editable()).isTrue();
    assertThatThrownBy(() -> as(Set.of(storeA), () -> staff.get(inB, actorA)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("集合が空の行使者には集合が空の者だけが見える")
  void emptySetActorSeesOnlyEmptySetStaff() {
    var ids = listed(Set.of(), null);

    assertThat(ids).contains(empty).doesNotContain(inA, inB, inBoth);
    assertThatThrownBy(() -> as(Set.of(), () -> staff.get(inA, actorA)))
        .isInstanceOf(NotFoundException.class);
  }

  private List<Long> listed(Set<Long> actorStores, Long storeFilter) {
    return as(
        actorStores,
        () ->
            staff
                .list(null, storeFilter, PageRequest.of(0, 100), actorA)
                .map(StaffView::id)
                .getContent());
  }

  private <T> T as(Set<Long> actorStores, Supplier<T> call) {
    scopes.install(() -> new StoreScope(actorStores));
    try {
      return call.get();
    } finally {
      scopes.clear();
    }
  }

  private long store() {
    return jdbc.queryForObject(
        "insert into t_stores(name,short_name,domain) values ('検証店',?,?) returning id",
        Long.class,
        StoreShortNames.next(),
        UUID.randomUUID() + ".example.test");
  }

  private long user(String email, long... stores) {
    long id =
        jdbc.queryForObject(
            "insert into t_users(email,password,display_name,user_type) values (?,'x','検証職員','STAFF') returning id",
            Long.class,
            email);
    jdbc.update("insert into t_user_roles(user_id,role_id) values (?,?)", id, role);
    for (long store : stores)
      jdbc.update("insert into t_user_stores(user_id,store_id) values (?,?)", id, store);
    return id;
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackageClasses = UserRepository.class)
  @Import({
    AdminStaffService.class,
    RoleManageHolderGuard.class,
    CredentialOperations.class,
    StoreScopeHolder.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return IntegrationTestDatabase.dataSource(StaffVisibilityIntegrationTest.class);
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
    BusinessAudit businessAudit() {
      return mock(BusinessAudit.class);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder();
    }
  }
}
