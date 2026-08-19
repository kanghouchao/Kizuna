package com.kizuna.shift.api.store;

import com.kizuna.shift.api.dto.AbsenceResponse;
import com.kizuna.shift.application.AbsenceService;
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
 * 欠勤の読み口。導出であって記録ではないので、読み取り以外の口は持たない（ADR 0014 — 欠勤は行を建てない）。
 *
 * <p>裸の {@code List} で返せるのは、1 店舗・1 営業日に確定シフトを持つキャストの数が上限だからである。
 */
@RestController
@RequestMapping("/store/absences")
@RequiredArgsConstructor
public class AbsenceController {

  private final AbsenceService absenceService;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_SHIFT_MANAGE')")
  public ResponseEntity<List<AbsenceResponse>> list(
      @RequestParam(name = "business_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate businessDate) {
    return ResponseEntity.ok(absenceService.list(businessDate));
  }
}
