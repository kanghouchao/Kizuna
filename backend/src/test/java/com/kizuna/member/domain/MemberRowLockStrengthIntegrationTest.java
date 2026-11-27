package com.kizuna.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.shared.persistence.EnabledIfIntegration;
import com.kizuna.shared.persistence.LiquibaseSchemaJpaConfig;
import com.kizuna.shared.persistence.RowLockProbe;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 会員行の 2 種の読み口が載せる行ロックの強さを、子表の挿入が会員行へ置く KEY SHARE との衝突で見分ける。
 *
 * <p>会員行まわりの順序の理由づけ（退会・帰属の物化・台帳）は、この強さの違いに依っている。
 */
@EnabledIfIntegration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberRowLockStrengthIntegrationTest {
  private AnnotationConfigApplicationContext context;
  private MemberRepository members;
  private TransactionTemplate transaction;
  private DataSource dataSource;
  private JdbcTemplate jdbc;
  private RowLockProbe locks;

  @BeforeAll
  void connect() {
    context = new AnnotationConfigApplicationContext(Config.class);
    members = context.getBean(MemberRepository.class);
    transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    dataSource = context.getBean(DataSource.class);
    jdbc = new JdbcTemplate(dataSource);
    locks = new RowLockProbe(jdbc, transaction);
  }

  @AfterAll
  void close() {
    if (context != null) context.close();
  }

  @Test
  void pessimisticWriteReadLeavesTheMemberRowOpenToKeyShare() {
    long member = createMember();
    transaction.executeWithoutResult(
        status -> {
          members.findByIdForUpdate(member).orElseThrow();
          assertThat(probe(member, "for key share")).isNull();
          assertThat(probe(member, "for no key update")).isEqualTo("55P03");
        });
  }

  // 退会は明示ロックの読み口で会員行を取り、続く退会消去が native の読み口を通る。KEY SHARE が締め出されるのはそこから。
  @Test
  void nativeReadOnTopOfThePessimisticWriteReadShutsOutKeyShare() {
    long member = createMember();
    transaction.executeWithoutResult(
        status -> {
          members.lockIdForUpdate(member).orElseThrow();
          assertThat(probe(member, "for key share")).isNull();
          members.lockIdForBalanceConfirmation(member).orElseThrow();
          assertThat(probe(member, "for key share")).isEqualTo("55P03");
        });
  }

  // 別の取引が挿入で KEY SHARE を置いた後から来る側。明示ロックの読み口は待たずに通り、native の読み口だけが待つ。
  @Test
  void keyShareHeldElsewhereStopsOnlyTheNativeRead() throws Exception {
    long member = createMember();
    var release = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      Future<?> holder = locks.hold(executor, "t_members", member, "for key share", release);
      try {
        transaction.executeWithoutResult(
            status -> {
              jdbc.execute("set local lock_timeout = '2s'");
              assertThat(members.lockIdForUpdate(member)).contains(member);
            });
        assertThat(locks.sqlStateOfLockingWithoutWaiting("t_members", member)).isEqualTo("55P03");
      } finally {
        release.countDown();
      }
      holder.get(10, TimeUnit.SECONDS);
    }
  }

  /** 自動コミットの別接続で、指定の強さの行ロックを待たずに試みる。取れれば null、取れなければ SQLSTATE を返す。 */
  private String probe(long member, String strength) {
    try (var connection = dataSource.getConnection();
        var statement =
            connection.prepareStatement(
                "select id from t_members where id = ? " + strength + " nowait")) {
      statement.setLong(1, member);
      try (var rows = statement.executeQuery()) {
        assertThat(rows.next()).isTrue();
        return null;
      }
    } catch (SQLException ex) {
      return ex.getSQLState();
    }
  }

  private long createMember() {
    long user =
        jdbc.queryForObject(
            "insert into t_users(display_name,user_type,email) values ('検証会員','MEMBER',?) returning id",
            Long.class,
            UUID.randomUUID() + "@example.test");
    return jdbc.queryForObject(
        "insert into t_members(member_code,user_id) values (?,?) returning id",
        Long.class,
        "M" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(),
        user);
  }

  @Configuration
  @EnableJpaRepositories(basePackageClasses = MemberRepository.class)
  static class Config extends LiquibaseSchemaJpaConfig {
    @Override
    protected Class<?> owner() {
      return MemberRowLockStrengthIntegrationTest.class;
    }
  }
}
