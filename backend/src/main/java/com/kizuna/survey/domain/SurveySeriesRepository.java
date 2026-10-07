package com.kizuna.survey.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SurveySeriesRepository
    extends JpaRepository<SurveySeries, String>, JpaSpecificationExecutor<SurveySeries> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from SurveySeries r where r.id = :id")
  Optional<SurveySeries> lockById(String id);

  Optional<SurveySeriesView> findProjectedById(String id);
}
