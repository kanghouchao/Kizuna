package com.kizuna.shift.infrastructure;

import com.kizuna.shift.domain.AttendanceRepository;
import com.kizuna.store.domain.AttendanceRecordCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** {@link AttendanceRecordCheck} の shift モジュール実装。AttendanceRepository の存在確認へ委譲する。 */
@Component
@RequiredArgsConstructor
class AttendanceRepositoryRecordCheck implements AttendanceRecordCheck {

  private final AttendanceRepository attendanceRepository;

  @Override
  public boolean existsForStore(long storeId) {
    return attendanceRepository.existsByStoreId(storeId);
  }
}
