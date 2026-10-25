package com.kizuna.survey.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SurveyRevisionRepository
    extends StoreScopedRepository<SurveyRevision>, JpaSpecificationExecutor<SurveyRevision> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from SurveyRevision r where r.id = :id and r.storeId = :storeId")
  Optional<SurveyRevision> findByIdAndStoreIdForUpdate(String id, Long storeId);

  boolean existsByIdAndStoreIdAndSurveyId(String id, Long storeId, String surveyId);

  boolean existsByStoreIdAndSurveyIdAndStatus(
      Long storeId, String surveyId, SurveyValues.RevisionStatus status);

  Optional<SurveyRevision> findByIdAndStoreIdAndSurveyId(String id, Long storeId, String surveyId);

  Optional<SurveyRevisionDetailView> findProjectedByIdAndStoreIdAndSurveyId(
      String id, Long storeId, String surveyId);

  List<SurveyRevisionSummaryView> findProjectedByIdInAndStoreId(
      Collection<String> ids, Long storeId);

  List<SurveyRevisionSummaryView> findProjectedByStoreIdAndSurveyIdInAndStatusIn(
      Long storeId, Collection<String> surveyIds, Collection<SurveyValues.RevisionStatus> states);
}
