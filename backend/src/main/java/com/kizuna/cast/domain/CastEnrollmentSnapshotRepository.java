package com.kizuna.cast.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;

public interface CastEnrollmentSnapshotRepository
    extends StoreScopedRepository<CastEnrollmentSnapshot> {
  List<CastEnrollmentSnapshot> findByStoreIdAndEnrollmentIdOrderByRecordedAtDescIdDesc(
      Long storeId, String enrollmentId, Limit limit);

  @Query(
      "select h from CastEnrollmentSnapshot h where h.storeId = :storeId and h.enrollmentId = :enrollmentId and (h.recordedAt < :at or (h.recordedAt = :at and h.id < :id)) order by h.recordedAt desc, h.id desc")
  List<CastEnrollmentSnapshot> findAfter(
      Long storeId, String enrollmentId, OffsetDateTime at, String id, Limit limit);
}
