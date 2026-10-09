package com.kizuna.remuneration.api.dto;

public record GuaranteeMutationResponse(
    GuaranteeResponse guarantee, long version, String changeId) {}
