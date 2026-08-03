package com.kizuna.order.api.platform;

import com.kizuna.order.api.dto.MemberOrderCreateRequest;
import com.kizuna.order.api.dto.MemberOrderResponse;
import com.kizuna.order.application.MemberOrderService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

  @GetMapping
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<Page<MemberOrderResponse>> list(
      Principal principal, @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(memberOrderService.list(principal.getName(), pageable));
  }

  @PostMapping("/{id}/cancellation")
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<MemberOrderResponse> cancel(Principal principal, @PathVariable String id) {
    return ResponseEntity.ok(memberOrderService.cancel(principal.getName(), id));
  }
}
