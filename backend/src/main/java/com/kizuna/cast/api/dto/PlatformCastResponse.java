package com.kizuna.cast.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PlatformCastResponse(
    Long id, Long platformUserId, String displayName, String realName, LocalDate birthDate) {}
