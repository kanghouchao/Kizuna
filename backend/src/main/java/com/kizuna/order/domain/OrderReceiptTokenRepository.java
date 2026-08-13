package com.kizuna.order.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

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
   * 受注に対して発行された伝票トークンを新しい順に。再発行があるため 1 受注が複数行を持ちうる。
   *
   * <p>「申領できる行が既にあるか」の判定は取得した行に {@link OrderReceiptToken#isClaimableAt} を当てて行う —
   * 状態と期限を問い合わせ側で書き下すと、 申領できるかの定義が二箇所に分かれる。
   *
   * <p>この読み口は店舗行分離機構に載らない（伝票トークンは platform 帰属）。店舗の所有は受注を先に引いて確かめること。
   */
  List<OrderReceiptToken> findByOrderIdOrderByIdDesc(String orderId);
}
