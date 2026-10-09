package com.kizuna.remuneration.api.dto;

import org.springframework.data.domain.Page;

public record GuaranteeListResponse(long version, Page<GuaranteeResponse> entries) {}
