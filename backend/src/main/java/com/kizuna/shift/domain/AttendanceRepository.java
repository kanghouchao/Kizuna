package com.kizuna.shift.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends StoreScopedRepository<Attendance> {

  /**
   * 指定営業日の未取消の実績。取消行は導出・照会から外す（ADR 0014）ため、この口からは見えない。
   *
   * <p>並びは実開始時刻に一意な副キー id を添えて全順序にする。
   */
  List<Attendance> findByStoreIdAndBusinessDateAndCancelledAtIsNullOrderByActualStartAtAscIdAsc(
      Long storeId, LocalDate businessDate);

  /** 上をキャストで絞った形。 */
  List<Attendance>
      findByStoreIdAndBusinessDateAndCastIdAndCancelledAtIsNullOrderByActualStartAtAscIdAsc(
          Long storeId, LocalDate businessDate, String castId);

  /**
   * 与えたシフトのうち、未取消の実績が付いているものの id。予実交差の守衛（禁改・変更申請の三面）は 例外なくこの一本から述語を得る —
   * 「取消済みを数えない」を単体判定と一括判定に二度書くと、片方だけが直る日が来る。
   */
  @Query(
      """
      select a.shiftId from com.kizuna.shift.domain.Attendance a
      where a.storeId = :storeId and a.shiftId in :shiftIds and a.cancelledAt is null
      """)
  Set<String> findShiftIdsWithActiveAttendance(
      @Param("storeId") Long storeId, @Param("shiftIds") Collection<String> shiftIds);

  /** そのシフトに未取消の実績が付いているか。単体の判定も一括の問い合わせから導く。 */
  default boolean hasActiveAttendance(Long storeId, String shiftId) {
    return !findShiftIdsWithActiveAttendance(storeId, List.of(shiftId)).isEmpty();
  }

  /**
   * 指定営業日に未取消の実績があるキャスト。欠勤導出が「出勤した」を数える唯一の口である。
   *
   * <p>投影で引くのは、欠勤導出が要るのがキャストの集合だけで、並びを要さないためである。
   */
  @Query(
      """
      select distinct a.castId from com.kizuna.shift.domain.Attendance a
      where a.storeId = :storeId and a.businessDate = :businessDate and a.cancelledAt is null
      """)
  Set<String> findCastIdsWithActiveAttendanceOn(
      @Param("storeId") Long storeId, @Param("businessDate") LocalDate businessDate);

  /** そのシフトの未取消の実績。取消済みは照会から外れる（ADR 0014）ので、返るのは部分一意索引どおり高々 1 行である。 */
  Optional<Attendance> findByStoreIdAndShiftIdAndCancelledAtIsNull(Long storeId, String shiftId);

  /** そのシフトを参照する実績があるか。削除の可否だけは取消済みも数える（ADR 0014）。 */
  boolean existsByStoreIdAndShiftId(Long storeId, String shiftId);

  /** その店舗に実績行があるか。取消済みも数える。 */
  boolean existsByStoreId(Long storeId);

  /**
   * 実績自身の参照と、取消後の付け替え先シフトへの参照の両方を含む。取消済みも削除制約に残る。
   *
   * <p>店舗を跨ぐ参照照会。境界は呼び手が店舗条件で引いた在籍 id（cast_id）集合。
   */
  @Query(
      """
      select a.castId from com.kizuna.shift.domain.Attendance a
      where a.castId in :castIds
      union
      select s.castId from com.kizuna.shift.domain.Shift s
      join com.kizuna.shift.domain.Attendance a on a.shiftId = s.id and a.storeId = s.storeId
      where s.castId in :castIds
      """)
  Set<String> findReferencedCastIds(@Param("castIds") Collection<String> castIds);
}
