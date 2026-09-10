package com.kizuna.cast.api.dto;

import com.kizuna.cast.domain.CastPublicationStatus;
import jakarta.validation.constraints.NotNull;

public record CastPublicationRequest(@NotNull CastPublicationStatus publicationStatus) {}
