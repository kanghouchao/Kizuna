package com.kizuna.order.api.me;

import com.kizuna.order.api.dto.MemberOrderApplicationCreateRequest;
import com.kizuna.order.api.dto.MemberOrderApplicationResponse;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.application.MemberOrderApplicationService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

  /** 本人の予約申請一覧。新しい希望日から。 */
  @GetMapping
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<Page<MemberOrderApplicationResponse>> list(
      Principal principal,
      @PageableDefault(
              size = 20,
              sort = {"businessDate", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(
        memberOrderApplicationService
            .list(principal.getName(), pageable)
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
