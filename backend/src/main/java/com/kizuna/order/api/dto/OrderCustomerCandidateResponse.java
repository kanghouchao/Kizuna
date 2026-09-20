package com.kizuna.order.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record OrderCustomerCandidateResponse(String id, String name, String phoneNumber) {}
