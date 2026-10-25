package com.kizuna.survey.domain;

import com.kizuna.shared.persistence.StoreScopedRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SurveyHistoryRepository
    extends StoreScopedRepository<SurveyHistory>, JpaSpecificationExecutor<SurveyHistory> {}
