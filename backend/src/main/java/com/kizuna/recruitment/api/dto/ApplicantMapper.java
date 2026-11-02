package com.kizuna.recruitment.api.dto;

import com.kizuna.recruitment.domain.Applicant;
import com.kizuna.recruitment.domain.ApplicantStatusHistory;
import com.kizuna.recruitment.domain.ApplicantSummaryView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ApplicantMapper {
  @Mapping(target = "id", source = "applicant.id")
  @Mapping(target = "name", source = "applicant.name")
  @Mapping(target = "medium", source = "medium")
  ApplicantResponse toResponse(Applicant applicant, ApplicantMediumResponse medium);

  @Mapping(target = "id", source = "applicant.id")
  @Mapping(target = "name", source = "applicant.name")
  @Mapping(target = "medium", source = "medium")
  ApplicantSummaryResponse toSummary(
      ApplicantSummaryView applicant, ApplicantMediumResponse medium);

  ApplicantHistoryResponse toHistory(ApplicantStatusHistory history);
}
