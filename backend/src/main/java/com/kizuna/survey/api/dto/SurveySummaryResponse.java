package com.kizuna.survey.api.dto;

import com.kizuna.survey.domain.SurveyValues.RevisionStatus;
import java.time.OffsetDateTime;

public record SurveySummaryResponse(
    String id,
    String latestRevisionId,
    int latestRevisionNumber,
    String latestTitle,
    RevisionStatus latestStatus,
    String openRevisionId,
    String draftRevisionId,
    OffsetDateTime createdAt) {}
