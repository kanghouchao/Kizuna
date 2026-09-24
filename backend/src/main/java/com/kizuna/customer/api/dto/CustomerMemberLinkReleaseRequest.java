package com.kizuna.customer.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomerMemberLinkReleaseRequest(
    @NotBlank(message = "関連 ID は必須です") String expectedLinkId,
    @NotBlank(message = "操作理由は必須です") String operationReason) {}
