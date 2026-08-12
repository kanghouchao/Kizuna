package com.kizuna.point.api.platform;

import com.kizuna.point.api.dto.MemberPointBalanceResponse;
import com.kizuna.point.api.dto.MemberPointEntryResponse;
import com.kizuna.point.application.MemberPointService;
import com.kizuna.shared.web.CursorPage;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 本人（会員）ポータルのポイント API。店舗文脈を要さない経路で、隔離は本人の一致による（{@code MemberPointService}）。 */
@RestController
@RequestMapping("/platform/me/points")
@RequiredArgsConstructor
public class PlatformMemberPointController {

  private final MemberPointService memberPointService;

  @GetMapping("/balance")
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<MemberPointBalanceResponse> balance(Principal principal) {
    return ResponseEntity.ok(memberPointService.balance(principal.getName()));
  }

  /** 本人のポイント明細。続きは応答の {@code next_cursor} をそのまま {@code cursor} に渡して取る。 */
  @GetMapping("/entries")
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<CursorPage<MemberPointEntryResponse>> entries(
      Principal principal,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(memberPointService.entries(principal.getName(), cursor, size));
  }
}
