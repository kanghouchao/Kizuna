package com.kizuna.recruitment.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ApplicantRepository
    extends StoreScopedRepository<Applicant>, JpaSpecificationExecutor<Applicant> {
  @Query("select a.status from Applicant a where a.id = :id and a.storeId = :storeId")
  Optional<ApplicantStatus> findStatusByIdAndStoreId(String id, Long storeId);

  /** 店舗を跨ぐ参照照会。境界は呼び手が店舗条件で引いた在籍 id 集合。 */
  @Query("select a.enrollmentId from Applicant a where a.enrollmentId in :enrollmentIds")
  Set<String> findReferencedEnrollmentIds(Collection<String> enrollmentIds);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Applicant a where a.id = :id and a.storeId = :storeId")
  Optional<Applicant> findByIdAndStoreIdForUpdate(String id, Long storeId);
}
