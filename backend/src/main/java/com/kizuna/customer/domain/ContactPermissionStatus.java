package com.kizuna.customer.domain;

public enum ContactPermissionStatus {
  UNKNOWN,
  ALLOWED,
  DENIED;

  public ContactPermissionStatus restrict(ContactPermissionStatus other) {
    if (this == DENIED || other == DENIED) return DENIED;
    if (this == UNKNOWN || other == UNKNOWN) return UNKNOWN;
    return ALLOWED;
  }
}
