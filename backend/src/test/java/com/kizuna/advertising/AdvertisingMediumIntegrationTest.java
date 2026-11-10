package com.kizuna.advertising;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kizuna.advertising.api.dto.AdvertisingRequests.CreateRequest;
import com.kizuna.advertising.api.dto.AdvertisingRequests.DeleteRequest;
import com.kizuna.advertising.api.dto.AdvertisingRequests.ReplaceRequest;
import com.kizuna.advertising.application.AdvertisingMediaComparison;
import com.kizuna.advertising.application.AdvertisingMediaService;
import com.kizuna.advertising.application.AdvertisingService;
import com.kizuna.advertising.application.AdvertisingViews.CostView;
import com.kizuna.advertising.domain.AdvertisingCategory;
import com.kizuna.advertising.domain.AdvertisingChange;
import com.kizuna.advertising.domain.AdvertisingChangeRepository;
import com.kizuna.advertising.domain.AdvertisingMedium;
import com.kizuna.advertising.infrastructure.AdvertisingRecords;
import com.kizuna.medium.application.MediumService;
import com.kizuna.medium.domain.MediumRepository;
import com.kizuna.medium.lookup.MediumLookup;
import com.kizuna.receipt.domain.OperationReceiptRepository;
import com.kizuna.receipt.once.OperationReceipts;
import com.kizuna.shared.config.AppProperties;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.store.StoreShortNames;
import com.kizuna.user.application.ActorIdentityService;
import com.kizuna.user.application.BusinessAudit;
import java.time.Clock;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

/** 実 PG（Liquibase の baseline）で、広告費の媒体参照が店舗内に閉じ、媒体の削除と改名が記録と集計に正しく効くことを確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdvertisingMediumIntegrationTest {
  private static final String ACTOR = "advertising-medium@example.test";
  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private AdvertisingService costs;
  private MediumService media;

  @BeforeAll
  void connect() {
    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                IntegrationTestDatabase.springDataSourceProperties(
                    AdvertisingMediumIntegrationTest.class))
            .properties(
                Map.ofEntries(
                    Map.entry("spring.config.name", "advertising-medium-test"),
                    Map.entry(
                        "spring.liquibase.change-log",
                        "classpath:db/changelog/db.changelog-master.yaml"),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                    Map.entry("spring.jpa.open-in-view", "false"),
                    Map.entry("logging.level.root", "WARN")))
            .run();
    jdbc = context.getBean(JdbcTemplate.class);
    costs = context.getBean(AdvertisingService.class);
    media = context.getBean(MediumService.class);
    long actorId =
        jdbc.queryForObject(
            "insert into t_users(email,display_name,user_type) values (?,'広告担当','STAFF') returning id",
            Long.class,
            ACTOR);
    when(context.getBean(ActorIdentityService.class).requireUserId(any())).thenReturn(actorId);
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void anotherStoresMediumIsRejectedByTheApiAndByTheCompositeForeignKey() {
    long store = store();
    long other = store();
    String own = media.create(store, "自店");
    String foreign = media.create(other, "他店");
    var cost = create(store, own);

    assertThatThrownBy(() -> create(store, foreign))
        .isInstanceOf(ServiceException.class)
        .hasMessage("媒体が見つかりません。一覧から選択してください");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into t_advertising_costs(id,store_id,month,category,medium_id,amount) values (?,?,'2026-09','SALES',?,0)",
                    UUID.randomUUID().toString(),
                    store,
                    foreign))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("fk_t_advertising_costs_medium");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update t_advertising_costs set medium_id=? where id=?", foreign, cost.id()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("fk_t_advertising_costs_medium");
  }

  @Test
  void aMediumReferencedOnlyByADeletedCostCannotBeDeleted() {
    long store = store();
    String id = media.create(store, "削除済みから参照");
    var cost = create(store, id);
    costs.delete(
        store,
        new DeleteRequest(cost.version(), "誤入力", UUID.randomUUID()).command(cost.id()),
        ACTOR);

    assertThatThrownBy(() -> media.delete(store, id, 0L))
        .isInstanceOf(ConflictException.class)
        .hasMessage("記録から参照されている媒体は削除できません。使わない場合は停止してください");
    assertThat(media.list(store, null))
        .singleElement()
        .satisfies(m -> assertThat(m.getId()).isEqualTo(id));
  }

  @Test
  void renamingAMediumChangesTheShownNameButNotTheGroupingOrTheRecordedHistory() {
    long store = store();
    String id = media.create(store, "旧名");
    String sibling = media.create(store, "別媒体");
    var cost = create(store, id);
    create(store, id);
    create(store, sibling);
    costs.replace(
        store,
        new ReplaceRequest(
                AdvertisingCategory.SALES,
                id,
                null,
                null,
                null,
                200,
                cost.version(),
                "修正",
                UUID.randomUUID())
            .command(cost.id()),
        ACTOR);

    media.rename(store, id, "新名", 0L);

    assertThat(costs.list(store, "2026-09", PageRequest.of(0, 20)).getContent())
        .filteredOn(c -> c.medium().id().equals(id))
        .hasSize(2)
        .allSatisfy(c -> assertThat(c.medium().name()).isEqualTo("新名"));
    assertThat(costs.snapshot(store, "2026-09").costs())
        .extracting(c -> c.medium())
        .containsExactlyInAnyOrder(
            new AdvertisingMedium(id, "新名"),
            new AdvertisingMedium(id, "新名"),
            new AdvertisingMedium(sibling, "別媒体"));
    var report = context.getBean(AdvertisingMediaService.class).view(store, "2026-09").report();
    assertThat(report.rows())
        .extracting(r -> r.medium().id() + ":" + r.medium().name() + ":" + r.entryCount())
        .containsExactlyInAnyOrder(id + ":新名:2", sibling + ":別媒体:1");
    var history =
        costs.changes(
            store,
            "2026-09",
            PageRequest.of(
                0,
                1,
                Sort.by(
                    Sort.Direction.DESC,
                    AdvertisingChange::getCreatedAt,
                    AdvertisingChange::getId)));
    var updated = costs.changeDetail(store, "2026-09", history.getContent().getFirst().getId());
    assertThat(updated.before().medium()).isEqualTo(new AdvertisingMedium(id, "旧名"));
    assertThat(updated.after().medium()).isEqualTo(new AdvertisingMedium(id, "旧名"));
  }

  private CostView create(long store, String mediumId) {
    return costs.create(
        store,
        new CreateRequest(
                "2026-09",
                AdvertisingCategory.SALES,
                mediumId,
                null,
                null,
                null,
                100,
                UUID.randomUUID())
            .command(),
        ACTOR);
  }

  private long store() {
    return jdbc.queryForObject(
        "insert into t_stores(name,short_name,domain) values ('広告媒体検証',?,?) returning id",
        Long.class,
        StoreShortNames.next(),
        UUID.randomUUID() + ".example.test");
  }

  @Configuration
  @EnableAutoConfiguration
  @EntityScan("com.kizuna")
  @EnableJpaRepositories(
      basePackageClasses = {
        AdvertisingChangeRepository.class,
        MediumRepository.class,
        OperationReceiptRepository.class
      })
  @Import({
    AdvertisingService.class,
    AdvertisingRecords.class,
    AdvertisingMediaComparison.class,
    AdvertisingMediaService.class,
    MediumService.class,
    MediumLookup.class,
    OperationReceipts.class
  })
  static class Config {
    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    AppProperties appProperties() {
      return new AppProperties();
    }

    @Bean
    ActorIdentityService actors() {
      return mock(ActorIdentityService.class);
    }

    @Bean
    BusinessAudit audit() {
      return mock(BusinessAudit.class);
    }
  }
}
