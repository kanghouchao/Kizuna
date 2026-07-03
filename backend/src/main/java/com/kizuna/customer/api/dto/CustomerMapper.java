package com.kizuna.customer.api.dto;

import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerPatch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

  CustomerResponse toResponse(Customer customer);

  @Mapping(target = "points", constant = "0")
  @Mapping(target = "landmark", ignore = true)
  // rank は DB デフォルト（'SILVER'）と同義。エンティティに列をマッピングしたため明示的に補完する
  @Mapping(target = "rank", source = "rank", defaultValue = "SILVER")
  Customer toEntity(CustomerCreateRequest request);

  /** 更新リクエストをドメインの部分更新コマンドに変換します。null フィールドは「変更しない」。 */
  CustomerPatch toPatch(CustomerUpdateRequest request);
}
