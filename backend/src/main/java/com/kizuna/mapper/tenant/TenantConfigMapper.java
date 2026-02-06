package com.kizuna.mapper.tenant;

import com.kizuna.model.dto.tenant.tenantconfig.TenantConfigResponse;
import com.kizuna.model.dto.tenant.tenantconfig.TenantConfigUpdateRequest;
import com.kizuna.model.entity.tenant.TenantConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/** TenantConfig エンティティと DTO 間の変換を行う MapStruct マッパー。 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TenantConfigMapper {

  /**
   * TenantConfig エンティティを TenantConfigResponse DTO に変換する。
   *
   * @param config テナント設定エンティティ
   * @return テナント設定レスポンス DTO
   */
  TenantConfigResponse toResponse(TenantConfig config);

  /**
   * TenantConfigUpdateRequest の非 null フィールドを既存エンティティに反映する。
   *
   * @param request 更新リクエスト
   * @param config 既存のテナント設定エンティティ
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tenant", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  void updateEntityFromRequest(
      TenantConfigUpdateRequest request, @MappingTarget TenantConfig config);
}
