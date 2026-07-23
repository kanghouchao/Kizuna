package com.kizuna.shift.api.platform;

import com.kizuna.shift.api.dto.CastScheduleResponse;
import com.kizuna.shift.application.CastScheduleService;
import java.security.Principal;
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

/** 本人（キャスト）ポータルの週間スケジュール API。cast_id 単層自限（本人所属店を跨いで集約する）。 */
@RestController
@RequestMapping("/platform/me/schedule")
@RequiredArgsConstructor
public class PlatformScheduleController {

  private final CastScheduleService castScheduleService;

  @GetMapping
  @PreAuthorize("hasRole('CAST')")
  public ResponseEntity<List<CastScheduleResponse>> myWeek(
      Principal principal,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(castScheduleService.myWeek(principal.getName(), from, to));
  }
}
