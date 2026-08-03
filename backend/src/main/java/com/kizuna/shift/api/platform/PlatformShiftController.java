package com.kizuna.shift.api.platform;

import com.kizuna.shift.api.dto.PublicShiftResponse;
import com.kizuna.shift.application.ConfirmedShiftLookupService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会員が予約申請でキャストを指名するための出勤参照 API。
 *
 * <p>公開の出勤表（{@code /store/shifts/public}）は当日のみで日付を持たないため、日付を指定して引ける本 API は会員に限って開く。
 */
@RestController
@RequestMapping("/platform/shifts")
@RequiredArgsConstructor
public class PlatformShiftController {

  private final ConfirmedShiftLookupService confirmedShiftLookupService;

  @GetMapping("/casts")
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<List<PublicShiftResponse>> confirmedCasts(
      @RequestParam(name = "store_id") Long storeId,
      @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return ResponseEntity.ok(confirmedShiftLookupService.listConfirmedCasts(storeId, date));
  }
}
