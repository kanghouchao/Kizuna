package com.kizuna.shift.api.dto;

import com.kizuna.shift.application.ShiftViews.InboxRequest;
import com.kizuna.shift.domain.CastShiftRequestView;
import com.kizuna.shift.domain.ShiftRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShiftRequestMapper {

  /** 本人ポータルの提出応答へ変換します。 */
  ShiftRequestResponse toResponse(ShiftRequest request);

  /** 承認・却下の応答。対象シフトを解決しないので current_* と approvable は null のまま返す。 */
  @Mapping(target = "currentWorkDate", ignore = true)
  @Mapping(target = "currentStartTime", ignore = true)
  @Mapping(target = "currentEndTime", ignore = true)
  @Mapping(target = "approvable", ignore = true)
  StoreShiftRequestResponse toStoreResponse(ShiftRequest request);

  /** inbox の 1 件。変更申請だけが対象シフトの現行日時（current_*）をインラインで載せる。 */
  default StoreShiftRequestResponse toStoreResponse(InboxRequest item) {
    StoreShiftRequestResponse response = toStoreResponse(item.request());
    if (item.target() != null) {
      response.setCurrentWorkDate(item.target().getWorkDate());
      response.setCurrentStartTime(item.target().getStartTime());
      response.setCurrentEndTime(item.target().getEndTime());
    }
    response.setApprovable(item.approvable());
    return response;
  }

  /** 本人ポータル履歴の読み側 projection を応答 DTO に変換します。 */
  CastShiftRequestResponse toHistoryResponse(CastShiftRequestView view);
}
