package com.kizuna.mapper.tenant;

import com.kizuna.model.dto.tenant.customer.CustomerCreateRequest;
import com.kizuna.model.dto.tenant.customer.CustomerResponse;
import com.kizuna.model.dto.tenant.customer.CustomerUpdateRequest;
import com.kizuna.model.dto.tenant.order.OrderCreateRequest;
import com.kizuna.model.entity.tenant.Customer;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface CustomerMapper {

  CustomerResponse toResponse(Customer customer);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenant", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "points", constant = "0")
  @Mapping(target = "landmark", ignore = true)
  Customer toEntity(CustomerCreateRequest request);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenant", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "points", ignore = true)
  @Mapping(target = "landmark", ignore = true)
  void updateEntityFromRequest(CustomerUpdateRequest request, @MappingTarget Customer customer);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenant", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "points", constant = "0")
  @Mapping(target = "name", source = "customerName")
  Customer toCustomer(OrderCreateRequest request);
}
