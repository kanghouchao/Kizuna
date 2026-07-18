package com.kizuna.shift.domain;

import com.kizuna.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_shifts")
@Filter(name = "tenantFilter", condition = "store_id = :storeId")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shift extends TenantScopedEntity {

  @Column(name = "cast_id", nullable = false, length = 64)
  private String castId;

  @Column(name = "work_date", nullable = false)
  private LocalDate workDate;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  /** 終了時刻。start_time 以下の場合は翌日にまたがる勤務として扱う（解釈は表示側）。 */
  @Column(name = "end_time", nullable = false)
  private LocalTime endTime;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

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
