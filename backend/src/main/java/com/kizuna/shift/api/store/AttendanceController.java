package com.kizuna.shift.api.store;

import com.kizuna.shift.api.dto.AttendanceCancellationRequest;
import com.kizuna.shift.api.dto.AttendanceCorrectionRequest;
import com.kizuna.shift.api.dto.AttendanceCreateRequest;
import com.kizuna.shift.api.dto.AttendanceResponse;
import com.kizuna.shift.application.AttendanceService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当日実績の操作面。物理削除の口は意図的に存在しない（法定保存 — ADR 0014）ので、{@code DELETE} を配線しないこと自体が
 * この端点群の不変量である。誤建行は取消（{@code /cancellation}）で導出・照会から外す。
 */
@RestController
@RequestMapping("/store/attendances")
@RequiredArgsConstructor
public class AttendanceController {

  private final AttendanceService attendanceService;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<List<AttendanceResponse>> list(
      @RequestParam(name = "business_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate businessDate,
      @RequestParam(name = "cast_id", required = false) String castId) {
    return ResponseEntity.ok(attendanceService.list(businessDate, castId));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<AttendanceResponse> record(
      @Valid @RequestBody AttendanceCreateRequest request, Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(attendanceService.record(request, principal.getName()));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<AttendanceResponse> correct(
      @PathVariable String id,
      @Valid @RequestBody AttendanceCorrectionRequest request,
      Principal principal) {
    return ResponseEntity.ok(attendanceService.correct(id, request, principal.getName()));
  }

  @PostMapping("/{id}/cancellation")
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<Void> cancel(
      @PathVariable String id,
      @Valid @RequestBody AttendanceCancellationRequest request,
      Principal principal) {
    attendanceService.cancel(id, request, principal.getName());
    return ResponseEntity.noContent().build();
  }
}
