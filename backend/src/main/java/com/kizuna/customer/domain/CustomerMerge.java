package com.kizuna.customer.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

/**
 * 顧客統合が 1 回実行された事実。統合に取消（undo）は無く、誤統合の修復は「誰が・いつ・どの行をどの行へ・何を移したか」を
 * 根拠とする人手作業になるため、この記録がその根拠そのものである（ADR 0010）。
 *
 * <p>移した件数は統合時の一括 UPDATE が報告した件数をそのまま持つ。後から存続行を数え直す形にすると、統合後に積まれた受注・関連まで 数えてしまい、何が移ったのかの根拠にならない。
 *
 * <p>全フィールドが確定した事実で、書き換える操作を持たない。実行者だけは利用者の削除で欠落しうる（FK が SET NULL）—
 * 表示名が引けなくなっても行ごと消さないためで、関連（{@link CustomerMemberLink}）の実行者と同じ紀律である。
 */
@Entity
@Table(name = "t_customer_merges")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
public class CustomerMerge extends StoreScopedEntity {

  @Column(name = "surviving_customer_id", nullable = false, updatable = false, length = 64)
  private String survivingCustomerId;

  @Column(name = "merged_customer_id", nullable = false, updatable = false, length = 64)
  private String mergedCustomerId;

  @Column(name = "merged_by", updatable = false)
  private Long mergedBy;

  @Column(name = "merged_at", nullable = false, updatable = false)
  private OffsetDateTime mergedAt;

  @Column(name = "moved_order_count", nullable = false, updatable = false)
  private int movedOrderCount;

  @Column(name = "moved_link_count", nullable = false, updatable = false)
  private int movedLinkCount;

  private CustomerMerge(
      String survivingCustomerId,
      String mergedCustomerId,
      Long mergedBy,
      OffsetDateTime mergedAt,
      int movedOrderCount,
      int movedLinkCount) {
    this.survivingCustomerId = survivingCustomerId;
    this.mergedCustomerId = mergedCustomerId;
    this.mergedBy = mergedBy;
    this.mergedAt = mergedAt;
    this.movedOrderCount = movedOrderCount;
    this.movedLinkCount = movedLinkCount;
  }

  /**
   * 実行された統合を記録する。値はすべて統合の実行そのものが決めたもので、要求から直接来るものは無い（実行者は認証主体から、件数は 一括 UPDATE
   * の結果から）。欠落は要求誤りではなく実装欠陥なので、NOT NULL 列による大きな失敗に委ねて構築時の検証は置かない。
   */
  public static CustomerMerge record(
      String survivingCustomerId,
      String mergedCustomerId,
      Long mergedBy,
      OffsetDateTime mergedAt,
      int movedOrderCount,
      int movedLinkCount) {
    return new CustomerMerge(
        survivingCustomerId, mergedCustomerId, mergedBy, mergedAt, movedOrderCount, movedLinkCount);
  }

  @Override
  public String toString() {
    return "CustomerMerge(id="
        + getId()
        + ", survivingCustomerId="
        + survivingCustomerId
        + ", mergedCustomerId="
        + mergedCustomerId
        + ")";
  }
}
