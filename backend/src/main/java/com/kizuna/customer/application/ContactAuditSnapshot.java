package com.kizuna.customer.application;

import com.kizuna.customer.domain.CustomerContact;
import com.kizuna.customer.domain.CustomerContactHistory;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

record ContactAuditSnapshot(Map<String, String> values, String privateValue) {
  static ContactAuditSnapshot of(CustomerContact contact) {
    return new ContactAuditSnapshot(
        Map.of(
            "version", Objects.toString(contact.getVersion(), ""),
            "customer_id", contact.getCustomerId(),
            "origin_customer_id", contact.getOriginCustomerId(),
            "type", contact.getType().name(),
            "preferred", String.valueOf(contact.isPreferred()),
            "deleted", String.valueOf(contact.isDeleted()),
            "business_status", contact.getBusinessStatus().name(),
            "marketing_status", contact.getMarketingStatus().name()),
        contact.getValue());
  }

  Map<String, String> after(ContactAuditSnapshot before, CustomerContactHistory history) {
    var after = new HashMap<>(values);
    after.put("operation_id", history.getOperationId());
    if (history.getSourceContactId() != null)
      after.put("source_contact_id", history.getSourceContactId());
    if (history.getPurpose() != null) after.put("purpose", history.getPurpose().name());
    if (before == null || !Objects.equals(before.privateValue, privateValue))
      after.put("redacted_fields_changed", "value");
    return Map.copyOf(after);
  }
}
