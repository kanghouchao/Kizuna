package com.kizuna.recruitment.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ApplicantStatusHistoryRepository
    extends StoreScopedRepository<ApplicantStatusHistory> {
  Page<ApplicantStatusHistory> findByStoreIdAndApplicantId(
      Long storeId, String applicantId, Pageable pageable);
}
