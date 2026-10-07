package com.kizuna.survey.domain;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SurveyRevisionRepository
    extends JpaRepository<SurveyRevision, String>, JpaSpecificationExecutor<SurveyRevision> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from SurveyRevision r where r.id = :id")
  Optional<SurveyRevision> lockById(String id);

  boolean existsByIdAndSurveyId(String id, String surveyId);

  boolean existsBySurveyIdAndStatus(String surveyId, SurveyValues.RevisionStatus status);

  Optional<SurveyRevision> findByIdAndSurveyId(String id, String surveyId);

  Optional<SurveyRevisionDetailView> findProjectedByIdAndSurveyId(String id, String surveyId);

  List<SurveyRevisionSummaryView> findProjectedByIdIn(Collection<String> ids);

  List<SurveyRevisionSummaryView> findProjectedBySurveyIdInAndStatusIn(
      Collection<String> surveyIds, Collection<SurveyValues.RevisionStatus> states);
}
