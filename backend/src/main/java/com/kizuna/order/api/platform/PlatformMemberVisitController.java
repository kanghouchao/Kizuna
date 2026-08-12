package com.kizuna.order.api.platform;

import com.kizuna.order.api.dto.MemberVisitResponse;
import com.kizuna.order.application.MemberVisitService;
import com.kizuna.shared.web.CursorPage;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人（会員）ポータルの来店履歴 API。店舗文脈を要さない経路で、隔離は本人の一致による（{@code MemberVisitService}）。
 *
 * <p>予約の申請一覧（{@code /platform/me/orders}）とは別の読み口である。あちらは申請の追跡で、こちらは確定した来店の記録（ADR 0009）。
 */
@RestController
@RequestMapping("/platform/me/visits")
@RequiredArgsConstructor
public class PlatformMemberVisitController {

  private final MemberVisitService memberVisitService;

  /** 本人の来店履歴。続きは応答の {@code next_cursor} をそのまま {@code cursor} に渡して取る。 */
  @GetMapping
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<CursorPage<MemberVisitResponse>> list(
      Principal principal,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(memberVisitService.list(principal.getName(), cursor, size));
  }
}
