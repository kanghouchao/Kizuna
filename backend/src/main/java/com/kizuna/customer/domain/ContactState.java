package com.kizuna.customer.domain;

public record ContactState(
    String customerId,
    ContactType type,
    String value,
    boolean preferred,
    boolean deleted,
    ContactPermissionStatus businessStatus,
    ContactPermissionStatus marketingStatus) {}
