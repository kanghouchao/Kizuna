package com.kizuna.shift.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

/**
 * 出勤希望集約。キャストが所属店舗を指定して提出する勤務希望と、確定済みシフトへの変更申請を単一の状態系列で表す。
 *
 * <p>NEW の承認は確定（CONFIRMED）Shift を新規作成し、CHANGE の承認は {@link #targetShiftId} のシフトを更新する。 いずれも希望自体は
 * Shift へ変化せず、申請の履歴として残る。
 */
@Entity
@Table(name = "t_shift_requests")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftRequest extends StoreScopedEntity {

  @Column(name = "cast_id", nullable = false, length = 64)
  private String castId;

  @Column(name = "work_date", nullable = false)
  private LocalDate workDate;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  /** 終了時刻。start_time 以下の場合は翌日にまたがる勤務として扱う（Shift と同語義、解釈は表示側）。 */
  @Column(name = "end_time", nullable = false)
  private LocalTime endTime;

  @Column(name = "note", length = 500)
  private String note;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private ShiftRequestStatus status = ShiftRequestStatus.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 20)
  @Builder.Default
  private ShiftRequestKind kind = ShiftRequestKind.NEW;

  /** 変更申請（CHANGE）が対象とする確定済みシフトの id。新規希望（NEW）では null。 */
  @Column(name = "target_shift_id", length = 64)
  private String targetShiftId;

  @Column(name = "decided_by", length = 255)
  private String decidedBy;

  @Column(name = "decided_at")
  private OffsetDateTime decidedAt;

  /**
   * 変更申請の内容を、対象シフトへの部分更新コマンドへ写す。
   *
   * <p>載せるのは申請が持つ日付・時刻だけで、他の成分は null（＝変更しない）に固定する。申請が持たない属性 — 担当キャスト・確定ステータス、および将来シフト側に増える設定 —
   * を承認が巻き込んで書き換えないことを、 判断ではなく構造で保証するための写像。
   */
  public ShiftPatch toShiftPatch() {
    if (kind != ShiftRequestKind.CHANGE) {
      throw new ShiftRequestStateException("変更申請ではないためシフトを更新できません");
    }
    return new ShiftPatch(null, workDate, startTime, endTime, null, null);
  }

  /** PENDING の希望のみ承認できる。それ以外は状態例外を投げる（処理済みへの再処理を拒否）。 */
  public void approve(String actor) {
    requirePending();
    this.status = ShiftRequestStatus.APPROVED;
    this.decidedBy = actor;
    this.decidedAt = OffsetDateTime.now();
  }

  /** PENDING の希望のみ却下できる。それ以外は状態例外を投げる。 */
  public void decline(String actor) {
    requirePending();
    this.status = ShiftRequestStatus.DECLINED;
    this.decidedBy = actor;
    this.decidedAt = OffsetDateTime.now();
  }

  /** NEW の承認後も、希望から確定シフトへ同じ正本系列を辿れるように関連付ける。 */
  public void linkToShift(String shiftId) {
    this.targetShiftId = shiftId;
  }

  private void requirePending() {
    if (status != ShiftRequestStatus.PENDING) {
      throw new ShiftRequestStateException("この出勤希望は既に処理済みです");
    }
  }

  @Override
  public String toString() {
    return "ShiftRequest(id="
        + getId()
        + ", castId="
        + castId
        + ", workDate="
        + workDate
        + ", startTime="
        + startTime
        + ", endTime="
        + endTime
        + ", status="
        + status
        + ", kind="
        + kind
        + ", targetShiftId="
        + targetShiftId
        + ")";
  }
}
