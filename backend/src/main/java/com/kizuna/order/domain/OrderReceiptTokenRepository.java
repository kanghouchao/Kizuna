package com.kizuna.order.domain;

import jakarta.persistence.LockModeType;
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
}
