package com.kizuna.cast.api.dto;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastPatch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CastMapper {

  CastResponse toResponse(Cast cast);

  @Mapping(target = "status", defaultValue = "ACTIVE")
  Cast toEntity(CastCreateRequest request);

  /** 更新リクエストをドメインの部分更新コマンドに変換します。null フィールドは「変更しない」。 */
  CastPatch toPatch(CastUpdateRequest request);
}
