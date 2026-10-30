package com.kizuna.shared.persistence;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

// 実 PostgreSQL テスト層の接続先。全クラスが一つの DB を共有するので、テストクラスごとに使い捨てスキーマを一つ切る。
// 同じクラスが別インスタンスを模して文脈を複数立てても、同じスキーマを見る。
public final class IntegrationTestDatabase {
  public static final String URL_VARIABLE = "KIZUNA_POSTGRES_TEST_JDBC_URL";
  private static final Map<Class<?>, String> URLS = new ConcurrentHashMap<>();

  private IntegrationTestDatabase() {}

  /** owner 専用のスキーマを既定スキーマにした JDBC URL。初回にスキーマを作る。 */
  public static String url(Class<?> owner) {
    return URLS.computeIfAbsent(owner, ignored -> createSchema());
  }

  public static DriverManagerDataSource dataSource(Class<?> owner) {
    return new DriverManagerDataSource(url(owner), username(), password());
  }

  /** プールの大きさは呼び出し側が決める。 */
  public static HikariDataSource pool(Class<?> owner) {
    var source = new HikariDataSource();
    source.setJdbcUrl(url(owner));
    source.setUsername(username());
    source.setPassword(password());
    return source;
  }

  public static Map<String, Object> springDataSourceProperties(Class<?> owner) {
    return Map.of(
        "spring.datasource.url", url(owner),
        "spring.datasource.username", username(),
        "spring.datasource.password", password());
  }

  private static String createSchema() {
    String url = System.getenv(URL_VARIABLE);
    String schema = "t_" + UUID.randomUUID().toString().replace("-", "");
    new JdbcTemplate(new DriverManagerDataSource(url, username(), password()))
        .execute("create schema " + schema);
    return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
  }

  private static String username() {
    return System.getenv("KIZUNA_POSTGRES_TEST_USERNAME");
  }

  private static String password() {
    return System.getenv("KIZUNA_POSTGRES_TEST_PASSWORD");
  }
}
