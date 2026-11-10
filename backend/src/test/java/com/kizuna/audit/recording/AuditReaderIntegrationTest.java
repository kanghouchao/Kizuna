package com.kizuna.audit.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.shared.storescope.StoreScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** 実PGで、監査の照会が店舗集合内の店舗・店舗を持たない操作・既に存在しない店舗の行だけを返すことを一覧と単件の両方で確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditReaderIntegrationTest {
  private static final long STORE1 = 1;
  private static final long STORE2 = 2;
  private static final long DELETED_STORE = 99;
  private AnnotationConfigApplicationContext context;
  private AuditReader reader;
  private long store1Row;
  private long store2Row;
  private long groupRow;
  private long deletedStoreRow;

  @BeforeAll
  void seed() {
    context = new AnnotationConfigApplicationContext(Config.class);
    reader = context.getBean(AuditReader.class);
    var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    for (long id : new long[] {STORE1, STORE2}) {
      jdbc.update(
          "insert into t_stores (id, name, short_name, domain) values (?, ?, ?, ?)",
          id,
          "店舗" + id,
          "s" + id,
          "s" + id + ".example.test");
    }
    store1Row = insertEvent(jdbc, STORE1);
    store2Row = insertEvent(jdbc, STORE2);
    groupRow = insertEvent(jdbc, null);
    deletedStoreRow = insertEvent(jdbc, DELETED_STORE);
  }

  @AfterAll
  void close() {
    context.close();
  }

  @Test
  @DisplayName("集合 {1} には店舗 1・店舗なし・削除済み店舗の行が見え、店舗 2 の行は一覧にも単件にも出ないこと")
  void storeSetOneSeesItsStoreGroupAndDeletedStoreRows() {
    var scope = new StoreScope(Set.of(STORE1));

    assertThat(listIds(scope, null)).containsExactly(deletedStoreRow, groupRow, store1Row);
    assertThat(reader.get(scope, store1Row).event().id()).isEqualTo(store1Row);
    assertThat(reader.get(scope, groupRow).event().id()).isEqualTo(groupRow);
    assertThat(reader.get(scope, deletedStoreRow).event().id()).isEqualTo(deletedStoreRow);
    assertThatThrownBy(() -> reader.get(scope, store2Row)).isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("集合 {2} には店舗 2 の行が見え、店舗 1 の行は一覧にも単件にも出ないこと")
  void storeSetTwoSeesStoreTwoButNotStoreOne() {
    var scope = new StoreScope(Set.of(STORE2));

    assertThat(listIds(scope, null)).containsExactly(deletedStoreRow, groupRow, store2Row);
    assertThat(reader.get(scope, store2Row).event().id()).isEqualTo(store2Row);
    assertThatThrownBy(() -> reader.get(scope, store1Row)).isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("空の集合には店舗なしと削除済み店舗の行だけが見え、空集合が全店に化けないこと")
  void emptySetSeesOnlyGroupAndDeletedStoreRows() {
    var scope = new StoreScope(Set.of());

    assertThat(listIds(scope, null)).containsExactly(deletedStoreRow, groupRow);
    assertThat(reader.get(scope, groupRow).event().id()).isEqualTo(groupRow);
    assertThat(reader.get(scope, deletedStoreRow).event().id()).isEqualTo(deletedStoreRow);
    assertThatThrownBy(() -> reader.get(scope, store1Row)).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> reader.get(scope, store2Row)).isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("全店舗を明示した集合にはすべての行が見えること")
  void explicitSetOfEveryStoreSeesEveryRow() {
    var scope = new StoreScope(Set.of(STORE1, STORE2));

    assertThat(listIds(scope, null))
        .containsExactly(deletedStoreRow, groupRow, store2Row, store1Row);
    assertThat(reader.get(scope, store2Row).event().id()).isEqualTo(store2Row);
  }

  @Test
  @DisplayName("操作名の絞り込みは店舗集合の絞り込みと併せて効くこと")
  void actionFilterCombinesWithStoreSet() {
    var scope = new StoreScope(Set.of(STORE1));

    assertThat(listIds(scope, "STORE_" + STORE2)).isEmpty();
    assertThat(listIds(scope, "STORE_" + STORE1)).containsExactly(store1Row);
  }

  private List<Long> listIds(StoreScope scope, String action) {
    var page =
        reader.list(
            scope, action, PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "occurredAt", "id")));
    assertThat(page.getTotalElements()).isEqualTo(page.getContent().size());
    return page.map(AuditEventSummary::id).getContent();
  }

  private static long insertEvent(JdbcTemplate jdbc, Long storeId) {
    return jdbc.queryForObject(
        """
        insert into t_audit_events (occurred_at, actor_id, actor_type, actor_name,
          originating_store_id, action, result, target_type, target_id, before_values, after_values)
        values (now(), 1, 'STAFF', '監査者', ?, ?, 'SUCCEEDED', 'STORE', '1', '{}'::jsonb, '{}'::jsonb)
        returning id
        """,
        Long.class,
        storeId,
        storeId == null ? "GROUP_OPERATION" : "STORE_" + storeId);
  }

  @Configuration
  @EnableTransactionManagement
  @EnableJpaRepositories(basePackageClasses = AuditEventRepository.class)
  @Import(AuditReader.class)
  static class Config {
    @Bean
    DataSource dataSource() {
      return IntegrationTestDatabase.dataSource(AuditReaderIntegrationTest.class);
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
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(source);
      factory.setPackagesToScan("com.kizuna.audit.domain", "com.kizuna.store.domain");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(
          Map.of(
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
  }
}
