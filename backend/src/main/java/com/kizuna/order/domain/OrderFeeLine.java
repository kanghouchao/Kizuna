package com.kizuna.order.domain;

import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 受注金額の内訳 1 行。種別・名称の写し・帯符号金額を持ち、受注集約の構成要素として単体では作られない。
 *
 * <p>名称は行が成立した時点の写しで、後から充填元（サービス定義）が変わっても書き換わらない。
 *
 * <p>不変条件（構築時に検証、違反は 400 系ドメイン例外）: 名称が非空白で、金額の符号が種別の約定に合うこと（{@link InvalidOrderFeeLineException}）。
 */
@Entity
@Table(name = "t_order_fee_lines")
@Getter
@NoArgsConstructor
public class OrderFeeLine extends BaseEntity {

  /**
   * 親受注。書き込みは受注集約の関連（{@code @JoinColumn}）が受け持つため読み取り専用で写像する。
   *
   * <p>読み口が受注 1 件ぶんの明細をまとめて引くときの結合鍵になる。
   */
  @Column(name = "order_id", insertable = false, updatable = false)
  private String orderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, updatable = false, length = 30)
  private OrderFeeLineKind kind;

  @Column(name = "name", nullable = false, updatable = false, length = 255)
  private String name;

  @Column(name = "amount", nullable = false, updatable = false)
  private Integer amount;

  private OrderFeeLine(OrderFeeLineKind kind, String name, int amount) {
    this.kind = kind;
    this.name = name;
    this.amount = amount;
  }

  /**
   * 明細行を起こす。
   *
   * @param amount 帯符号の金額。減項は負値で保存する
   */
  public static OrderFeeLine of(OrderFeeLineKind kind, String name, int amount) {
    if (kind == null) {
      throw new InvalidOrderFeeLineException("明細の種別は必須です");
    }
    if (name == null || name.isBlank()) {
      throw new InvalidOrderFeeLineException("明細の名称は必須です");
    }
    if (name.length() > 255) {
      throw new InvalidOrderFeeLineException("明細の名称は 255 文字以内です");
    }
    if (!kind.allows(amount)) {
      throw new InvalidOrderFeeLineException(
          "明細の金額が種別の符号約定に合いません: %s（加算の種別は 0 以上、減算の種別は 0 以下）".formatted(kind));
    }
    return new OrderFeeLine(kind, name, amount);
  }

  @Override
  public String toString() {
    return "OrderFeeLine(id=" + getId() + ", kind=" + kind + ", amount=" + amount + ")";
  }
}
