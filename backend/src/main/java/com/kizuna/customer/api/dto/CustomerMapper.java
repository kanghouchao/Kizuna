package com.kizuna.customer.api.dto;

import com.kizuna.customer.domain.Customer;
import com.kizuna.customer.domain.CustomerPatch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

  // 会員紐づけは別集約の投影なので、application 層が写像後に補う。統合の標識も同様で、
  // 「要求された ID が何だったか」は写す元の行が持たない事実である。
  @Mapping(target = "memberLinked", ignore = true)
  @Mapping(target = "linkedMemberCode", ignore = true)
  @Mapping(target = "merged", ignore = true)
  @Mapping(target = "mergedFromId", ignore = true)
  CustomerResponse toResponse(Customer customer);

  // 同上。一覧は紐づけの有無だけを持ち、会員コードは載せない。
  @Mapping(target = "memberLinked", ignore = true)
  CustomerSummaryResponse toSummaryResponse(Customer customer);

  @Mapping(target = "landmark", ignore = true)
  // rank は DB デフォルト（'SILVER'）と同義。エンティティに列をマッピングしたため明示的に補完する
  @Mapping(target = "rank", source = "rank", defaultValue = "SILVER")
  // 起こしたばかりの行は定義上まだ生きている。統合先参照は統合だけが立てる。
  @Mapping(target = "mergedIntoId", ignore = true)
  Customer toEntity(CustomerCreateRequest request);

  /** 更新リクエストをドメインの部分更新コマンドに変換します。null フィールドは「変更しない」。 */
  CustomerPatch toPatch(CustomerUpdateRequest request);
}
