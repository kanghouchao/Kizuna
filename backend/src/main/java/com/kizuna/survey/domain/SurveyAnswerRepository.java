package com.kizuna.survey.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SurveyAnswerRepository
    extends JpaRepository<SurveyAnswer, String>, JpaSpecificationExecutor<SurveyAnswer> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from SurveyAnswer r where r.id = :id")
  Optional<SurveyAnswer> lockById(String id);

  @Query(
      "select count(r) as totalRecords, coalesce(sum(case when r.status = com.kizuna.survey.domain.SurveyValues$AnswerStatus.ACTIVE then 1 else 0 end),0) as activeRecords, coalesce(sum(case when r.status = com.kizuna.survey.domain.SurveyValues$AnswerStatus.WITHDRAWN then 1 else 0 end),0) as withdrawnRecords from SurveyAnswer r where r.revisionId = :revisionId")
  SurveyCountsView counts(String revisionId);

  Optional<SurveyAnswerDetailView> findProjectedById(String id);
}
