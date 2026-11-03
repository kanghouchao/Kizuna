package com.kizuna.cast.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.cast.domain.CastPersonView;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AdminCastResponse(
    Long id, Long userId, String displayName, String realName, LocalDate birthDate) {

  public static AdminCastResponse of(CastPersonView row) {
    return new AdminCastResponse(
        row.getId(), row.getUserId(), row.getDisplayName(), row.getRealName(), row.getBirthDate());
  }
}
