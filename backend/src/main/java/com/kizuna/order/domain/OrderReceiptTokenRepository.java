package com.kizuna.order.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderReceiptTokenRepository extends JpaRepository<OrderReceiptToken, Long> {

  /**
   * 申領のためにダイジェストでトークンを引き、その行を押さえる。
   *
   * <p>行ロックが並行申領の収束を担う。ロック無しでは両者が「未申領」を観測して二重に帰属と付与が成立し、 敗者は同形のエラーではなく帰属記録の部分一意違反（500
   * 系）で落ちる。ロックがあれば敗者の読みは 勝者の確定後に行われ、消えた前提状態（未申領）をそのまま同形のエラーへ変えられる。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<OrderReceiptToken> findByTokenDigest(String tokenDigest);

  /**
   * 再発行のために受注の伝票トークンを新しい順に引き、その行をすべて押さえる。再発行があるため 1 受注が複数行を持ちうる。
   *
   * <p>この読み口は店舗行分離機構に載らない（伝票トークンは platform 帰属）。店舗の所有は受注を先に引いて確かめること。
   *
   * <p>この読みが再発行の直列化点そのものである。申領は行を押さえたまま帰属記録を書くため、押さえずに読むと 在途の申領が見えず、「帰属していない」という古い観測のまま 2
   * 本目を発行してしまう。押さえてから帰属記録を 読むことで、待たされた再発行は申領の確定後に判じられる — <b>この順序は入れ替えられない</b>。
   *
   * <p>待ちが環にならないのは、申領が押さえるのがトークン行だけだからである（受注行を要求しない）。再発行は受注行 → トークン行の順に取る。
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select t from com.kizuna.order.domain.OrderReceiptToken t"
          + " where t.orderId = :orderId order by t.id desc")
  List<OrderReceiptToken> findByOrderIdForUpdate(@Param("orderId") String orderId);
}
