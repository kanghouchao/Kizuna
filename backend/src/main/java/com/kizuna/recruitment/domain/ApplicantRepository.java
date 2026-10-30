package com.kizuna.recruitment.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Limit;
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

  /** 店舗を跨ぐ照会。グループ全体の匿名化処理だけが使い、以後の操作は各行の店舗で引き直す。終端は一度きりなので遷移履歴は一行に定まる。 */
  @Query(
      "select new com.kizuna.recruitment.domain.ApplicantAnonymizationTarget(a.id, a.storeId, h.createdAt) from Applicant a, ApplicantStatusHistory h where h.storeId = a.storeId and h.applicantId = a.id and h.newStatus = a.status and a.status = :status and a.anonymizedAt is null and h.createdAt < :before order by h.createdAt, a.id")
  List<ApplicantAnonymizationTarget> findAnonymizationTargets(
      ApplicantStatus status, OffsetDateTime before, Limit limit);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Applicant a where a.id = :id and a.storeId = :storeId")
  Optional<Applicant> findByIdAndStoreIdForUpdate(String id, Long storeId);
}
