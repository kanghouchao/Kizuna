package com.kizuna.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.medium.domain.Medium;
import com.kizuna.notificationdelivery.domain.DeliveryChannel;
import com.kizuna.notificationdelivery.domain.DeliveryContent;
import com.kizuna.notificationdelivery.domain.DeliveryPurpose;
import com.kizuna.notificationdelivery.domain.DeliverySourceType;
import com.kizuna.notificationdelivery.domain.NotificationDelivery;
import com.kizuna.store.StoreShortNames;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 複数インスタンスが調整なしで衝突しない ID を採るため、生成 ID は実マッピング経由で UUIDv7 の 36 文字になる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeneratedIdIntegrationTest {
  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private TransactionTemplate tx;

  @BeforeAll
  void connect() {
    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                IntegrationTestDatabase.springDataSourceProperties(
                    GeneratedIdIntegrationTest.class))
            .properties(
                Map.ofEntries(
                    Map.entry("spring.config.name", "generated-id-test"),
                    Map.entry(
                        "spring.liquibase.change-log",
                        "classpath:db/changelog/db.changelog-master.yaml"),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                    Map.entry("spring.jpa.open-in-view", "false"),
                    Map.entry("logging.level.root", "WARN")))
            .run();
    jdbc = context.getBean(JdbcTemplate.class);
    tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void storeScopedEntityGetsVersion7Uuid() {
    long store =
        jdbc.queryForObject(
            "insert into t_stores(name,short_name,domain) values ('採番検証',?,?) returning id",
            Long.class,
            StoreShortNames.next(),
            UUID.randomUUID() + ".example.test");
    var medium = new Medium("採番検証");
    medium.setStoreId(store);

    assertVersion7(persist(medium).getId());
  }

  @Test
  void deliveryRecordGetsVersion7Uuid() {
    var delivery =
        NotificationDelivery.draft(
            null,
            new DeliveryContent(
                DeliverySourceType.MEMBER,
                "1",
                DeliveryChannel.EMAIL,
                DeliveryPurpose.BUSINESS,
                "件名",
                "本文",
                OffsetDateTime.now(),
                "generated-id"));

    assertVersion7(persist(delivery).getId());
  }

  private <T> T persist(T entity) {
    return tx.execute(
        status -> {
          context.getBean(EntityManager.class).persist(entity);
          return entity;
        });
  }

  private static void assertVersion7(String id) {
    assertThat(id).hasSize(36).isEqualTo(id.toLowerCase());
    assertThat(UUID.fromString(id).version()).isEqualTo(7);
  }

  @Configuration
  @EnableAutoConfiguration
  @EntityScan("com.kizuna")
  static class Config {}
}
