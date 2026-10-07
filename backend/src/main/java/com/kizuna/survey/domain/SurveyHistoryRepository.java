package com.kizuna.survey.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SurveyHistoryRepository
    extends JpaRepository<SurveyHistory, String>, JpaSpecificationExecutor<SurveyHistory> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from SurveyHistory r where r.id = :id")
  Optional<SurveyHistory> lockById(String id);
}
