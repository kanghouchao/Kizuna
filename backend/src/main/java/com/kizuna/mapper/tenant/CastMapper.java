package com.kizuna.mapper.tenant;

import com.kizuna.model.dto.tenant.cast.CastCreateRequest;
import com.kizuna.model.dto.tenant.cast.CastResponse;
import com.kizuna.model.dto.tenant.cast.CastUpdateRequest;
import com.kizuna.model.entity.tenant.Cast;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface CastMapper {

  CastResponse toResponse(Cast cast);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenant", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "status", defaultValue = "ACTIVE")
  Cast toEntity(CastCreateRequest request);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenant", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  void updateEntityFromRequest(CastUpdateRequest request, @MappingTarget Cast cast);
}
