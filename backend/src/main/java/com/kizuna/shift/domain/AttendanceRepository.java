package com.kizuna.shift.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<Attendance, String> {

  /**
   * 指定営業日の未取消の実績。取消行は導出・照会から外す（ADR 0014）ため、この口からは見えない。
   *
   * <p>並びは実開始時刻に一意な副キー id を添えて全順序にする。
   */
  List<Attendance> findByBusinessDateAndCancelledAtIsNullOrderByActualStartAtAscIdAsc(
      LocalDate businessDate);

  /** 上をキャストで絞った形。 */
  List<Attendance> findByBusinessDateAndCastIdAndCancelledAtIsNullOrderByActualStartAtAscIdAsc(
      LocalDate businessDate, String castId);

  /**
   * 与えたシフトのうち、未取消の実績が付いているものの id。予実交差の守衛（禁改・変更申請の三面）は 例外なくこの一本から述語を得る —
   * 「取消済みを数えない」を単体判定と一括判定に二度書くと、片方だけが直る日が来る。
   */
  @Query(
      """
      select a.shiftId from com.kizuna.shift.domain.Attendance a
      where a.shiftId in :shiftIds and a.cancelledAt is null
      """)
  Set<String> findShiftIdsWithActiveAttendance(@Param("shiftIds") Collection<String> shiftIds);

  /** そのシフトに未取消の実績が付いているか。単体の判定も一括の問い合わせから導く。 */
  default boolean hasActiveAttendance(String shiftId) {
    return !findShiftIdsWithActiveAttendance(List.of(shiftId)).isEmpty();
  }

  /** そのシフトの未取消の実績。取消済みは導出・照会から外れる（ADR 0014）ので、部分一意索引の効く範囲と一致し、 返るのは高々 1 行である。 */
  Optional<Attendance> findByShiftIdAndCancelledAtIsNull(String shiftId);

  /** そのシフトを参照する実績があるか。削除の可否だけは取消済みも数える（ADR 0014）。 */
  boolean existsByShiftId(String shiftId);

  /** その店舗に実績行があるか。取消済みも数える。 */
  boolean existsByStoreId(Long storeId);

  /**
   * そのキャストへ届く実績の件数。取消済みも数える。
   *
   * <p>自身の cast_id だけでは足りない。実績を取り消せばシフトの付け替えが解禁されるので（ADR 0014 の逃げ道）、 実績の cast_id とシフトの cast_id
   * は永久に一致するとは限らない。付け替え先のキャストを削除するとシフトへ連鎖し、 実績のシフト側外部キーに当たる — その経路もここで数える。
   */
  @Query(
      """
      select count(a) from com.kizuna.shift.domain.Attendance a
      where a.castId = :castId
        or a.shiftId in (
          select s.id from com.kizuna.shift.domain.Shift s where s.castId = :castId
        )
      """)
  long countReferencingCast(@Param("castId") String castId);
}
