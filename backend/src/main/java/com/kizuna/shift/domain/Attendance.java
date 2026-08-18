package com.kizuna.shift.domain;

import com.kizuna.shared.persistence.StoreScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

/**
 * キャストが実際に出勤した事実の一等記録。シフト（予定）とは別集約で、予定の無い飛び込み出勤も shift_id = NULL の一等記録として成立する（ADR 0014）。
 *
 * <p>行の存在そのものが出勤確認であり、確認フラグも状態機械も持たない。物理削除はせず（労基法 109 条）、誤建行は取消標記で 導出・照会から外す。訂正は就地更新と同時に {@link
 * AttendanceCorrection} へ編集前の姿を残す。
 */
@Entity
@Table(name = "t_attendances")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
public class Attendance extends StoreScopedEntity {

  @Column(name = "cast_id", nullable = false, updatable = false, length = 64)
  private String castId;

  /** 帰属営業日。シフト紐づきは work_date を継承し、飛び込みは実開始時刻から判定した値を物化する。 */
  @Column(name = "business_date", nullable = false)
  private LocalDate businessDate;

  @Column(name = "actual_start_at", nullable = false)
  private LocalDateTime actualStartAt;

  /** 実際の終了。閉店時の記入まで NULL 可。 */
  @Column(name = "actual_end_at")
  private LocalDateTime actualEndAt;

  /** 予定していたシフト。NULL は飛び込み出勤で、「予定外の出勤か否か」が照会可能な事実になる。 */
  @Column(name = "shift_id", updatable = false, length = 64)
  private String shiftId;

  @Column(name = "waiting_place", length = 200)
  private String waitingPlace;

  @Column(name = "cancelled_at")
  private OffsetDateTime cancelledAt;

  @Column(name = "cancelled_by")
  private Long cancelledBy;

  @Column(name = "created_by", updatable = false)
  private Long createdBy;

  @Column(name = "updated_by")
  private Long updatedBy;

  private Attendance(
      String castId,
      String shiftId,
      LocalDate businessDate,
      LocalDateTime actualStartAt,
      LocalDateTime actualEndAt,
      String waitingPlace,
      Long createdBy) {
    this.castId = castId;
    this.businessDate = businessDate;
    this.actualStartAt = actualStartAt;
    this.actualEndAt = actualEndAt;
    this.shiftId = shiftId;
    this.waitingPlace = waitingPlace;
    this.createdBy = createdBy;
  }

  /** 出勤の事実を記録する。帰属営業日は呼出側が決める — シフト紐づきは work_date の継承、飛び込みは実開始時刻からの判定で、 どちらもこの集約の外にある情報を要する。 */
  public static Attendance record(
      String castId,
      String shiftId,
      LocalDate businessDate,
      LocalDateTime actualStartAt,
      LocalDateTime actualEndAt,
      String waitingPlace,
      Long createdBy) {
    requireEndAfterStart(actualStartAt, actualEndAt);
    return new Attendance(
        castId, shiftId, businessDate, actualStartAt, actualEndAt, waitingPlace, createdBy);
  }

  /**
   * 記入の誤りを訂正する。キャストとシフト参照は訂正の対象にしない — 予実の付け替えは実績が物化した事実を破るため、 逃げ道は取消 → 再記録に限る（ADR 0014）。
   *
   * <p>帰属営業日を明示で受けるのは、日付変更時刻の変更が不遡及であることの裏返しである。変更前に起きた飛び込みを 変更後に事後補記すると営業日を取り違えるので、その救済口がここに要る。
   */
  public void correct(
      LocalDate businessDate,
      LocalDateTime actualStartAt,
      LocalDateTime actualEndAt,
      String waitingPlace,
      Long actorId) {
    if (isCancelled()) {
      throw new AttendanceStateException("取消済みの実績は訂正できません");
    }
    requireEndAfterStart(actualStartAt, actualEndAt);
    this.businessDate = businessDate;
    this.actualStartAt = actualStartAt;
    this.actualEndAt = actualEndAt;
    this.waitingPlace = waitingPlace;
    this.updatedBy = actorId;
  }

  /** 取消標記を付ける。二度目は静默冪等に委ねず撥ねる — 誰の取消として記録するかが一意に決まらなくなる。 */
  public void cancel(Long actorId, OffsetDateTime at) {
    if (isCancelled()) {
      throw new AttendanceStateException("既に取消済みの実績です");
    }
    this.cancelledAt = at;
    this.cancelledBy = actorId;
  }

  public boolean isCancelled() {
    return cancelledAt != null;
  }

  /** 実終了は実開始より後でなければならない。未記入（NULL）は閉店時までの正常な途中状態として許す。 */
  private static void requireEndAfterStart(LocalDateTime start, LocalDateTime end) {
    if (end != null && !end.isAfter(start)) {
      throw new AttendanceStateException("実終了は実開始より後でなければなりません");
    }
  }

  @Override
  public String toString() {
    return "Attendance(id="
        + getId()
        + ", castId="
        + castId
        + ", businessDate="
        + businessDate
        + ", shiftId="
        + shiftId
        + ", cancelledAt="
        + cancelledAt
        + ")";
  }
}
