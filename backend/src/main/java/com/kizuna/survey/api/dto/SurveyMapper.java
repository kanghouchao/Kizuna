package com.kizuna.survey.api.dto;

import com.kizuna.survey.domain.SurveyAnswer;
import com.kizuna.survey.domain.SurveyAnswerDetailView;
import com.kizuna.survey.domain.SurveyInput;
import com.kizuna.survey.domain.SurveyRevision;
import com.kizuna.survey.domain.SurveyRevisionDetailView;
import java.time.OffsetDateTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface SurveyMapper {
  SurveyMapper INSTANCE = Mappers.getMapper(SurveyMapper.class);

  @Mapping(
      target = "createdBy",
      expression = "java(actor(row.getRecordedBy(),row.getRecorderName()))")
  @Mapping(target = "retentionPolicy", constant = "NOT_CONFIGURED")
  SurveyRevisionResponse revision(SurveyRevision row);

  @Mapping(
      target = "createdBy",
      expression = "java(actor(row.getRecordedBy(),row.getRecorderName()))")
  @Mapping(target = "retentionPolicy", constant = "NOT_CONFIGURED")
  SurveyRevisionResponse revision(SurveyRevisionDetailView row);

  @Mapping(
      target = "recordedBy",
      expression = "java(actor(row.getRecordedBy(),row.getRecorderName()))")
  @Mapping(target = "retentionPolicy", constant = "NOT_CONFIGURED")
  @Mapping(target = "intakeSource", constant = "STAFF_RECORDED")
  SurveyAnswerResponse answer(SurveyAnswer row);

  @Mapping(
      target = "recordedBy",
      expression = "java(actor(row.getRecordedBy(),row.getRecorderName()))")
  @Mapping(target = "retentionPolicy", constant = "NOT_CONFIGURED")
  @Mapping(target = "intakeSource", constant = "STAFF_RECORDED")
  SurveyAnswerResponse answer(SurveyAnswerDetailView row);

  default SurveyActorResponse actor(Long id, String name) {
    return new SurveyActorResponse(id.toString(), name);
  }

  default OffsetDateTime timestamp(OffsetDateTime value) {
    return value == null ? null : SurveyInput.time(value);
  }
}
