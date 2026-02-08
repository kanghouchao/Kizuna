package com.kizuna.mapper.tenant;

import com.kizuna.model.dto.tenant.order.OrderCreateRequest;
import com.kizuna.model.dto.tenant.order.OrderResponse;
import com.kizuna.model.dto.tenant.order.OrderUpdateRequest;
import com.kizuna.model.entity.tenant.Order;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/** 注文エンティティとDTOのマッピングを行うMapStructマッパー。 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface OrderMapper {

  // ==================== Entity -> Response ====================

  /**
   * 注文エンティティをレスポンスDTOに変換します。
   *
   * @param order 注文エンティティ
   * @return 注文レスポンスDTO
   */
  @Mapping(target = "receptionistId", source = "receptionist.id")
  @Mapping(target = "receptionistName", source = "receptionist.nickname")
  @Mapping(target = "customerId", source = "customer.id")
  @Mapping(target = "customerName", source = "customer.name")
  @Mapping(target = "castId", source = "cast.id")
  @Mapping(target = "castName", source = "cast.name")
  OrderResponse toResponse(Order order);

  // ==================== CreateRequest -> Entity ====================

  /**
   * 注文作成リクエストDTOを注文エンティティに変換します。 注: 関連エンティティ（顧客、キャスト、受付担当）はサービス層でID検索後に手動設定する必要があります。
   *
   * @param request 注文作成リクエスト
   * @return 注文エンティティ
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenant", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "locationAddress", source = "address")
  @Mapping(target = "locationBuilding", source = "buildingName")
  @Mapping(target = "status", constant = "CREATED")
  @Mapping(target = "surveyStatus", ignore = true)
  @Mapping(target = "actualArrivalTime", ignore = true)
  @Mapping(target = "actualEndTime", ignore = true)
  // 関連エンティティ - サービス層で手動設定
  @Mapping(target = "customer", ignore = true)
  @Mapping(target = "cast", ignore = true)
  @Mapping(target = "receptionist", ignore = true)
  Order toEntity(OrderCreateRequest request);

  // ==================== UpdateRequest -> Entity (Partial Update) ====================

  /**
   * 注文更新リクエストDTOから注文エンティティを更新します。 リクエスト内の非nullフィールドのみが適用されます (nullValuePropertyMappingStrategy =
   * IGNORE)。
   *
   * @param request 注文更新リクエスト
   * @param order 更新対象の注文エンティティ
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenant", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "businessDate", ignore = true) // 営業日は更新不可
  @Mapping(target = "carrier", ignore = true) // キャリアは更新不可
  @Mapping(target = "mediaName", ignore = true) // 媒体名は更新不可
  @Mapping(target = "surveyStatus", ignore = true)
  @Mapping(target = "actualArrivalTime", ignore = true)
  @Mapping(target = "actualEndTime", ignore = true)
  @Mapping(target = "locationAddress", ignore = true) // 更新リクエストに含まれない
  @Mapping(target = "locationBuilding", ignore = true) // 更新リクエストに含まれない
  // 関連エンティティ - サービス層で手動設定
  @Mapping(target = "customer", ignore = true)
  @Mapping(target = "cast", ignore = true)
  @Mapping(target = "receptionist", ignore = true)
  void updateEntityFromRequest(OrderUpdateRequest request, @MappingTarget Order order);
}
