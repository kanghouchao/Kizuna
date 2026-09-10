package com.kizuna.cast.domain;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CastEnrollmentSnapshotRepository
    extends JpaRepository<CastEnrollmentSnapshot, String> {
  List<CastEnrollmentSnapshot> findByEnrollmentIdOrderByRecordedAtDescIdDesc(
      String enrollmentId, Limit limit);

  @Query(
      "select h from CastEnrollmentSnapshot h where h.enrollmentId = :enrollmentId and (h.recordedAt < :at or (h.recordedAt = :at and h.id < :id)) order by h.recordedAt desc, h.id desc")
  List<CastEnrollmentSnapshot> findAfter(
      String enrollmentId, OffsetDateTime at, String id, Limit limit);
}
