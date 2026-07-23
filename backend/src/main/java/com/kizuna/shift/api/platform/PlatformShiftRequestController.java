package com.kizuna.shift.api.platform;

import com.kizuna.shift.api.dto.CastShiftRequestResponse;
import com.kizuna.shift.api.dto.ShiftRequestCreateRequest;
import com.kizuna.shift.api.dto.ShiftRequestResponse;
import com.kizuna.shift.application.CastShiftRequestService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/platform/me/shift-requests")
@RequiredArgsConstructor
public class PlatformShiftRequestController {

  private final CastShiftRequestService castShiftRequestService;

  @PostMapping
  @PreAuthorize("hasRole('CAST')")
  public ResponseEntity<ShiftRequestResponse> submit(
      Principal principal, @Valid @RequestBody ShiftRequestCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(castShiftRequestService.submit(principal.getName(), request));
  }

  @GetMapping
  @PreAuthorize("hasRole('CAST')")
  public ResponseEntity<List<CastShiftRequestResponse>> history(Principal principal) {
    return ResponseEntity.ok(castShiftRequestService.history(principal.getName()));
  }
}
