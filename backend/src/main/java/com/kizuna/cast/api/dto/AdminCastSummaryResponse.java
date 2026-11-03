package com.kizuna.cast.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.cast.domain.CastPersonSummaryView;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminCastSummaryResponse(Long id, String displayName, String realName) {

  public static AdminCastSummaryResponse of(CastPersonSummaryView row) {
    return new AdminCastSummaryResponse(row.getId(), row.getDisplayName(), row.getRealName());
  }
}
