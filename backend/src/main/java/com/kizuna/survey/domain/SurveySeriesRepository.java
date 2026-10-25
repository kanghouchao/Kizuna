package com.kizuna.survey.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SurveySeriesRepository
    extends StoreScopedRepository<SurveySeries>, JpaSpecificationExecutor<SurveySeries> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from SurveySeries r where r.id = :id and r.storeId = :storeId")
  Optional<SurveySeries> findByIdAndStoreIdForUpdate(String id, Long storeId);

  Optional<SurveySeriesView> findProjectedByIdAndStoreId(String id, Long storeId);

  boolean existsByIdAndStoreId(String id, Long storeId);
}
