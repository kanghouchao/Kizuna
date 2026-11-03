package com.kizuna.shift.api.dto;

import com.kizuna.shift.application.ShiftViews.ShiftView;
import com.kizuna.shift.domain.CastScheduleView;
import com.kizuna.shift.domain.Shift;
import java.time.LocalTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShiftMapper {

  /** 日付変更時刻は行にも要求にも無く、設定から解決した値を呼び手が渡す（{@link Shift#scheduledStartAt}）。 */
  @Mapping(target = "scheduledStartAt", expression = "java(shift.scheduledStartAt(dateChangeTime))")
  @Mapping(target = "scheduledEndAt", expression = "java(shift.scheduledEndAt(dateChangeTime))")
  ShiftResponse toResponse(Shift shift, LocalTime dateChangeTime);

  default ShiftResponse toResponse(ShiftView view) {
    return toResponse(view.shift(), view.dateChangeTime());
  }

  /** 本人ポータル週間スケジュールの読み側 projection をレスポンスDTOに変換します。 */
  CastScheduleResponse toScheduleResponse(CastScheduleView view);
}
