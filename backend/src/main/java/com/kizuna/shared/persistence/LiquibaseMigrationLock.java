package com.kizuna.shared.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * 複数の backend が同時に起動しても、Liquibase の実行全体を PostgreSQL のセッション助言ロックで一台ずつにする。 Liquibase は changelog
 * の解析中、自身のロック（DATABASECHANGELOGLOCK）を取る前に DATABASECHANGELOG を「無ければ作る」ため、空の DB では二台が同時に CREATE TABLE
 * し、一台が {@code relation "databasechangelog" already exists} で起動に失敗する。 プールの接続は close
 * してもセッションのロックが残るので、必ず明示的に解く。
 */
@Log4j2
@Component
class LiquibaseMigrationLock implements BeanPostProcessor, DisposableBean {

  static final int LOCK_NAMESPACE = 395;

  private final Map<String, Connection> held = new ConcurrentHashMap<>();

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) {
    if (bean instanceof SpringLiquibase liquibase) {
      held.put(beanName, acquire(liquibase.getDataSource()));
    }
    return bean;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    release(held.remove(beanName));
    return bean;
  }

  /** 実行が失敗して文脈が閉じるときも、取ったままのロックを解く。 */
  @Override
  public void destroy() {
    held.values().forEach(LiquibaseMigrationLock::release);
    held.clear();
  }

  private static Connection acquire(DataSource dataSource) {
    Connection connection = null;
    try {
      connection = dataSource.getConnection();
      try (var statement = connection.prepareStatement("select pg_advisory_lock(?, 0)")) {
        statement.setInt(1, LOCK_NAMESPACE);
        statement.execute();
      }
      return connection;
    } catch (SQLException e) {
      release(connection);
      throw new IllegalStateException("マイグレーションのロックを取得できませんでした", e);
    }
  }

  private static void release(Connection connection) {
    if (connection == null) {
      return;
    }
    try (connection;
        var statement = connection.prepareStatement("select pg_advisory_unlock(?, 0)")) {
      statement.setInt(1, LOCK_NAMESPACE);
      statement.execute();
    } catch (SQLException e) {
      // 接続ごと切れていればロックも解けている。残っていればこの接続が閉じるまで他の起動を待たせる
      log.warn("マイグレーションのロックを解けませんでした", e);
    }
  }
}
