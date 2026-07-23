package com.kizuna.shift.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

/**
 * 出勤希望集約。キャストが所属店舗を指定して提出する勤務希望。
 *
 * <p>承認で確定（CONFIRMED）Shift を新規作成する。希望自体は Shift へ変化せず、申請の履歴として残る。
 */
@Entity
@Table(name = "t_shift_requests")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
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

  /** PENDING の希望のみ承認できる。それ以外は状態例外を投げる（処理済みへの再処理を拒否）。 */
  public void approve() {
    requirePending();
    this.status = ShiftRequestStatus.APPROVED;
  }

  /** PENDING の希望のみ却下できる。それ以外は状態例外を投げる。 */
  public void decline() {
    requirePending();
    this.status = ShiftRequestStatus.DECLINED;
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
        + ")";
  }
}
