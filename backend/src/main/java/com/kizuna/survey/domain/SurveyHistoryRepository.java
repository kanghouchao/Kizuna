package com.kizuna.survey.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SurveyHistoryRepository
    extends JpaRepository<SurveyHistory, String>, JpaSpecificationExecutor<SurveyHistory> {}
