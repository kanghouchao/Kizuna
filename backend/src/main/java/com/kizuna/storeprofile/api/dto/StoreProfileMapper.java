package com.kizuna.storeprofile.api.dto;

import com.kizuna.storeprofile.domain.StoreProfile;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/** StoreProfile エンティティと DTO 間の変換を行う MapStruct マッパー。 */
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface StoreProfileMapper {

  /**
   * StoreProfile エンティティを StoreProfileResponse DTO に変換する。
   *
   * @param config 店舗設定エンティティ
   * @return 店舗設定レスポンス DTO
   */
  StoreProfileResponse toResponse(StoreProfile config);

  /**
   * StoreProfileUpdateRequest の非 null フィールドを既存エンティティに反映する。
   *
   * @param request 更新リクエスト
   * @param config 既存の店舗設定エンティティ
   */
  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "storeId", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  void updateEntityFromRequest(
      StoreProfileUpdateRequest request, @MappingTarget StoreProfile config);
}
