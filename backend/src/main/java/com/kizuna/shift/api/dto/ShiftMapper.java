package com.kizuna.shift.api.dto;

import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftPatch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShiftMapper {

  ShiftResponse toResponse(Shift shift);

  @Mapping(target = "status", defaultValue = "TENTATIVE")
  Shift toEntity(ShiftCreateRequest request);

  /** 更新リクエストをドメインの部分更新コマンドに変換します。null フィールドは「変更しない」。 */
  ShiftPatch toPatch(ShiftUpdateRequest request);
}
