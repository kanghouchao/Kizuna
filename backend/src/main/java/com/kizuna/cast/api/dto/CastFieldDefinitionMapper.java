package com.kizuna.cast.api.dto;

import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastFieldDefinitionPatch;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CastFieldDefinitionMapper {

  CastFieldDefinitionResponse toResponse(CastFieldDefinition definition);

  /** 更新リクエストをドメインの部分更新コマンドに変換する。null フィールドは「変更しない」。 */
  CastFieldDefinitionPatch toPatch(CastFieldDefinitionUpdateRequest request);
}
