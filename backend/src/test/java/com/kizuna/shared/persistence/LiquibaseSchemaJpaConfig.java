package com.kizuna.shared.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.hibernate.SpringBeanContainer;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

/** テストクラス専用のスキーマに本番と同じ changelog を当て、その上に JPA を立てる。外部キーも本番どおりに付く。 */
public abstract class LiquibaseSchemaJpaConfig {

  /** スキーマの持ち主になるテストクラス。 */
  protected abstract Class<?> owner();

  @Bean
  DataSource dataSource() {
    return IntegrationTestDatabase.dataSource(owner());
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
  @Primary
  EntityManager entityManager(EntityManagerFactory factory) {
    return SharedEntityManagerCreator.createSharedEntityManager(factory);
  }

  @Bean
  PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
    return new JpaTransactionManager(factory);
  }
}
