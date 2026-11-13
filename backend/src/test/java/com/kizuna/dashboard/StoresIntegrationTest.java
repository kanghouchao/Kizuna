package com.kizuna.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kizuna.dashboard.api.StoresController;
import com.kizuna.dashboard.application.StoresService;
import com.kizuna.shared.exception.CommonExceptionHandler;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import com.kizuna.store.StoreShortNames;
import com.kizuna.store.dashboard.StoreDashboardReader;
import jakarta.persistence.EntityManagerFactory;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/** 実 PostgreSQL で、担当店舗の区画が呼び出し者の店舗集合の店舗だけを稼働状態別に数えることを確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StoresIntegrationTest {
  private AnnotationConfigApplicationContext context;
  private JdbcTemplate jdbc;
  private StoreScopeHolder scopes;
  private MockMvc mvc;
  private final JsonMapper json =
      JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    scopes = context.getBean(StoreScopeHolder.class);
    mvc =
        MockMvcBuilders.standaloneSetup(context.getBean(StoresController.class))
            .setControllerAdvice(new CommonExceptionHandler())
            .setMessageConverters(new JacksonJsonHttpMessageConverter(json))
            .build();
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @BeforeEach
  void authenticateWithRegionPermission() {
    authenticate("PERM_DASHBOARD_STORES_VIEW");
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    scopes.clear();
  }

  @Test
  @DisplayName("店舗集合 {1} の従業員には店舗 2 を数えず、集合内の店舗だけを稼働状態別に数える")
  void countsOnlyStoresInTheCallersSet() throws Exception {
    long own = store("当店", false);
    // 集合外の店舗は当店と違う状態にし、漏れれば総数と内訳の両方に現れるようにする
    store("他店", true);
    store("他店2", true);
    scopes.install(() -> new StoreScope(Set.of(own)));

    JsonNode body = read();

    assertThat(body.get("total").asLong()).isEqualTo(1);
    assertThat(body.get("preparing").asLong()).isEqualTo(1);
    assertThat(body.get("active").asLong()).isZero();
  }

  @Test
  @DisplayName("集合内の準備中と稼働中を内訳に分け、合計を総数にする")
  void splitsSetByStatus() throws Exception {
    long preparing = store("準備中の店", false);
    long active1 = store("稼働中の店1", true);
    long active2 = store("稼働中の店2", true);
    store("集合外の店", false);
    scopes.install(() -> new StoreScope(Set.of(preparing, active1, active2)));

    JsonNode body = read();

    assertThat(body.get("total").asLong()).isEqualTo(3);
    assertThat(body.get("preparing").asLong()).isEqualTo(1);
    assertThat(body.get("active").asLong()).isEqualTo(2);
  }

  @Test
  @DisplayName("空の店舗集合は全店と読み替えず 0 を返す")
  void emptySetIsZero() throws Exception {
    store("どこかの店", true);
    store("どこかの店2", false);
    scopes.install(() -> new StoreScope(Set.of()));

    JsonNode body = read();

    assertThat(body.get("total").asLong()).isZero();
    assertThat(body.get("preparing").asLong()).isZero();
    assertThat(body.get("active").asLong()).isZero();
  }

  @Test
  @DisplayName("店舗集合を解決できない要求は 403")
  void unresolvedScopeIsForbidden() throws Exception {
    scopes.install(() -> null);

    mvc.perform(get("/dashboard/stores")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("区画の権限が無ければ、店舗の管理の権限があっても 403")
  void withoutRegionPermissionIsForbidden() throws Exception {
    authenticate("PERM_STORE_MANAGE");
    long own = store("当店", true);
    scopes.install(() -> new StoreScope(Set.of(own)));

    mvc.perform(get("/dashboard/stores")).andExpect(status().isForbidden());
  }

  private JsonNode read() throws Exception {
    var response =
        mvc.perform(get("/dashboard/stores")).andExpect(status().isOk()).andReturn().getResponse();
    return json.readTree(response.getContentAsString());
  }

  private static void authenticate(String... authorities) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "staff@example.test",
                "unused",
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
  }

  private long store(String name, boolean active) {
    return jdbc.queryForObject(
        "insert into t_stores(name,short_name,domain,status) values (?,?,?,?) returning id",
        Long.class,
        name,
        StoreShortNames.next(),
        UUID.randomUUID() + ".example.test",
        active ? "ACTIVE" : "PREPARING");
  }

  @Configuration
  @EnableTransactionManagement
  @EnableMethodSecurity
  @Import({
    StoresController.class,
    StoresService.class,
    StoreDashboardReader.class,
    StoreScopeHolder.class
  })
  static class Config {
    @Bean
    DataSource dataSource() {
      return IntegrationTestDatabase.dataSource(StoresIntegrationTest.class);
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
  }
}
