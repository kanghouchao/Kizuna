package com.kizuna.recruitment.api.dto;

import com.kizuna.recruitment.domain.Applicant;
import com.kizuna.recruitment.domain.ApplicantStatusHistory;
import com.kizuna.recruitment.domain.ApplicantSummaryView;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ApplicantMapper {
  ApplicantResponse toResponse(Applicant applicant);

  ApplicantSummaryResponse toSummary(ApplicantSummaryView applicant);

  ApplicantHistoryResponse toHistory(ApplicantStatusHistory history);
}
