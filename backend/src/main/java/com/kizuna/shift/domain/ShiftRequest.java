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
 * 出勤希望集約。キャストが所属店舗を指定して提出する勤務希望（新規希望・確定シフトへの変更申請の両種別）。
 *
 * <p>NEW の承認で確定（CONFIRMED）Shift を新規作成し、CHANGE の承認で対象 Shift の日時を更新する。 希望自体は Shift へ変化せず、申請の履歴として残る。
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
  @Column(name = "request_type", nullable = false, length = 20)
  @Builder.Default
  private ShiftRequestType type = ShiftRequestType.NEW;

  /**
   * 関連シフト id — 系列（希望→承認→変更申請）の背骨。CHANGE では提出時の対象シフト、NEW では承認で生成したシフトを指す。 PENDING・DECLINED の NEW では
   * null。対象シフトが削除されると DB 側で null に落ち、申請は履歴として残る。
   */
  @Column(name = "shift_id", length = 64)
  private String shiftId;

  /** 変更申請時点の対象シフト勤務日。申請後に対象シフトが編集されたことの検知に使う（NEW では null）。 */
  @Column(name = "original_work_date")
  private LocalDate originalWorkDate;

  /** 変更申請時点の対象シフト開始時刻（NEW では null）。 */
  @Column(name = "original_start_time")
  private LocalTime originalStartTime;

  /** 変更申請時点の対象シフト終了時刻（NEW では null）。 */
  @Column(name = "original_end_time")
  private LocalTime originalEndTime;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private ShiftRequestStatus status = ShiftRequestStatus.PENDING;

  /** 承認・却下を実行した者（PlatformUser id）。未処理では null。利用者削除後も申請は残す（FK が SET NULL）。 */
  @Column(name = "processed_by")
  private Long processedBy;

  /** 承認・却下の実行時刻。未処理では null。 */
  @Column(name = "processed_at")
  private OffsetDateTime processedAt;

  /** PENDING の希望のみ承認できる。それ以外は状態例外を投げる（処理済みへの再処理を拒否）。 */
  public void approve(Long processedBy, OffsetDateTime processedAt) {
    requirePending();
    this.status = ShiftRequestStatus.APPROVED;
    this.processedBy = processedBy;
    this.processedAt = processedAt;
  }

  /** PENDING の希望のみ却下できる。それ以外は状態例外を投げる。 */
  public void decline(Long processedBy, OffsetDateTime processedAt) {
    requirePending();
    this.status = ShiftRequestStatus.DECLINED;
    this.processedBy = processedBy;
    this.processedAt = processedAt;
  }

  /** NEW の承認で生成したシフトを申請行へ結ぶ。 */
  public void linkShift(String shiftId) {
    this.shiftId = shiftId;
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
        + ", type="
        + type
        + ", shiftId="
        + shiftId
        + ", status="
        + status
        + ")";
  }
}
