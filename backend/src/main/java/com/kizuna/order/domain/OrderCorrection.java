package com.kizuna.order.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;

/**
 * 完了した受注が 1 回訂正された事実。訂正前の姿・訂正者・時刻・理由を持つ（当日実績の訂正履歴の拡張）。
 *
 * <p>標量の前値は列で、明細行の前値は行集合全体の写しで持つ。行に同一性が無い以上、変わった行だけを名指す形は 作れない。前値の鎖で履歴を復元できる —
 * ある訂正の「後値」＝次の訂正の「前値」または本体の現値である。
 *
 * <p>受注側の痕はここまでで、訂正仕訳の横断形式は精算の領分。前後の合計がこの鎖から導出できるため、後から 取材して接続できる。
 *
 * <p>全フィールドが確定した事実で、書き換える操作を持たない。訂正者だけは利用者の削除で欠落しうる（FK が SET NULL）。
 */
@Entity
@Table(name = "t_order_corrections")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
public class OrderCorrection extends StoreScopedEntity {

  @Column(name = "order_id", nullable = false, updatable = false, length = 64)
  private String orderId;

  /** 訂正の理由。凍結済みの記録を動かす特権操作なので必須で、分類軸ではない（取消理由と同じ紀律）。 */
  @Column(name = "reason", nullable = false, updatable = false, length = 500)
  private String reason;

  @Column(name = "actual_arrival_time", updatable = false)
  private LocalTime actualArrivalTime;

  @Column(name = "actual_end_time", updatable = false)
  private LocalTime actualEndTime;

  @Column(name = "course_name", updatable = false, length = 255)
  private String courseName;

  @Column(name = "course_minutes", updatable = false)
  private Integer courseMinutes;

  @Column(name = "extension_minutes", updatable = false)
  private Integer extensionMinutes;

  /** 訂正前の合計。明細の前値から導出できるが列でも持つ — 差額の集計が行集合を展開せずに読めるようにする。 */
  @Column(name = "total_fee", updatable = false)
  private Integer totalFee;

  /** 訂正前の明細の全行（システム専有の行も含む）。門が触れない行も載せ、履歴行だけで前の姿が読めるようにする。 */
  @Type(JsonBinaryType.class)
  @Column(name = "fee_lines", columnDefinition = "jsonb", updatable = false)
  private List<OrderFeeLineSnapshot> feeLines;

  @Column(name = "corrected_by", updatable = false)
  private Long correctedBy;

  @Column(name = "corrected_at", nullable = false, updatable = false)
  private OffsetDateTime correctedAt;

  private OrderCorrection(
      Order before, String reason, Long correctedBy, OffsetDateTime correctedAt) {
    this.orderId = before.getId();
    this.reason = reason;
    this.actualArrivalTime = before.getActualArrivalTime();
    this.actualEndTime = before.getActualEndTime();
    this.courseName = before.getCourseName();
    this.courseMinutes = before.getCourseMinutes();
    this.extensionMinutes = before.getExtensionMinutes();
    this.totalFee = before.getTotalFee();
    this.feeLines = before.getFeeLines().stream().map(OrderFeeLineSnapshot::of).toList();
    this.correctedBy = correctedBy;
    this.correctedAt = correctedAt;
  }

  /**
   * 訂正を適用する<b>前</b>の受注から履歴を起こす。適用後に呼ぶと訂正後の姿を前値として記録してしまう。
   *
   * <p>理由と実行者はここで必須にする。理由の無い訂正が通ると、凍結済みの記録がなぜ動いたかを後から辿れない。
   */
  public static OrderCorrection snapshotOf(
      Order before, String reason, Long correctedBy, OffsetDateTime correctedAt) {
    if (reason == null || reason.isBlank()) {
      throw new InvalidOrderCorrectionException("訂正の理由は必須です");
    }
    if (correctedBy == null) {
      throw new InvalidOrderCorrectionException("訂正の実行者は必須です");
    }
    return new OrderCorrection(before, reason, correctedBy, correctedAt);
  }

  @Override
  public String toString() {
    return "OrderCorrection(id="
        + getId()
        + ", orderId="
        + orderId
        + ", correctedAt="
        + correctedAt
        + ")";
  }
}
