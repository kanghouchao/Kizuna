package com.kizuna.recruitment.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface AttachmentUploadRepository
    extends StoreScopedRepository<AttachmentUpload>, JpaSpecificationExecutor<AttachmentUpload> {
  Optional<AttachmentUpload> findByStoreIdAndApplicantIdAndIdempotencyKey(
      Long storeId, String applicantId, UUID key);

  List<AttachmentUpload> findByStoreIdAndApplicantId(Long storeId, String applicantId);

  Optional<AttachmentUpload> findByIdAndStoreIdAndApplicantId(
      String id, Long storeId, String applicantId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
  @Query(
      "select u from AttachmentUpload u where u.id = :id and u.storeId = :storeId and u.applicantId = :applicantId")
  Optional<AttachmentUpload> findByIdAndStoreIdAndApplicantIdForUpdate(
      String id, Long storeId, String applicantId);

  @Query(
      "select count(u) from AttachmentUpload u where u.storeId = :storeId and u.applicantId = :applicantId")
  long countReserved(Long storeId, String applicantId);

  @Query(
      "select coalesce(sum(u.sizeBytes), 0) from AttachmentUpload u where u.storeId = :storeId and u.applicantId = :applicantId")
  long reservedBytes(Long storeId, String applicantId);
}
