package com.kizuna.shift.api.dto;

import com.kizuna.shift.domain.CastShiftRequestView;
import com.kizuna.shift.domain.ShiftRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShiftRequestMapper {

  /** 本人ポータルの提出応答へ変換します。 */
  ShiftRequestResponse toResponse(ShiftRequest request);

  /** 店舗側 inbox 応答へ変換します。対象シフトの現行日時（current_*）と適用可否（approvable）はサービス層が別途内联します。 */
  @Mapping(target = "currentWorkDate", ignore = true)
  @Mapping(target = "currentStartTime", ignore = true)
  @Mapping(target = "currentEndTime", ignore = true)
  @Mapping(target = "approvable", ignore = true)
  StoreShiftRequestResponse toStoreResponse(ShiftRequest request);

  /** 本人ポータル履歴の読み側 projection を応答 DTO に変換します。 */
  CastShiftRequestResponse toHistoryResponse(CastShiftRequestView view);
}
