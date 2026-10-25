package com.kizuna.survey.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import java.util.Optional;

public interface SurveyOperationRepository extends StoreScopedRepository<SurveyOperation> {
  Optional<SurveyOperation> findByStoreIdAndActorIdAndDedupeKey(
      Long storeId, Long actorId, String key);
}
