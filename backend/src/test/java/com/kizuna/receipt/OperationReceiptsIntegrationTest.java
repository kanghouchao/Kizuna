package com.kizuna.receipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kizuna.receipt.once.Committed;
import com.kizuna.receipt.once.OperationReceipts;
import com.kizuna.receipt.once.Receipt;
import com.kizuna.receipt.once.ReceiptKey;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.IntegrationTestDatabase;
import com.kizuna.store.StoreShortNames;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 受領の振る舞いを実 PostgreSQL で一度だけ固定する。各モジュールは受領を経由していることだけを確かめる。 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationReceiptsIntegrationTest {
  private ConfigurableApplicationContext context;
  private JdbcTemplate jdbc;
  private TransactionTemplate transactions;
  private OperationReceipts receipts;
  private Long storeId;
  private Long actorId;

  private record Outcome(Receipt receipt, int current) {}

  @BeforeAll
  void connect() {
    context =
        new SpringApplicationBuilder(Config.class)
            .web(WebApplicationType.NONE)
            .properties(
                IntegrationTestDatabase.springDataSourceProperties(
                    OperationReceiptsIntegrationTest.class))
            .properties(
                Map.of(
                    "spring.config.name", "receipt-test",
                    "spring.liquibase.change-log",
                        "classpath:db/changelog/db.changelog-master.yaml",
                    "spring.jpa.hibernate.ddl-auto", "validate",
                    "spring.jpa.open-in-view", "false",
                    "logging.level.root", "WARN"))
            .run();
    jdbc = context.getBean(JdbcTemplate.class);
    transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    receipts = context.getBean(OperationReceipts.class);
    // 呼出側の業務行を模す。受領は対象の行に依存しないので、検証用の表で足りる。
    jdbc.execute("create table receipt_probe(id varchar(32) primary key, counter int not null)");
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @BeforeEach
  void fixtures() {
    String suffix = UUID.randomUUID().toString();
    storeId =
        jdbc.queryForObject(
            "insert into t_stores(name,short_name,domain) values ('受領検証',?,?) returning id",
            Long.class,
            StoreShortNames.next(),
            suffix + ".invalid");
    actorId =
        jdbc.queryForObject(
            "insert into t_users(email,display_name,user_type,store_scope_type) values (?,'受領担当','STAFF','ALL_STORES') returning id",
            Long.class,
            suffix + "@example.invalid");
  }

  private String probe() {
    String id = UUID.randomUUID().toString().replace("-", "");
    jdbc.update("insert into receipt_probe(id,counter) values (?,0)", id);
    return id;
  }

  private int counter(String id) {
    return jdbc.queryForObject("select counter from receipt_probe where id=?", Integer.class, id);
  }

  /** 業務行を行ロックして一つ進め、進めた後の値を成立時の事実として残す。 */
  private Outcome increment(String key, String fingerprint, String id, Runnable whileLocked) {
    return transactions.execute(
        status ->
            receipts.once(
                new ReceiptKey(storeId, actorId, key),
                fingerprint,
                () -> {
                  jdbc.queryForObject(
                      "select counter from receipt_probe where id=? for update", Integer.class, id);
                  whileLocked.run();
                  int next =
                      jdbc.queryForObject(
                          "update receipt_probe set counter=counter+1 where id=? returning counter",
                          Integer.class,
                          id);
                  return new Committed("TEST_INCREMENTED", id, Map.of("committed_counter", next));
                },
                receipt -> new Outcome(receipt, counter(receipt.targetId()))));
  }

  private Outcome increment(String key, String fingerprint, String id) {
    return increment(key, fingerprint, id, () -> {});
  }

  @Test
  void sameKeyAndContentReplaysTheCurrentStateWithoutRunningTheWorkAgain() {
    String id = probe();
    var first = increment("same", "fingerprint", id);
    jdbc.update("update receipt_probe set counter=10 where id=?", id);

    var replay = increment("same", "fingerprint", id);

    assertThat(first.receipt().replayed()).isFalse();
    assertThat(replay.receipt().replayed()).isTrue();
    assertThat(replay.receipt().id()).isEqualTo(first.receipt().id());
    assertThat(replay.receipt().operation()).isEqualTo("TEST_INCREMENTED");
    assertThat(replay.receipt().longFact("committed_counter")).isEqualTo(1);
    assertThat(replay.current()).isEqualTo(10);
    assertThat(counter(id)).isEqualTo(10);
  }

  @Test
  void sameKeyWithDifferentContentIsAConflictAndRunsNothing() {
    String id = probe();
    increment("changed", "fingerprint", id);

    assertThatThrownBy(() -> increment("changed", "other", id))
        .isInstanceOf(ConflictException.class);
    assertThat(counter(id)).isEqualTo(1);
  }

  @Test
  void failedWorkLeavesNoReceiptSoTheSameKeyRunsAsTheFirstTime() {
    String id = probe();
    assertThatThrownBy(
            () ->
                increment(
                    "failed",
                    "fingerprint",
                    id,
                    () -> {
                      throw new IllegalStateException("業務の失敗");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_operation_receipts where store_id=?",
                Integer.class,
                storeId))
        .isZero();
    assertThat(increment("failed", "fingerprint", id).receipt().replayed()).isFalse();
    assertThat(counter(id)).isEqualTo(1);
  }

  @Test
  void concurrentSameKeyWaitsOnTheKeyLockBeforeAnyRowLockAndCommitsOnce() throws Exception {
    String id = probe();
    var firstHoldsTheRow = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(
              () ->
                  increment(
                      "concurrent",
                      "fingerprint",
                      id,
                      () -> {
                        firstHoldsTheRow.countDown();
                        try {
                          release.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException interrupted) {
                          Thread.currentThread().interrupt();
                        }
                      }));
      assertThat(firstHoldsTheRow.await(10, TimeUnit.SECONDS)).isTrue();
      var second = executor.submit(() -> increment("concurrent", "fingerprint", id));
      try {
        awaitWaiter("advisory");
      } finally {
        release.countDown();
      }

      var results =
          new Outcome[] {first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)};
      assertThat(results[0].receipt().replayed()).isFalse();
      assertThat(results[1].receipt().replayed()).isTrue();
      assertThat(results[1].receipt().id()).isEqualTo(results[0].receipt().id());
    }
    assertThat(counter(id)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from t_operation_receipts where store_id=?",
                Integer.class,
                storeId))
        .isEqualTo(1);
  }

  /** 後続の要求が業務行ではなく受領キーの助言ロックで待っていること。 */
  private void awaitWaiter(String event) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      var waits =
          jdbc.queryForList(
              "select wait_event from pg_stat_activity where datname=current_database() and wait_event_type='Lock'",
              String.class);
      if (!waits.isEmpty()) {
        assertThat(waits).containsExactly(event);
        return;
      }
      Thread.sleep(10);
    }
    throw new AssertionError("後続の要求がロックで待機しませんでした");
  }

  @Configuration
  @EnableAutoConfiguration
  @EntityScan("com.kizuna.receipt")
  @EnableJpaRepositories("com.kizuna.receipt")
  @Import(OperationReceipts.class)
  static class Config {
    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }
  }
}
