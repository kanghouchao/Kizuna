package com.kizuna.customer.domain;

public record ContactPermissions(
    ContactPermissionStatus business, ContactPermissionStatus marketing) {
  public ContactPermissions restrict(ContactPermissions other) {
    return new ContactPermissions(
        business.restrict(other.business), marketing.restrict(other.marketing));
  }
}
