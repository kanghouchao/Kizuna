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

/**
 * MapStruct mapper for Order entity and DTOs. Handles conversion between Order entity and
 * OrderResponse/OrderCreateRequest/OrderUpdateRequest.
 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface OrderMapper {

  // ==================== Entity -> Response ====================

  /**
   * Convert Order entity to OrderResponse DTO.
   *
   * @param order the order entity
   * @return the order response DTO
   */
  @Mapping(target = "receptionistId", source = "receptionist.id")
  @Mapping(target = "receptionistName", source = "receptionist.nickname")
  @Mapping(target = "customerId", source = "customer.id")
  @Mapping(target = "customerName", source = "customer.name")
  @Mapping(target = "girlId", source = "girl.id")
  @Mapping(target = "girlName", source = "girl.name")
  OrderResponse toResponse(Order order);

  // ==================== CreateRequest -> Entity ====================

  /**
   * Convert OrderCreateRequest DTO to Order entity. Note: Associated entities (Customer, Girl,
   * Receptionist) should be set manually in the service layer after ID lookup.
   *
   * @param request the order create request
   * @return the order entity
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
  // Associated entities - handled manually in service
  @Mapping(target = "customer", ignore = true)
  @Mapping(target = "girl", ignore = true)
  @Mapping(target = "receptionist", ignore = true)
  Order toEntity(OrderCreateRequest request);

  // ==================== UpdateRequest -> Entity (Partial Update) ====================

  /**
   * Update Order entity from OrderUpdateRequest DTO. Only non-null fields from request will be
   * applied (nullValuePropertyMappingStrategy = IGNORE).
   *
   * @param request the order update request
   * @param order the existing order entity to update
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenant", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "businessDate", ignore = true) // Business date should not be updated
  @Mapping(target = "carrier", ignore = true) // Carrier should not be updated
  @Mapping(target = "mediaName", ignore = true) // MediaName should not be updated
  @Mapping(target = "surveyStatus", ignore = true)
  @Mapping(target = "actualArrivalTime", ignore = true)
  @Mapping(target = "actualEndTime", ignore = true)
  @Mapping(target = "locationAddress", ignore = true) // Not in UpdateRequest
  @Mapping(target = "locationBuilding", ignore = true) // Not in UpdateRequest
  // Associated entities - handled manually in service
  @Mapping(target = "customer", ignore = true)
  @Mapping(target = "girl", ignore = true)
  @Mapping(target = "receptionist", ignore = true)
  void updateEntityFromRequest(OrderUpdateRequest request, @MappingTarget Order order);
}
