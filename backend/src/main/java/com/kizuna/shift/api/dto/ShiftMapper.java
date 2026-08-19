package com.kizuna.shift.api.dto;

import com.kizuna.shift.domain.CastScheduleView;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftPatch;
import java.time.LocalTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShiftMapper {

  /** 日付変更時刻は行にも要求にも無く、設定から解決した値を呼び手が渡す（{@link Shift#scheduledStartAt}）。 */
  @Mapping(target = "scheduledStartAt", expression = "java(shift.scheduledStartAt(dateChangeTime))")
  @Mapping(target = "scheduledEndAt", expression = "java(shift.scheduledEndAt(dateChangeTime))")
  ShiftResponse toResponse(Shift shift, LocalTime dateChangeTime);

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
