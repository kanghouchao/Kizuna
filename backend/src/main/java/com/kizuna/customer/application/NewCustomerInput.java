package com.kizuna.customer.application;

import org.springframework.modulith.NamedInterface;

@NamedInterface("application")
public record NewCustomerInput(String name, String phoneNumber) {}
