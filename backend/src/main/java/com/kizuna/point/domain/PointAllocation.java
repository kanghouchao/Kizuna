package com.kizuna.point.domain;

import com.kizuna.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 減算仕訳が「どの加算仕訳（ロット）から何ポイント引き当てたか」の 1 行。減算仕訳集約の構成要素であり、単体で作られることはない。
 *
 * <p>この行があることで、加算ロットごとの消費済み量が合計 1 本で求まり、残高が加算量と引き当て量の差として一意に決まる。
 *
 * <p>不変条件（構築時に検証、違反は 400 系ドメイン例外）: 引き当て元の仕訳 ID が必須で、引き当て量は 1 以上（{@link
 * InvalidPointEntryException}）。
 */
@Entity
@Table(name = "t_point_usage_allocations")
@Getter
@NoArgsConstructor
public class PointAllocation extends BaseEntity {

  @Column(name = "source_entry_id", nullable = false, updatable = false)
  private Long sourceEntryId;

  @Column(name = "amount", nullable = false, updatable = false)
  private Integer amount;

  private PointAllocation(Long sourceEntryId, int amount) {
    this.sourceEntryId = sourceEntryId;
    this.amount = amount;
  }

  /** 加算仕訳 {@code sourceEntryId} から {@code amount} ポイントを引き当てる。 */
  public static PointAllocation of(Long sourceEntryId, int amount) {
    if (sourceEntryId == null) {
      throw new InvalidPointEntryException("引き当て元の仕訳 ID は必須です");
    }
    if (amount <= 0) {
      throw new InvalidPointEntryException("引き当て量は 1 以上で指定してください");
    }
    return new PointAllocation(sourceEntryId, amount);
  }

  @Override
  public String toString() {
    return "PointAllocation(id="
        + getId()
        + ", sourceEntryId="
        + sourceEntryId
        + ", amount="
        + amount
        + ")";
  }
}
