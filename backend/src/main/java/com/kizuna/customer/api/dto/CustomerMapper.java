package com.kizuna.customer.api.dto;

import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerPatch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

  // 会員紐づけは別集約の投影なので、application 層が写像後に補う。
  @Mapping(target = "memberLinked", ignore = true)
  @Mapping(target = "linkedMemberCode", ignore = true)
  CustomerResponse toResponse(Customer customer);

  @Mapping(target = "landmark", ignore = true)
  // rank は DB デフォルト（'SILVER'）と同義。エンティティに列をマッピングしたため明示的に補完する
  @Mapping(target = "rank", source = "rank", defaultValue = "SILVER")
  // 起こしたばかりの行は定義上まだ生きている。統合先参照は統合だけが立てる。
  @Mapping(target = "mergedIntoId", ignore = true)
  Customer toEntity(CustomerCreateRequest request);

  /** 更新リクエストをドメインの部分更新コマンドに変換します。null フィールドは「変更しない」。 */
  CustomerPatch toPatch(CustomerUpdateRequest request);
}
