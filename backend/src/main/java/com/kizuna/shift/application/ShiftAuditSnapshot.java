package com.kizuna.shift.application;

import com.kizuna.shift.domain.Attendance;
import com.kizuna.shift.domain.Shift;
import com.kizuna.shift.domain.ShiftRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

record ShiftAuditSnapshot(Map<String, String> values, Map<String, String> privateValues) {
  static ShiftAuditSnapshot of(Shift shift) {
    var values = new HashMap<String, String>();
    put(values, "exists", true);
    put(values, "version", shift.getVersion());
    put(values, "cast_id", shift.getCastId());
    put(values, "work_date", shift.getWorkDate());
    put(values, "start_time", shift.getStartTime());
    put(values, "end_time", shift.getEndTime());
    put(values, "status", shift.getStatus());
    put(values, "published", shift.isPublished());
    return new ShiftAuditSnapshot(Map.copyOf(values), Map.of());
  }

  static ShiftAuditSnapshot of(ShiftRequest request) {
    var values = new HashMap<String, String>();
    put(values, "exists", true);
    put(values, "version", request.getVersion());
    put(values, "cast_id", request.getCastId());
    put(values, "shift_id", request.getShiftId());
    put(values, "type", request.getType());
    put(values, "status", request.getStatus());
    put(values, "work_date", request.getWorkDate());
    put(values, "start_time", request.getStartTime());
    put(values, "end_time", request.getEndTime());
    put(values, "original_work_date", request.getOriginalWorkDate());
    put(values, "original_start_time", request.getOriginalStartTime());
    put(values, "original_end_time", request.getOriginalEndTime());
    put(values, "processed_by", request.getProcessedBy());
    put(values, "processed_at", request.getProcessedAt());
    return new ShiftAuditSnapshot(
        Map.copyOf(values),
        request.getNote() == null ? Map.of() : Map.of("note", request.getNote()));
  }

  static ShiftAuditSnapshot of(Attendance attendance) {
    var values = new HashMap<String, String>();
    put(values, "exists", true);
    put(values, "version", attendance.getVersion());
    put(values, "cast_id", attendance.getCastId());
    put(values, "shift_id", attendance.getShiftId());
    put(values, "business_date", attendance.getBusinessDate());
    put(values, "actual_start_at", attendance.getActualStartAt());
    put(values, "actual_end_at", attendance.getActualEndAt());
    put(values, "cancelled", attendance.isCancelled());
    put(values, "cancelled_at", attendance.getCancelledAt());
    put(values, "cancelled_by", attendance.getCancelledBy());
    var privateValues = new HashMap<String, String>();
    if (attendance.getWaitingPlace() != null)
      privateValues.put("waiting_place", attendance.getWaitingPlace());
    if (attendance.getCancelledReason() != null)
      privateValues.put("cancelled_reason", attendance.getCancelledReason());
    return new ShiftAuditSnapshot(Map.copyOf(values), Map.copyOf(privateValues));
  }

  Map<String, String> after(ShiftAuditSnapshot before) {
    var previous = before == null ? Map.<String, String>of() : before.privateValues;
    var changed = new TreeSet<>(privateValues.keySet());
    changed.addAll(previous.keySet());
    changed.removeIf(key -> Objects.equals(privateValues.get(key), previous.get(key)));
    var after = new HashMap<>(values);
    if (!changed.isEmpty()) after.put("redacted_fields_changed", String.join(",", changed));
    return Map.copyOf(after);
  }

  private static void put(Map<String, String> values, String key, Object value) {
    values.put(key, Objects.toString(value, ""));
  }
}
