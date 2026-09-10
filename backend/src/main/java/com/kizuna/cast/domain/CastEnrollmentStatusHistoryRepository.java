package com.kizuna.cast.domain;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CastEnrollmentStatusHistoryRepository
    extends JpaRepository<CastEnrollmentStatusHistory, String> {
  List<CastEnrollmentStatusHistory> findByEnrollmentIdOrderByRecordedAtDescIdDesc(
      String enrollmentId, Limit limit);

  @Query(
      "select h from CastEnrollmentStatusHistory h where h.enrollmentId = :enrollmentId and (h.recordedAt < :at or (h.recordedAt = :at and h.id < :id)) order by h.recordedAt desc, h.id desc")
  List<CastEnrollmentStatusHistory> findAfter(
      String enrollmentId, OffsetDateTime at, String id, Limit limit);
}
