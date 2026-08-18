package com.kizuna.shift.api.dto;

import com.kizuna.shift.domain.CastScheduleView;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftPatch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShiftMapper {

  ShiftResponse toResponse(Shift shift);

  /** 実行者は要求ではなく認証主体から来るので、要求とは別の引数で受ける。書き換えの実行者は作成時には無い。 */
  @Mapping(target = "status", source = "request.status", defaultValue = "TENTATIVE")
  @Mapping(target = "published", source = "request.published", defaultValue = "true")
  @Mapping(target = "createdBy", source = "createdBy")
  @Mapping(target = "updatedBy", ignore = true)
  Shift toEntity(ShiftCreateRequest request, Long createdBy);

  /** 更新リクエストをドメインの部分更新コマンドに変換します。null フィールドは「変更しない」。 */
  ShiftPatch toPatch(ShiftUpdateRequest request);

  /** 本人ポータル週間スケジュールの読み側 projection をレスポンスDTOに変換します。 */
  CastScheduleResponse toScheduleResponse(CastScheduleView view);
}
