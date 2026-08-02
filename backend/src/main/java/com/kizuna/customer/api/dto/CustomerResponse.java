package com.kizuna.customer.api.dto;

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
  private String phoneNumber;
  private String phoneNumber2;
  private String address;
  private String buildingName;
  private String classification;
  private Boolean hasPet;
  private Integer points;
  private String rank;
  private String lineId;
  private String usageAreas;
  private String ngType;
  private String ngContent;

  /** 会員紐づけの有無。関連状態の投影であり、応答では必ず真偽値が入る（null にはならない）。 */
  private Boolean memberLinked;

  /** 紐づけ済みの会員コード。未紐づけなら null。 */
  private String linkedMemberCode;
}
