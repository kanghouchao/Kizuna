package com.kizuna.shift.infrastructure;

import com.kizuna.cast.domain.AttendanceReferenceCheck;
import com.kizuna.shift.domain.AttendanceRepository;
import com.kizuna.store.domain.AttendanceRecordCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 店舗削除・キャスト削除の守衛が使う実績の存在確認（{@link AttendanceRecordCheck}・{@link AttendanceReferenceCheck}）の shift
 * モジュール実装。
 *
 * <p>キャスト側は行の cast_id だけを見れば足りる。実績の記録はシフトのキャストと実績のキャストの一致を要求するので、 そのキャストのシフトに紐づく実績も自身の cast_id
 * が同じキャストを指しているためである（飛び込みも同型）。
 */
@Component
@RequiredArgsConstructor
class AttendanceRepositoryRecordChecks implements AttendanceRecordCheck, AttendanceReferenceCheck {

  private final AttendanceRepository attendanceRepository;

  @Override
  public boolean existsForStore(long storeId) {
    return attendanceRepository.existsByStoreId(storeId);
  }

  @Override
  public boolean existsForCast(String castId) {
    return attendanceRepository.existsByCastId(castId);
  }
}
