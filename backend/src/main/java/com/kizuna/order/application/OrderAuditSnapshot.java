package com.kizuna.order.application;

import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderApplication;
import com.kizuna.order.domain.OrderFeeLine;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 個人情報と自由記述の値は変更項目の比較にだけ使い、監査へ渡す値から除く。 */
record OrderAuditSnapshot(Map<String, String> values, Map<String, Object> redacted) {
  OrderAuditSnapshot {
    values = Map.copyOf(values);
    redacted = Collections.unmodifiableMap(new LinkedHashMap<>(redacted));
  }

  static OrderAuditSnapshot of(Order order) {
    var values = new LinkedHashMap<String, String>();
    put(values, "version", order.getVersion());
    put(values, "status", order.getStatus());
    put(values, "customer_id", order.getCustomerId());
    put(values, "cast_enrollment_id", order.getCastId());
    put(values, "receptionist_id", order.getReceptionistId());
    put(values, "requester_member_id", order.getRequesterMemberId());
    put(values, "business_date", order.getBusinessDate());
    put(values, "scheduled_start", order.getArrivalScheduledStartTime());
    put(values, "scheduled_end", order.getArrivalScheduledEndTime());
    put(values, "actual_start", order.getActualArrivalTime());
    put(values, "actual_end", order.getActualEndTime());
    put(values, "pax", order.getPax());
    put(values, "reception_route", order.getReceptionRoute());
    put(
        values,
        "course_revision_id",
        order.getCourse() == null ? null : order.getCourse().revisionId());
    put(values, "total_fee", order.getTotalFee());
    put(values, "total_remuneration", order.getTotalRemuneration());
    put(values, "accrued_remuneration", order.getAccruedRemuneration());
    put(values, "auto_grant_points", order.getAutoGrantPoints());
    put(values, "duration_minutes", order.getTotalDurationMinutes());
    put(values, "started_at", order.getStartedAt());
    put(values, "completed_at", order.getCompletedAt());
    put(values, "cancelled_at", order.getCancelledAt());
    put(values, "completion_invalidated", order.isCompletionInvalidated());
    put(values, "replacement_for_order_id", order.getReplacementForOrderId());
    put(
        values,
        "fee_lines",
        order.getFeeLines().stream()
            .map(OrderAuditSnapshot::line)
            .sorted()
            .collect(Collectors.joining("\n")));
    var redacted = new LinkedHashMap<String, Object>();
    redacted.put("contact", order.getContactSnapshot());
    redacted.put("carrier", order.getCarrier());
    redacted.put("media_name", order.getMediaName());
    redacted.put("survey_status", order.getSurveyStatus());
    redacted.put("location_address", order.getLocationAddress());
    redacted.put("location_building", order.getLocationBuilding());
    redacted.put("remarks", order.getRemarks());
    redacted.put("cast_driver_message", order.getCastDriverMessage());
    redacted.put("requester_declared_name", order.getRequesterDeclaredName());
    redacted.put("start_reason", order.getStartReason());
    redacted.put("cancelled_reason", order.getCancelledReason());
    redacted.put(
        "fee_line_names",
        order.getFeeLines().stream()
            .map(line -> Map.entry(text(line.getId()) + ":" + line.getKind(), line.getName()))
            .sorted(
                Map.Entry.<String, String>comparingByKey()
                    .thenComparing(Map.Entry.comparingByValue()))
            .toList());
    return new OrderAuditSnapshot(values, redacted);
  }

  Map<String, String> after(OrderAuditSnapshot before) {
    var after = new LinkedHashMap<>(values);
    if (before != null) {
      String changed =
          redacted.keySet().stream()
              .filter(key -> !Objects.equals(before.redacted.get(key), redacted.get(key)))
              .sorted()
              .collect(Collectors.joining(","));
      if (!changed.isEmpty()) after.put("redacted_fields_changed", changed);
    }
    return after;
  }

  static Map<String, String> application(OrderApplication application) {
    var values = new LinkedHashMap<String, String>();
    put(values, "version", application.getVersion());
    put(values, "status", application.getStatus());
    put(values, "business_date", application.getBusinessDate());
    put(values, "scheduled_start", application.getArrivalScheduledStartTime());
    put(values, "pax", application.getPax());
    put(values, "cast_enrollment_id", application.getCastId());
    put(values, "requester_member_id", application.getRequesterMemberId());
    put(values, "order_id", application.getOrderId());
    put(values, "processed_by", application.getProcessedBy());
    put(values, "processed_at", application.getProcessedAt());
    put(values, "contact_import_count", application.getContactImports().size());
    return Map.copyOf(values);
  }

  private static String line(OrderFeeLine line) {
    return "id="
        + text(line.getId())
        + ",kind="
        + line.getKind()
        + ",amount="
        + line.getAmount()
        + ",minutes="
        + text(line.getDurationMinutes())
        + ",remuneration="
        + line.getRemuneration()
        + ",revision_id="
        + text(line.getAdoption() == null ? null : line.getAdoption().revisionId());
  }

  private static void put(Map<String, String> values, String key, Object value) {
    values.put(key, text(value));
  }

  private static String text(Object value) {
    return value == null ? "" : value.toString();
  }
}
