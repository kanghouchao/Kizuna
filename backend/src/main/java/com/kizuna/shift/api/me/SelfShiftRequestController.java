package com.kizuna.shift.api.me;

import com.kizuna.shift.api.dto.CastShiftRequestResponse;
import com.kizuna.shift.api.dto.ShiftChangeRequestCreateRequest;
import com.kizuna.shift.api.dto.ShiftRequestCreateRequest;
import com.kizuna.shift.api.dto.ShiftRequestResponse;
import com.kizuna.shift.application.CastShiftRequestService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本人（キャスト）ポータルの出勤希望 API（提出・履歴）。 */
@RestController
@RequestMapping("/me/shift-requests")
@RequiredArgsConstructor
public class SelfShiftRequestController {

  private final CastShiftRequestService castShiftRequestService;

  @PostMapping
  @PreAuthorize("hasRole('CAST')")
  public ResponseEntity<ShiftRequestResponse> submit(
      Principal principal, @Valid @RequestBody ShiftRequestCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(castShiftRequestService.submit(principal.getName(), request));
  }

  /** 確定シフトへの変更申請を提出する（type=CHANGE の出勤希望として同一正本に載る）。 */
  @PostMapping("/changes")
  @PreAuthorize("hasRole('CAST')")
  public ResponseEntity<ShiftRequestResponse> submitChange(
      Principal principal, @Valid @RequestBody ShiftChangeRequestCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(castShiftRequestService.submitChange(principal.getName(), request));
  }

  @GetMapping
  @PreAuthorize("hasRole('CAST')")
  public ResponseEntity<Page<CastShiftRequestResponse>> history(
      Principal principal,
      @PageableDefault(
              size = 20,
              sort = {"createdAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(castShiftRequestService.history(principal.getName(), pageable));
  }
}
