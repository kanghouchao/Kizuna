package com.kizuna.shift.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

/**
 * 排班の事実の唯一の正本。系列は双起点一主幹で、キャストの出勤希望の承認から生まれる行と店舗が直接作成する行の どちらもこの表に合流する。
 *
 * <p>実行者は「行を書いた操作の実行者を印字する」規則で持つ。直接編集は最後の一手のみが残り、中間履歴は保たない。
 */
@Entity
@Table(name = "t_shifts")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shift extends StoreScopedEntity {

  @Column(name = "cast_id", nullable = false, length = 64)
  private String castId;

  @Column(name = "work_date", nullable = false)
  private LocalDate workDate;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  /** 終了時刻。start_time 以下の場合は翌日にまたがる勤務として扱う（解釈は表示側）。 */
  @Column(name = "end_time", nullable = false)
  private LocalTime endTime;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ShiftStatus status;

  /**
   * 店外（匿名訪問者+会員）へ露出してよいか。承認とは独立の軸で、状態機械の一部ではない（ADR 0015）。 既定は公開可 —
   * 非公開で出生させたい行は作成・承認が同一トランザクションで指定する。
   */
  @Builder.Default
  @Column(name = "is_published", nullable = false)
  private boolean published = true;

  /** この行を作成した操作の実行者（PlatformUser id）。承認で生まれた行なら承認者。利用者削除後も行は残す（FK が SET NULL）。 */
  @Column(name = "created_by", updatable = false)
  private Long createdBy;

  /** この行を最後に書き換えた操作の実行者（PlatformUser id）。作成のみの行では null。 */
  @Column(name = "updated_by")
  private Long updatedBy;

  /** 部分更新コマンドを適用する。null のフィールドは変更しない。 */
  public void apply(ShiftPatch patch) {
    if (patch.castId() != null) {
      this.castId = patch.castId();
    }
    if (patch.workDate() != null) {
      this.workDate = patch.workDate();
    }
    if (patch.startTime() != null) {
      this.startTime = patch.startTime();
    }
    if (patch.endTime() != null) {
      this.endTime = patch.endTime();
    }
    if (patch.status() != null) {
      this.status = patch.status();
    }
  }

  /**
   * 予定終了の暦日付き時刻。終了時刻が開始時刻以下の行は日跨ぎとして翌日へ送る。
   *
   * <p>跨ぎの解釈は表示側にも散っているが、判定に使う分はここへ寄せる — 欠勤導出の門が「予定終了の経過」を 問う以上、跨ぎを読み違えれば進行中のシフトがそのまま欠勤に化ける。
   */
  public LocalDateTime scheduledEndAt() {
    LocalDate endDate = endTime.isAfter(startTime) ? workDate : workDate.plusDays(1);
    return endDate.atTime(endTime);
  }

  /** 店外への露出可否を切り替える。部分更新コマンドとは別の口で受け、承認・時間帯の編集が公開可否を巻き込めないようにする。 */
  public void changePublication(boolean published) {
    this.published = published;
  }

  /** 書き換えの実行者を印字する。実行者は要求ではなく認証主体から来るため、部分更新コマンドとは別の口で受ける。 */
  public void stampUpdatedBy(Long actorId) {
    this.updatedBy = actorId;
  }

  @Override
  public String toString() {
    return "Shift(id="
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
