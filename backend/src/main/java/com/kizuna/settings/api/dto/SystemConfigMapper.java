package com.kizuna.settings.api.dto;

import com.kizuna.settings.domain.SystemConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface SystemConfigMapper {

  SystemConfigResponse toResponse(SystemConfig config);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "configKey", ignore = true)
  @Mapping(target = "category", ignore = true)
  @Mapping(target = "description", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "valueType", ignore = true)
  @Mapping(target = "secret", ignore = true)
  void updateEntityFromRequest(
      SystemConfigUpdateRequest request, @MappingTarget SystemConfig config);
}
