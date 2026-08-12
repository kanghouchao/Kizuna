package com.kizuna.order.api.dto;

import com.kizuna.customer.domain.Customer;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderPatch;
import com.kizuna.order.domain.OrderView;
import com.kizuna.order.domain.PlatformOrderView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** 注文エンティティとDTOのマッピングを行うMapStructマッパー。 */
@Mapper(componentModel = "spring")
public interface OrderMapper {

  // ==================== View(projection) -> Response ====================

  /** 読み側 projection をレスポンスDTOに変換します。 */
  OrderResponse toResponse(OrderView view);

  /** 平台横断一覧の projection をレスポンスDTOに変換します（集合作用域）。 */
  PlatformOrderResponse toPlatformResponse(PlatformOrderView view);

  // ==================== CreateRequest -> Entity ====================

  /**
   * 注文作成リクエストDTOを注文エンティティに変換します。 注: 関連 ID（顧客、キャスト、受付担当）はサービス層で存在確認後に割り当てます。
   *
   * @param request 注文作成リクエスト
   * @return 注文エンティティ
   */
  @Mapping(target = "locationAddress", source = "address")
  @Mapping(target = "locationBuilding", source = "buildingName")
  @Mapping(target = "status", constant = "CREATED")
  @Mapping(target = "surveyStatus", ignore = true)
  @Mapping(target = "actualArrivalTime", ignore = true)
  @Mapping(target = "actualEndTime", ignore = true)
  // 会計とポイントは完了処理でのみ確定する（作成の契約は受け付けない）
  @Mapping(target = "totalFee", ignore = true)
  @Mapping(target = "usedPoints", ignore = true)
  @Mapping(target = "autoGrantPoints", ignore = true)
  // 連絡先の写しは顧客に着かなかった受注にだけ入る（判定はサービス層）
  @Mapping(target = "contactName", ignore = true)
  @Mapping(target = "contactPhoneNumber", ignore = true)
  // 関連 ID - サービス層で存在確認後に割り当て
  @Mapping(target = "customerId", ignore = true)
  @Mapping(target = "castId", ignore = true)
  @Mapping(target = "receptionistId", ignore = true)
  // 申請者は会員ポータル経由の受注だけが持つ
  @Mapping(target = "requesterMemberId", ignore = true)
  @Mapping(target = "requesterMemberCode", ignore = true)
  @Mapping(target = "requesterDeclaredName", ignore = true)
  Order toEntity(OrderCreateRequest request);

  // ==================== UpdateRequest -> Patch ====================

  /** 注文更新リクエストをドメインの部分更新コマンドに変換します。null フィールドは「変更しない」。 */
  OrderPatch toPatch(OrderUpdateRequest request);

  // ==================== CreateRequest -> Customer（電話番号からの顧客スマートリンク用） ====================

  @Mapping(target = "name", source = "customerName")
  // rank は DB デフォルト（'SILVER'）と同義。注文経由の顧客作成でも通常作成と揃える
  @Mapping(target = "rank", constant = "SILVER")
  @Mapping(target = "lineId", ignore = true)
  @Mapping(target = "usageAreas", ignore = true)
  Customer toCustomer(OrderCreateRequest request);
}
