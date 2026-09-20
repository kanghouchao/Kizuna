package com.kizuna.customer.domain;

public interface ContactRestrictionView {
  ContactType getType();

  String getValue();

  ContactPermissionStatus getBusinessStatus();

  ContactPermissionStatus getMarketingStatus();

  default ContactPermissions permissions() {
    return new ContactPermissions(getBusinessStatus(), getMarketingStatus());
  }
}
