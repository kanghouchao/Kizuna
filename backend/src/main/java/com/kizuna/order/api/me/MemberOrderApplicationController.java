package com.kizuna.order.api.me;

import com.kizuna.order.api.dto.MemberOrderApplicationCreateRequest;
import com.kizuna.order.api.dto.MemberOrderApplicationResponse;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.application.MemberOrderApplicationService;
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

/** 本人（会員）ポータルの予約申請 API。店舗文脈を要さない経路で、隔離は申請者本人の一致による（{@code MemberOrderApplicationService}）。 */
@RestController
@RequestMapping("/me/order-applications")
@RequiredArgsConstructor
public class MemberOrderApplicationController {

  private final MemberOrderApplicationService memberOrderApplicationService;
  private final OrderMapper orderMapper;

  @PostMapping
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<MemberOrderApplicationResponse> request(
      Principal principal, @Valid @RequestBody MemberOrderApplicationCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            orderMapper.toResponse(
                memberOrderApplicationService.request(
                    principal.getName(), orderMapper.toCommand(request))));
  }

  /** 本人の予約申請一覧。続きは応答の {@code next_cursor} をそのまま {@code cursor} に渡して取る。 */
  @GetMapping
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<CursorPage<MemberOrderApplicationResponse>> list(
      Principal principal,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(
        memberOrderApplicationService
            .list(principal.getName(), cursor, size)
            .map(orderMapper::toResponse));
  }

  /** 未処理の申請を取り下げる（WITHDRAWN）。確定・謝絶の後は 400 で撥ねられる。 */
  @PostMapping("/{id}/withdrawal")
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<MemberOrderApplicationResponse> withdraw(
      Principal principal, @PathVariable String id) {
    return ResponseEntity.ok(
        orderMapper.toResponse(memberOrderApplicationService.withdraw(principal.getName(), id)));
  }
}
