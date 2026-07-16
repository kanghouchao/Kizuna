package com.kizuna.cast.api.dto;

import com.kizuna.cast.domain.Cast;
import com.kizuna.cast.domain.CastFieldDefinition;
import com.kizuna.cast.domain.CastInvitationStatus;
import com.kizuna.cast.domain.CastPatch;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

  @Mapping(target = "customFields", ignore = true)
  CastPublicResponse toPublicResponseBase(Cast cast);

  /**
   * 公開キャスト詳細に変換する。可視性フィルタの最終防波堤として、公開かつ値が非空の定義のみを表示順で整形する（リポジトリ側の絞り込みに二重で依存しない）。
   *
   * @param cast 対象キャスト
   * @param definitions 公開対象のカスタムフィールド定義（呼び出し側で公開・生存のみを渡す想定だが、本メソッドでも公開判定する）
   */
  default CastPublicResponse toPublicResponse(Cast cast, List<CastFieldDefinition> definitions) {
    CastPublicResponse response = toPublicResponseBase(cast);
    Map<String, String> values = Optional.ofNullable(cast.getCustomFields()).orElse(Map.of());
    List<CastCustomFieldView> views =
        definitions.stream()
            .filter(definition -> Boolean.TRUE.equals(definition.getIsPublic()))
            .sorted(Comparator.comparing(CastFieldDefinition::getDisplayOrder))
            .map(
                definition -> {
                  String value = values.get(definition.getKey());
                  return value == null || value.isEmpty()
                      ? null
                      : new CastCustomFieldView(definition.getKey(), definition.getLabel(), value);
                })
            .filter(Objects::nonNull)
            .toList();
    response.setCustomFields(views);
    return response;
  }
}
