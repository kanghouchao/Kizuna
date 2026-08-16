package com.kizuna.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.shared.CrossStoreTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 群読み口（作業キュー・アーカイブ）が要求する「1 トランザクション ＝ 1 断面」を、本物の PostgreSQL で確かめる統合テスト。
 *
 * <p>この 2 つの読み口は 1 回の応答を作るのに複数の文を投げる（並び → 本体、アーカイブはさらに総件数）。既定の READ COMMITTED
 * では文ごとに断面を取り直すため、文の間に他の操作者の commit が挟まると<b>同じ応答の中で違う世界を見る</b>。実際に評審で 3 つの変種が出た — 続きの位置が行を飛ばす /
 * 完了済みの行が作業キューに混じる / ページャの総数が中身と食い違う。変種ごとに手当てするのではなく、断面を 1 つに固定して根を断つのが {@code OrderService}
 * の選択で、その前提がこのテストである。
 *
 * <p>固定するのは<b>機構そのもの</b>（この構成で {@code REPEATABLE READ} が実際にコネクションへ届き、断面が保たれること）。サービス呼び出しの
 * 内部で他者の書き込みを差し込むことは決定的にはできないため、そこは機構の正しさから導いている。宣言が外れていないことは 単体側の {@code
 * OrderServiceTest#groupReadsRunInASingleSnapshot} が見る。
 */
class OrderGroupReadSnapshotIT extends CrossStoreTestSupport {

  @Autowired private OrderRepository orderRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  @PersistenceContext private EntityManager entityManager;

  /** 他の操作者が書き込む値。断面の内側からは見えてはならない。 */
  private static final int REWRITTEN_PAX = 99;

  @Test
  @DisplayName("REPEATABLE READ の読みは、文の間に他者が commit した書き換えを見ないこと")
  void repeatableReadHoldsOneSnapshotAcrossStatements() {
    String orderId = seedOrder();

    readIn(TransactionDefinition.ISOLATION_REPEATABLE_READ)
        .execute(
            status -> {
              // 1 本目がこのトランザクションの断面を確定させる
              Integer before = paxOf(orderId);
              // 隔離水準が実際にコネクションへ届いていること。届かなければ以下は READ COMMITTED の
              // 挙動になり、下の断言が「たまたま」通る余地が残る
              assertThat(currentIsolation()).isEqualTo("repeatable read");

              commitPaxFromAnotherConnection(orderId);

              // 2 本目。並び → 本体の 2 段がここで見る世界にあたる
              assertThat(paxOf(orderId)).as("断面の内側では書き換えが見えないこと").isEqualTo(before);
              return null;
            });

    // トランザクションの外では見える（上の不可視が「書き込みが失敗しただけ」ではないことの対照）
    assertThat(paxOutsideAnyTransaction(orderId)).isEqualTo(REWRITTEN_PAX);
  }

  @Test
  @DisplayName("READ COMMITTED では同じ読みが文の間の書き換えを拾ってしまうこと（上の断言が空振りでないことの反証）")
  void readCommittedSeesTheInterleavedWrite() {
    String orderId = seedOrder();

    readIn(TransactionDefinition.ISOLATION_READ_COMMITTED)
        .execute(
            status -> {
              Integer before = paxOf(orderId);
              commitPaxFromAnotherConnection(orderId);

              // 既定の水準では 2 本目が新しい断面を取り直す。群読み口がこの上に乗っていた
              assertThat(paxOf(orderId)).isNotEqualTo(before).isEqualTo(REWRITTEN_PAX);
              return null;
            });
  }

  /** 読み取り専用の問い合わせを、指定した隔離水準の 1 トランザクションで走らせる型。 */
  private TransactionTemplate readIn(int isolationLevel) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setIsolationLevel(isolationLevel);
    // 群読み口と同じ形（readOnly）。PostgreSQL では SET TRANSACTION READ ONLY になる
    template.setReadOnly(true);
    return template;
  }

  private String seedOrder() {
    Order order =
        Order.builder().businessDate(LocalDate.now()).pax(2).status(OrderStatus.CONFIRMED).build();
    order.setStoreId(STORE_A);
    return orderRepository.save(order).getId();
  }

  private Integer paxOf(String orderId) {
    return entityManager
        .createQuery(
            "select o.pax from com.kizuna.order.domain.Order o where o.id = :id", Integer.class)
        .setParameter("id", orderId)
        .getSingleResult();
  }

  private String currentIsolation() {
    return entityManager
        .createNativeQuery("select current_setting('transaction_isolation')", String.class)
        .getSingleResult()
        .toString();
  }

  /** 他の操作者の書き込み。別コネクションで commit まで済ませる（同じ接続では断面の話にならない）。 */
  private void commitPaxFromAnotherConnection(String orderId) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement("update t_orders set pax = ? where id = ?")) {
      connection.setAutoCommit(true);
      statement.setInt(1, REWRITTEN_PAX);
      statement.setString(2, orderId);
      assertThat(statement.executeUpdate()).as("前提: 他者の書き込みが 1 行に当たること").isEqualTo(1);
    } catch (SQLException e) {
      throw new IllegalStateException("他コネクションからの書き込みに失敗しました", e);
    }
  }

  private Integer paxOutsideAnyTransaction(String orderId) {
    return orderRepository
        .findById(orderId)
        .map(Order::getPax)
        .orElseThrow(() -> new IllegalStateException("仕込んだ受注が消えました: " + orderId));
  }
}
