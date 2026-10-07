package com.kizuna.customer.application;

import com.kizuna.customer.domain.Customer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

record CustomerAuditSnapshot(Map<String, String> values, Map<String, Object> privateValues) {
  static CustomerAuditSnapshot of(Customer customer) {
    var fields = new HashMap<String, Object>();
    fields.put("name", customer.getName());
    fields.put("address", customer.getAddress());
    fields.put("building_name", customer.getBuildingName());
    fields.put("landmark", customer.getLandmark());
    fields.put("classification", customer.getClassification());
    fields.put("has_pet", customer.getHasPet());
    fields.put("usage_areas", customer.getUsageAreas());
    fields.put("ng_type", customer.getNgType());
    fields.put("ng_content", customer.getNgContent());
    fields.values().removeIf(Objects::isNull);
    return new CustomerAuditSnapshot(
        Map.of("exists", "true", "version", Objects.toString(customer.getVersion(), "")),
        Map.copyOf(fields));
  }

  Map<String, String> after(CustomerAuditSnapshot before) {
    var previous = before == null ? Map.<String, Object>of() : before.privateValues;
    var changed = new TreeSet<>(privateValues.keySet());
    changed.addAll(previous.keySet());
    changed.removeIf(key -> Objects.equals(privateValues.get(key), previous.get(key)));
    var after = new HashMap<>(values);
    if (!changed.isEmpty()) after.put("redacted_fields_changed", String.join(",", changed));
    return Map.copyOf(after);
  }
}
