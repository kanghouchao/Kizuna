package com.kizuna.shift.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
