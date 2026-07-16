package com.kizuna.cast.api.dto;

import com.kizuna.cast.domain.CastInvitationStatus;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CastResponse {
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
  private CastInvitationStatus invitationStatus;
  private Map<String, String> customFields;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
