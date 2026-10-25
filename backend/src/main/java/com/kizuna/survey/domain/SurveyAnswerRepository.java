package com.kizuna.survey.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SurveyAnswerRepository
    extends StoreScopedRepository<SurveyAnswer>, JpaSpecificationExecutor<SurveyAnswer> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from SurveyAnswer r where r.id = :id and r.storeId = :storeId")
  Optional<SurveyAnswer> findByIdAndStoreIdForUpdate(String id, Long storeId);

  @Query(
      "select count(r) as totalRecords, coalesce(sum(case when r.status = com.kizuna.survey.domain.SurveyValues$AnswerStatus.ACTIVE then 1 else 0 end),0) as activeRecords, coalesce(sum(case when r.status = com.kizuna.survey.domain.SurveyValues$AnswerStatus.WITHDRAWN then 1 else 0 end),0) as withdrawnRecords from SurveyAnswer r where r.storeId = :storeId and r.revisionId = :revisionId")
  SurveyCountsView counts(Long storeId, String revisionId);

  Optional<SurveyAnswerDetailView> findProjectedByIdAndStoreId(String id, Long storeId);

  boolean existsByIdAndStoreId(String id, Long storeId);
}
