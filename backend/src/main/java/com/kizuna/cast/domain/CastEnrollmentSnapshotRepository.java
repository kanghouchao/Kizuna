package com.kizuna.cast.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CastEnrollmentSnapshotRepository
    extends StoreScopedRepository<CastEnrollmentSnapshot> {
  Page<CastEnrollmentSnapshot> findByStoreIdAndEnrollmentId(
      Long storeId, String enrollmentId, Pageable pageable);
}
