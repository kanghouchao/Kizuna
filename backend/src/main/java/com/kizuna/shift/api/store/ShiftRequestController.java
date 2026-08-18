package com.kizuna.shift.api.store;

import com.kizuna.shift.api.dto.ShiftApprovalRequest;
import com.kizuna.shift.api.dto.StoreShiftRequestResponse;
import com.kizuna.shift.application.ShiftRequestService;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 店舗側の出勤希望 API（inbox 閲覧・承認・却下）。 */
@RestController
@RequestMapping("/store/shift-requests")
@RequiredArgsConstructor
public class ShiftRequestController {

  private final ShiftRequestService shiftRequestService;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<List<StoreShiftRequestResponse>> list(
      @RequestParam(required = false) String status) {
    return ResponseEntity.ok(shiftRequestService.list(status));
  }

  /** 承認。本体は任意で、新規希望を非公開のまま出生させたいときだけ公開可否を明示する。 */
  @PostMapping("/{id}/approval")
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<StoreShiftRequestResponse> approve(
      @PathVariable String id,
      @RequestBody(required = false) ShiftApprovalRequest request,
      Principal principal) {
    Boolean published = request == null ? null : request.getPublished();
    return ResponseEntity.ok(shiftRequestService.approve(id, published, principal.getName()));
  }

  @PostMapping("/{id}/rejection")
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<StoreShiftRequestResponse> decline(
      @PathVariable String id, Principal principal) {
    return ResponseEntity.ok(shiftRequestService.decline(id, principal.getName()));
  }
}
