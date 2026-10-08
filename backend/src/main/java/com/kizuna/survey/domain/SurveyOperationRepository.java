package com.kizuna.survey.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SurveyOperationRepository
    extends JpaRepository<SurveyOperation, String>, JpaSpecificationExecutor<SurveyOperation> {
  Optional<SurveyOperation> findByActorIdAndDedupeKey(Long actorId, String key);
}
