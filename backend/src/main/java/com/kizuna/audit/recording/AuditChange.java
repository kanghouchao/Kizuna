package com.kizuna.audit.recording;

import java.util.Map;
import java.util.Set;

public record AuditChange(
    AuditActor actor,
    Long storeId,
    String action,
    String targetType,
    String targetId,
    String sourceType,
    String sourceId,
    Map<String, String> beforeValues,
    Map<String, String> afterValues,
    String result) {
  public AuditChange(
      AuditActor actor,
      Long storeId,
      String action,
      String targetType,
      String targetId,
      String sourceType,
      String sourceId,
      Map<String, String> beforeValues,
      Map<String, String> afterValues) {
    this(
        actor,
        storeId,
        action,
        targetType,
        targetId,
        sourceType,
        sourceId,
        beforeValues,
        afterValues,
        "SUCCEEDED");
  }

  public AuditChange {
    if (!Set.of("SUCCEEDED", "FAILED", "RUNNING", "INTERRUPTED").contains(result)
        || actor == null
        || !valid(action, 80)
        || !valid(targetType, 80)
        || !valid(targetId, 100)
        || (storeId != null && storeId <= 0)
        || (sourceType != null && !valid(sourceType, 80))
        || (sourceId != null && !valid(sourceId, 100))
        || (sourceType == null) != (sourceId == null)) {
      throw new IllegalArgumentException("監査対象の指定が不正です");
    }
    beforeValues = checked(beforeValues);
    afterValues = checked(afterValues);
  }

  private static boolean valid(String value, int max) {
    return value != null && !value.isBlank() && value.length() <= max && value.indexOf('\0') < 0;
  }

  private static Map<String, String> checked(Map<String, String> values) {
    if (values == null
        || values.size() > 32
        || values.entrySet().stream()
            .anyMatch(
                e ->
                    !valid(e.getKey(), 80)
                        || e.getValue() == null
                        || e.getValue().indexOf('\0') >= 0)) {
      throw new IllegalArgumentException("監査摘要の指定が不正です");
    }
    return Map.copyOf(values);
  }
}
