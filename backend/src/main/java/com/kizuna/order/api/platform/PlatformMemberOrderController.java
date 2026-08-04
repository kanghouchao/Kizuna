package com.kizuna.order.api.platform;

import com.kizuna.order.api.dto.MemberOrderCreateRequest;
import com.kizuna.order.api.dto.MemberOrderResponse;
import com.kizuna.order.application.MemberOrderService;
import com.kizuna.shared.web.CursorPage;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 本人（会員）ポータルの予約 API。店舗文脈を要さない経路で、隔離は申請者本人の一致による（{@code MemberOrderService}）。 */
@RestController
@RequestMapping("/platform/me/orders")
@RequiredArgsConstructor
public class PlatformMemberOrderController {

  private final MemberOrderService memberOrderService;

  @PostMapping
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<MemberOrderResponse> request(
      Principal principal, @Valid @RequestBody MemberOrderCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(memberOrderService.request(principal.getName(), request));
  }

  /** 本人の予約一覧。続きは応答の {@code next_cursor} をそのまま {@code cursor} に渡して取る。 */
  @GetMapping
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<CursorPage<MemberOrderResponse>> list(
      Principal principal,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(memberOrderService.list(principal.getName(), cursor, size));
  }

  @PostMapping("/{id}/cancellation")
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<MemberOrderResponse> cancel(Principal principal, @PathVariable String id) {
    return ResponseEntity.ok(memberOrderService.cancel(principal.getName(), id));
  }
}
