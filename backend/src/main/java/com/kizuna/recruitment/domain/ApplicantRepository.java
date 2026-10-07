package com.kizuna.recruitment.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ApplicantRepository
    extends JpaRepository<Applicant, String>, JpaSpecificationExecutor<Applicant> {
  @Query("select a.status from Applicant a where a.id = :id")
  Optional<ApplicantStatus> findScopedStatus(String id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Applicant a where a.id = :id")
  Optional<Applicant> findScopedForUpdate(String id);
}
