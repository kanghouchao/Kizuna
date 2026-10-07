package com.kizuna.recruitment.domain;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface AttachmentUploadRepository
    extends JpaRepository<AttachmentUpload, String>, JpaSpecificationExecutor<AttachmentUpload> {
  Optional<AttachmentUpload> findByApplicantIdAndIdempotencyKey(String applicantId, UUID key);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
  @Query("select u from AttachmentUpload u where u.id = :id and u.applicantId = :applicantId")
  Optional<AttachmentUpload> lockScoped(String applicantId, String id);

  @Query("select count(u) from AttachmentUpload u where u.applicantId = :applicantId")
  long countReserved(String applicantId);

  @Query(
      "select coalesce(sum(u.sizeBytes), 0) from AttachmentUpload u where u.applicantId = :applicantId")
  long reservedBytes(String applicantId);
}
