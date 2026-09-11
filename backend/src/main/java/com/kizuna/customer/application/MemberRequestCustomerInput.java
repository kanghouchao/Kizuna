package com.kizuna.customer.application;

import org.springframework.modulith.NamedInterface;

@NamedInterface("application")
public record MemberRequestCustomerInput(
    Long memberId, String memberCode, String declaredName, Long actorId) {}
