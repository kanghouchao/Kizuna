package com.kizuna.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;

// 空の DB に複数の backend が同時に起動する場面。Spring Boot の SpringLiquibase を、本番と同じ後処理（ロック）で包んで流す。
@EnabledIfIntegration
class LiquibaseMigrationLockIntegrationTest {

  private static final String CHANGE_LOG = "classpath:db/changelog/db.changelog-master.yaml";

  private final List<HikariDataSource> pools = new ArrayList<>();

  @AfterEach
  void closePools() {
    pools.forEach(HikariDataSource::close);
  }

  @Test
  void ロックを持つ起動がある間_次の起動は_changelog_の解析も始めずに待つ() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(IntegrationTestDatabase.dataSource(SerialSchema.class));
    try (Connection holder =
            IntegrationTestDatabase.dataSource(SerialSchema.class).getConnection();
        var lock = holder.prepareStatement("select pg_advisory_lock(?, 0)")) {
      lock.setInt(1, LiquibaseMigrationLock.LOCK_NAMESPACE);
      lock.execute();

      CompletableFuture<Void> startup =
          CompletableFuture.runAsync(() -> migrate(SerialSchema.class));

      awaitAdvisoryWaiter(jdbc);
      assertThat(startup).isNotDone();
      assertThat(changelogTable(jdbc)).isNull();

      try (var unlock = holder.prepareStatement("select pg_advisory_unlock(?, 0)")) {
        unlock.setInt(1, LiquibaseMigrationLock.LOCK_NAMESPACE);
        unlock.execute();
      }
      startup.get(2, TimeUnit.MINUTES);
    }
    assertThat(changelogTable(jdbc)).isNotNull();
  }

  @Test
  void 二つの起動が同時に空の_DB_へマイグレーションしても_両方とも成功する() throws Exception {
    CyclicBarrier start = new CyclicBarrier(2);
    List<CompletableFuture<Void>> startups = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      startups.add(
          CompletableFuture.runAsync(
              () -> {
                await(start);
                migrate(RaceSchema.class);
              }));
    }

    CompletableFuture.allOf(startups.toArray(CompletableFuture[]::new)).get(3, TimeUnit.MINUTES);

    JdbcTemplate jdbc = new JdbcTemplate(IntegrationTestDatabase.dataSource(RaceSchema.class));
    assertThat(jdbc.queryForObject("select count(*) from databasechangelog", Integer.class))
        .isPositive();
  }

  // 一回の呼び出しが一台の backend の起動に当たる：自分のプール、自分の後処理、Spring が呼ぶのと同じ順序
  private void migrate(Class<?> schema) {
    HikariDataSource pool = IntegrationTestDatabase.pool(schema);
    synchronized (pools) {
      pools.add(pool);
    }
    SpringLiquibase liquibase = new SpringLiquibase();
    liquibase.setDataSource(pool);
    liquibase.setChangeLog(CHANGE_LOG);
    liquibase.setResourceLoader(new DefaultResourceLoader());
    LiquibaseMigrationLock lock = new LiquibaseMigrationLock();
    lock.postProcessBeforeInitialization(liquibase, "liquibase");
    try {
      liquibase.afterPropertiesSet();
    } catch (Exception e) {
      lock.destroy();
      throw new IllegalStateException(e);
    }
    lock.postProcessAfterInitialization(liquibase, "liquibase");
  }

  private static String changelogTable(JdbcTemplate jdbc) {
    return jdbc.queryForObject("select to_regclass('databasechangelog')::text", String.class);
  }

  private static void awaitAdvisoryWaiter(JdbcTemplate jdbc) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < deadline) {
      Integer waiters =
          jdbc.queryForObject(
              "select count(*) from pg_stat_activity where datname = current_database()"
                  + " and wait_event_type = 'Lock' and wait_event = 'advisory'",
              Integer.class);
      if (waiters != null && waiters > 0) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("起動が助言ロックを待たなかった");
  }

  private static void await(CyclicBarrier barrier) {
    try {
      barrier.await(30, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  // スキーマの持ち主の印。テストごとに空のスキーマを一つずつ使う
  private static final class SerialSchema {}

  private static final class RaceSchema {}
}
