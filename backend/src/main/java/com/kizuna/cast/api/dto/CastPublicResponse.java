package com.kizuna.cast.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公開キャスト詳細の応答。管理用 {@link CastResponse} と異なり invitationStatus を持たず、customFields は
 * 公開・生存・値ありの定義のみを表示順に整形した {@link CastCustomFieldView} のリストとして返す。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CastPublicResponse {
  private String id;
  private String name;
  private String status;
  private String photoUrl;
  private String introduction;
  private Integer age;
  private Integer height;
  private Integer bust;
  private Integer waist;
  private Integer hip;
  private Integer displayOrder;
  private List<CastCustomFieldView> customFields;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
