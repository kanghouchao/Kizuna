package com.kizuna.order.api;

import com.kizuna.order.api.dto.AdminOrderResponse;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.application.AdminOrderService;
import com.kizuna.shared.storescope.StoreScope;
import com.kizuna.shared.storescope.StoreScopeHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** グループ級受注 API。授権店舗集合での横断一覧を提供する（集合作用域）。 */
@RestController
@RequestMapping("/orders/cross-store")
@RequiredArgsConstructor
public class CrossStoreOrderController {

  private final AdminOrderService adminOrderService;
  private final StoreScopeHolder storeScopeHolder;
  private final OrderMapper orderMapper;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_SET_VIEW')")
  public ResponseEntity<Page<AdminOrderResponse>> list(
      @PageableDefault(sort = "businessDate", direction = Sort.Direction.DESC) Pageable pageable) {
    StoreScope scope = storeScopeHolder.current();
    if (scope == null) throw new AccessDeniedException("店舗集合スコープを解決できません");
    return ResponseEntity.ok(
        adminOrderService.list(scope, pageable).map(orderMapper::toAdminResponse));
  }
}
