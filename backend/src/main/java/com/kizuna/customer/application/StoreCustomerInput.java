package com.kizuna.customer.application;

import org.springframework.modulith.NamedInterface;

@NamedInterface("application")
public record StoreCustomerInput(
    String customerId,
    String name,
    String phoneNumber,
    String phoneNumber2,
    String address,
    String buildingName,
    String landmark,
    String classification,
    Boolean hasPet,
    String ngType,
    String ngContent) {}
