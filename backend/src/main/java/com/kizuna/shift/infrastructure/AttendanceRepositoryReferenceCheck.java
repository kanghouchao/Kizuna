package com.kizuna.shift.infrastructure;

import com.kizuna.cast.domain.AttendanceReferenceCheck;
import com.kizuna.shift.domain.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link AttendanceReferenceCheck} の shift モジュール実装。
 *
 * <p>数え方（実績自身の cast_id と、そのキャストのシフト経由の参照の両方）の理由は {@code AttendanceRepository#countReferencingCast}
 * の Javadoc にある。
 */
@Component
@RequiredArgsConstructor
class AttendanceRepositoryReferenceCheck implements AttendanceReferenceCheck {

  private final AttendanceRepository attendanceRepository;

  @Override
  public boolean existsForCast(String castId) {
    return attendanceRepository.countReferencingCast(castId) > 0;
  }
}
