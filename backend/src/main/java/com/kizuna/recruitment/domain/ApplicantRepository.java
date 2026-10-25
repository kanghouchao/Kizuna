package com.kizuna.recruitment.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ApplicantRepository
    extends StoreScopedRepository<Applicant>, JpaSpecificationExecutor<Applicant> {
  @Query("select a.status from Applicant a where a.id = :id and a.storeId = :storeId")
  Optional<ApplicantStatus> findStatusByIdAndStoreId(String id, Long storeId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Applicant a where a.id = :id and a.storeId = :storeId")
  Optional<Applicant> findByIdAndStoreIdForUpdate(String id, Long storeId);
}
