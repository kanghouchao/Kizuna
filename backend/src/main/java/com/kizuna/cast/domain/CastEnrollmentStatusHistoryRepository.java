package com.kizuna.cast.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CastEnrollmentStatusHistoryRepository
    extends StoreScopedRepository<CastEnrollmentStatusHistory> {
  Page<CastEnrollmentStatusHistory> findByStoreIdAndEnrollmentId(
      Long storeId, String enrollmentId, Pageable pageable);
}
