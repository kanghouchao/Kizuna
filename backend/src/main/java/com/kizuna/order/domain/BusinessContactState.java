package com.kizuna.order.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kizuna.customer.domain.ContactPermissionStatus;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record BusinessContactState(
    String value, ContactPermissionStatus status, String source, String reason) {
  public static BusinessContactState unknown(String value) {
    return value == null
        ? null
        : new BusinessContactState(value, ContactPermissionStatus.UNKNOWN, null, null);
  }
}
