package com.kizuna.point.domain;

import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 受注 1 件のポイントを打ち消した操作の記録。受注につき高々 1 行で、理由・実行者・時刻を持つ。
 *
 * <p>台帳の仕訳とは別に物化するのは、<b>仕訳ゼロの受注でも操作が成立する</b>ためである。伝票トークンの付与予定額は完了時点で
 * 固定され再発行でも計算し直されないので、記録で拒まなければ申領がいつでも原額の付与を積み直せる。事後申領の入口はこの 記録を見て拒み、台帳に仕訳があるかでは判じない。
 *
 * <p>二度目は撥ねる（{@code uq_t_point_rollbacks_order}）。静默な no-op で吞むと初回の理由・実行者が書き換わったのか
 * そのままなのかが利用者に分からない（ADR 0013 の紀律）。
 */
@Entity
@Table(name = "t_point_rollbacks")
@Getter
@NoArgsConstructor
public class PointRollback extends BaseEntity {

  @Column(name = "order_id", nullable = false, updatable = false, length = 64)
  private String orderId;

  @Column(name = "reason", nullable = false, updatable = false, length = 500)
  private String reason;

  @Column(name = "actor_user_id", updatable = false)
  private Long actorUserId;

  private PointRollback(String orderId, String reason, Long actorUserId) {
    this.orderId = orderId;
    this.reason = reason;
    this.actorUserId = actorUserId;
  }

  public static PointRollback of(String orderId, String reason, Long actorUserId) {
    if (orderId == null || orderId.isBlank()) {
      throw new InvalidPointEntryException("巻き戻す受注は必須です");
    }
    if (reason == null || reason.isBlank()) {
      throw new InvalidPointEntryException("巻き戻しの理由は必須です");
    }
    return new PointRollback(orderId, reason, actorUserId);
  }

  @Override
  public String toString() {
    return "PointRollback(id=" + getId() + ", orderId=" + orderId + ")";
  }
}
