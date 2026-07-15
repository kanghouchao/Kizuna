package com.kizuna.cast.api.dto;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastInvitationStatus;
import com.kizuna.cast.domain.CastPatch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CastMapper {

  @Mapping(target = "invitationStatus", ignore = true)
  CastResponse toResponse(Cast cast);

  /** 招待状態を詰めた応答に変換する（一覧・詳細用）。 */
  @Mapping(target = "invitationStatus", source = "invitationStatus")
  CastResponse toResponse(Cast cast, CastInvitationStatus invitationStatus);

  @Mapping(target = "status", defaultValue = "ACTIVE")
  @Mapping(target = "platformUserId", ignore = true)
  Cast toEntity(CastCreateRequest request);

  /** 更新リクエストをドメインの部分更新コマンドに変換します。null フィールドは「変更しない」。 */
  CastPatch toPatch(CastUpdateRequest request);
}
