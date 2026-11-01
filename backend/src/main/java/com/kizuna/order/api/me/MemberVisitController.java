package com.kizuna.order.api.me;

import com.kizuna.order.api.dto.MemberVisitResponse;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.application.MemberVisitService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本人（会員）ポータルの来店履歴 API。店舗文脈を要さない経路で、隔離は本人の一致による（{@code MemberVisitService}）。
 *
 * <p>予約の申請一覧（{@code /me/orders}）とは別の読み口である。あちらは申請の追跡で、こちらは確定した来店の記録（ADR 0009）。
 */
@RestController
@RequestMapping("/me/visits")
@RequiredArgsConstructor
public class MemberVisitController {

  private final MemberVisitService memberVisitService;
  private final OrderMapper orderMapper;

  /** 本人の来店履歴。新しい帰属から。 */
  @GetMapping
  @PreAuthorize("hasRole('MEMBER')")
  public ResponseEntity<Page<MemberVisitResponse>> list(
      Principal principal,
      @PageableDefault(
              size = 20,
              sort = {"createdAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(
        memberVisitService.list(principal.getName(), pageable).map(orderMapper::toResponse));
  }
}
