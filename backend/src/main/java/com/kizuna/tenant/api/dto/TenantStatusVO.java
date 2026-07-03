package com.kizuna.tenant.api.dto;

public record TenantStatusVO(long total, long active, long inactive, long pending) {}
