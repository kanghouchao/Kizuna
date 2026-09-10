package com.kizuna.shift.infrastructure;

import com.kizuna.cast.domain.AttendanceReferenceCheck;
import com.kizuna.shift.domain.AttendanceRepository;
import java.util.Collection;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AttendanceRepositoryReferenceCheck implements AttendanceReferenceCheck {

  private final AttendanceRepository attendanceRepository;

  @Override
  public Set<String> findReferencedCastIds(Collection<String> castIds) {
    return attendanceRepository.findReferencedCastIds(castIds);
  }
}
