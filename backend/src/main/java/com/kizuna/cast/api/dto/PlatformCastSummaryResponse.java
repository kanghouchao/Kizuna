package com.kizuna.cast.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PlatformCastSummaryResponse(Long id, String displayName, String realName) {}
