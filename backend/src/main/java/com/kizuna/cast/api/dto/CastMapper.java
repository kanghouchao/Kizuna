package com.kizuna.cast.api.dto;

import com.kizuna.cast.application.CastCreateCommand;
import com.kizuna.cast.application.CastDetail;
import com.kizuna.cast.application.CastUpdateCommand;
import com.kizuna.cast.domain.CastEnrollment;
import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastProfile;
import com.kizuna.cast.domain.CastProfilePatch;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CastMapper {
  @Mapping(target = ".", source = "profile")
  @Mapping(target = "id", source = "enrollment.id")
  @Mapping(target = "status", source = "enrollment.status")
  @Mapping(target = "startedOn", source = "enrollment.startedOn")
  @Mapping(target = "endedAt", source = "enrollment.endedAt")
  @Mapping(target = "createdAt", source = "enrollment.createdAt")
  @Mapping(target = "updatedAt", source = "enrollment.updatedAt")
  @Mapping(
      target = "customFields",
      expression = "java(combine(detail.enrollment(), detail.profile()))")
  CastResponse toResponse(CastDetail detail);

  @Mapping(target = ".", source = "profile")
  @Mapping(target = "id", source = "enrollment.id")
  @Mapping(target = "status", source = "enrollment.status")
  @Mapping(target = "startedOn", source = "enrollment.startedOn")
  CastSummaryResponse toSummaryResponse(CastDetail detail);

  CastCreateCommand toCommand(CastCreateRequest request);

  CastProfilePatch toPatch(CastUpdateRequest request);

  default CastUpdateCommand toCommand(CastUpdateRequest request) {
    return new CastUpdateCommand(toPatch(request), request.getCustomFields());
  }

  @Mapping(target = "id", source = "enrollmentId")
  @Mapping(target = "customFields", ignore = true)
  CastPublicResponse toPublicResponseBase(CastProfile profile);

  default Map<String, String> combine(CastEnrollment enrollment, CastProfile profile) {
    Map<String, String> values = new HashMap<>(enrollment.getCustomFields());
    values.putAll(profile.getCustomFields());
    return values;
  }

  default CastPublicResponse toPublicResponse(
      CastProfile profile, List<CastFieldDefinition> definitions) {
    CastPublicResponse response = toPublicResponseBase(profile);
    response.setCustomFields(
        definitions.stream()
            .sorted(Comparator.comparing(CastFieldDefinition::getDisplayOrder))
            .filter(definition -> Boolean.TRUE.equals(definition.getIsPublic()))
            .filter(
                definition ->
                    profile.getCustomFields().get(definition.getKey()) != null
                        && !profile.getCustomFields().get(definition.getKey()).isEmpty())
            .map(
                definition ->
                    new CastCustomFieldView(
                        definition.getKey(),
                        definition.getLabel(),
                        profile.getCustomFields().get(definition.getKey())))
            .toList());
    return response;
  }
}
