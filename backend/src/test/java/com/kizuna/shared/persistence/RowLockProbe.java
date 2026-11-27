package com.kizuna.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 経路が行を押さえる順序を実 PostgreSQL で見分ける道具。
 *
 * <p>外から 1 行を握って経路をそこで待たせ、その間に隣の行を待たずに押さえてみる。
 *
 * <p>「待つかどうか」では順序を固定できない。逆順の実装でも外部キー検査で結局待つ（ADR 0016）。
 */
@RequiredArgsConstructor
public final class RowLockProbe {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;

  /** 別のトランザクションでその行を {@code for update} で押さえ、解放の合図まで握り続ける。押さえ終えてから戻る。 */
  public Future<?> hold(ExecutorService executor, String table, Object id, CountDownLatch release)
      throws InterruptedException {
    var locked = new CountDownLatch(1);
    Future<?> holder =
        executor.submit(
            () ->
                transaction.executeWithoutResult(
                    status -> {
                      jdbc.queryForList("select 1 from " + table + " where id = ? for update", id);
                      locked.countDown();
                      await(release);
                    }));
    assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
    return holder;
  }

  /** ロック待ちの接続がちょうど 1 本になるまで待ち、待っている文を小文字で返す。 */
  public String awaitBlockedStatement() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      List<String> waiting =
          jdbc.queryForList(
              "select lower(query) from pg_stat_activity"
                  + " where datname = current_database() and wait_event_type = 'Lock'",
              String.class);
      if (waiting.size() == 1) return waiting.getFirst();
      Thread.sleep(20);
    }
    throw new AssertionError("ロックを待つトランザクションが 1 本になりません");
  }

  /** その行を待たずに {@code for update} で押さえてみて、取れれば null、取れなければ SQLSTATE を返す。押さえた行は直後に放す。 */
  public String sqlStateOfLockingWithoutWaiting(String table, Object id) {
    try {
      List<Integer> rows =
          jdbc.queryForList(
              "select 1 from " + table + " where id = ? for update nowait", Integer.class, id);
      // 行が無くても例外にならないので、押さえられたことを行数で確かめる。
      assertThat(rows).hasSize(1);
      return null;
    } catch (DataAccessException ex) {
      if (ex.getRootCause() instanceof SQLException sql) return sql.getSQLState();
      throw ex;
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("待ち合わせが時間切れです");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new AssertionError(ex);
    }
  }
}
