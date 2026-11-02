package com.kizuna.medium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.audit.domain.AuditEventRepository;
import com.kizuna.audit.recording.AuditWriter;
import com.kizuna.medium.api.dto.MediumResponses.MediumOptionResponse;
import com.kizuna.medium.api.dto.MediumResponses.MediumSummary;
import com.kizuna.medium.application.MediumService;
import com.kizuna.medium.domain.MediumRepository;
import com.kizuna.medium.domain.MediumStatus;
import com.kizuna.medium.lookup.MediumLookup;
import com.kizuna.medium.lookup.MediumReference;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.store.StoreShortNames;
import com.kizuna.user.application.BusinessAudit;
import com.kizuna.user.domain.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 実 PG（Liquibase の baseline）で、媒体の一意性・版・状態遷移・店舗隔離と業務監査を確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MediumIntegrationTest {
  private static final String ACTOR = "medium@example.test";
  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private MediumService media;

  @BeforeAll
  void connect() {
    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                IntegrationTestDatabase.springDataSourceProperties(MediumIntegrationTest.class))
            .properties(
                Map.ofEntries(
                    Map.entry("spring.config.name", "medium-test"),
                    Map.entry(
                        "spring.liquibase.change-log",
                        "classpath:db/changelog/db.changelog-master.yaml"),
                    Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                    Map.entry("spring.jpa.open-in-view", "false"),
                    Map.entry("logging.level.root", "WARN")))
            .run();
    jdbc = context.getBean(JdbcTemplate.class);
    media = context.getBean(MediumService.class);
    jdbc.update(
        "insert into t_users(email,display_name,user_type,store_scope_type) values (?,'媒体担当','STAFF','ALL_STORES')",
        ACTOR);
  }

  @BeforeEach
  void authenticate() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(ACTOR, null, List.of()));
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
  void createdMediumIsTrimmedListedActiveAndAudited() {
    long store = store();

    String id = media.create(store, "  シティヘブン ");

    assertThat(media.list(store, null))
        .singleElement()
        .returns(id, MediumSummary::id)
        .returns("シティヘブン", MediumSummary::name)
        .returns(MediumStatus.ACTIVE, MediumSummary::status)
        .returns(0L, MediumSummary::version);
    assertThat(audits(id)).containsExactly("MEDIUM_CREATED");
  }

  @Test
  void sameNameIsRejectedWithinTheStoreButNotAcrossStoresOrCaseAndWidth() {
    long store = store();
    long other = store();
    media.create(store, "ヘブン");

    assertThatThrownBy(() -> media.create(store, " ヘブン")).isInstanceOf(ConflictException.class);
    media.create(store, "ﾍﾌﾞﾝ");
    media.create(other, "ヘブン");

    assertThat(media.list(store, null)).extracting(MediumSummary::name).hasSize(2);
  }

  @Test
  void concurrentSameNameLoserGetsTheSameConflictThroughTheConstraint() throws Exception {
    long store = store();
    var transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    var inserted = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var winner =
          executor.submit(
              () ->
                  transactions.executeWithoutResult(
                      status -> {
                        authenticate();
                        media.create(store, "同時");
                        inserted.countDown();
                        await(release);
                      }));
      await(inserted);
      var loser = Executors.newSingleThreadExecutor();
      try {
        var attempt =
            loser.submit(
                () -> {
                  authenticate();
                  return media.create(store, "同時");
                });
        Thread.sleep(300);
        release.countDown();
        winner.get(10, TimeUnit.SECONDS);
        assertThatThrownBy(() -> attempt.get(10, TimeUnit.SECONDS))
            .hasCauseInstanceOf(ConflictException.class);
      } finally {
        loser.shutdownNow();
      }
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
    assertThat(media.list(store, null)).singleElement().returns("同時", MediumSummary::name);
  }

  @Test
  void renameChecksNameAndVersionAndIsAudited() {
    long store = store();
    String id = media.create(store, "旧名");
    media.create(store, "使用中");

    assertThatThrownBy(() -> media.rename(store, id, "使用中", 0L))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> media.rename(store, id, "新名", 9L))
        .isInstanceOf(ConflictException.class);
    var renamed = media.rename(store, id, " 新名 ", 0L);

    assertThat(renamed).returns("新名", MediumSummary::name).returns(1L, MediumSummary::version);
    assertThat(audits(id)).containsExactly("MEDIUM_CREATED", "MEDIUM_RENAMED");
    assertThat(
            jdbc.queryForObject(
                "select after_values ->> 'redacted_fields_changed' from t_audit_events where target_id = ? and action = 'MEDIUM_RENAMED'",
                String.class,
                id))
        .isEqualTo("name");
    assertThat(
            jdbc.queryForList(
                "select before_values::text || after_values::text from t_audit_events where target_id = ?",
                String.class,
                id))
        .noneMatch(values -> values.contains("旧名") || values.contains("新名"));
  }

  @Test
  void stopAndResumeRejectRepeatsAndStaleVersionsAndAreAudited() {
    long store = store();
    String id = media.create(store, "停止対象");

    assertThatThrownBy(() -> media.resume(store, id, 0L)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> media.stop(store, id, 3L)).isInstanceOf(ConflictException.class);
    assertThat(media.stop(store, id, 0L)).returns(MediumStatus.STOPPED, MediumSummary::status);
    assertThatThrownBy(() -> media.stop(store, id, 1L)).isInstanceOf(ConflictException.class);
    assertThat(media.resume(store, id, 1L))
        .returns(MediumStatus.ACTIVE, MediumSummary::status)
        .returns(2L, MediumSummary::version);

    assertThat(audits(id)).containsExactly("MEDIUM_CREATED", "MEDIUM_STOPPED", "MEDIUM_RESUMED");
  }

  @Test
  void deleteChecksVersionRemovesTheRowAndIsAudited() {
    long store = store();
    String id = media.create(store, "誤登録");

    assertThatThrownBy(() -> media.delete(store, id, 5L)).isInstanceOf(ConflictException.class);
    media.delete(store, id, 0L);

    assertThat(media.list(store, null)).isEmpty();
    assertThat(audits(id)).containsExactly("MEDIUM_CREATED", "MEDIUM_DELETED");
  }

  @Test
  void otherStoresMediumIsNotFoundForEveryOperationAndNeverListed() {
    long store = store();
    long other = store();
    String foreign = media.create(other, "他店");

    assertThatThrownBy(() -> media.rename(store, foreign, "奪取", 0L))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> media.stop(store, foreign, 0L)).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> media.resume(store, foreign, 0L))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> media.delete(store, foreign, 0L))
        .isInstanceOf(NotFoundException.class);
    assertThat(media.list(store, null)).isEmpty();
    assertThat(media.options(store)).isEmpty();
  }

  @Test
  void optionsListOnlyActiveMediaByNameAndListFiltersByStatus() {
    long store = store();
    String b = media.create(store, "B媒体");
    String a = media.create(store, "A媒体");
    String stopped = media.create(store, "C媒体");
    media.stop(store, stopped, 0L);

    assertThat(media.options(store))
        .containsExactly(new MediumOptionResponse(a, "A媒体"), new MediumOptionResponse(b, "B媒体"));
    assertThat(media.list(store, null))
        .extracting(MediumSummary::id)
        .containsExactly(a, b, stopped);
    assertThat(media.list(store, MediumStatus.STOPPED))
        .extracting(MediumSummary::id)
        .containsExactly(stopped);
  }

  @Test
  void lookupGivesTheCurrentNameAndStateOnlyWithinTheStore() {
    long store = store();
    long other = store();
    String id = media.create(store, "参照前");
    media.rename(store, id, "参照後", 0L);
    media.stop(store, id, 1L);
    var lookup = context.getBean(MediumLookup.class);

    assertThat(lookup.find(store, id)).contains(new MediumReference(id, "参照後", false));
    assertThat(lookup.find(other, id)).isEmpty();
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("待機が時間切れ");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private List<String> audits(String id) {
    return jdbc.queryForList(
        "select action from t_audit_events where target_type = 'MEDIUM' and target_id = ? order by id",
        String.class,
        id);
  }

  private long store() {
    return jdbc.queryForObject(
        "insert into t_stores(name,short_name,domain) values ('媒体検証',?,?) returning id",
        Long.class,
        StoreShortNames.next(),
        UUID.randomUUID() + ".example.test");
  }

  @Configuration
  @EnableAutoConfiguration
  @EntityScan("com.kizuna")
  @EnableJpaRepositories(
      basePackageClasses = {
        MediumRepository.class,
        AuditEventRepository.class,
        UserRepository.class
      })
  @Import({MediumService.class, MediumLookup.class, BusinessAudit.class, AuditWriter.class})
  static class Config {
    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }
  }
}
