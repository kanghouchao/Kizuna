package com.kizuna.customer.api.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {
  private String id;
  private String name;
  private List<ContactSummary> preferredContacts;
  private String address;
  private String buildingName;
  private String landmark;
  private String classification;
  private Boolean hasPet;
  private String usageAreas;
  private String ngType;
  private String ngContent;

  /** 会員紐づけの有無。関連状態の投影であり、応答では必ず真偽値が入る（null にはならない）。 */
  private Boolean memberLinked;

  /** 紐づけ済みの会員コード。未紐づけなら null。 */
  private String linkedMemberCode;

  /** 統合済みの旧 ID で引かれたか。生きた行を引いた応答では null（欄が現れない）。 */
  private Boolean merged;

  /** 要求された旧 ID。本体は統合先の行なので、{@code id} との対で「どれがどれへ統合されたか」が判る。 */
  private String mergedFromId;
}
