package com.kizuna.survey.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SurveyOperationRepository
    extends JpaRepository<SurveyOperation, String>, JpaSpecificationExecutor<SurveyOperation> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from SurveyOperation r where r.id = :id")
  Optional<SurveyOperation> lockById(String id);

  Optional<SurveyOperation> findByActorIdAndDedupeKey(Long actorId, String key);
}
