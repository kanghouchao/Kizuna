package com.kizuna.order.api.admin;

import com.kizuna.order.api.dto.AdminOrderResponse;
import com.kizuna.order.application.AdminOrderService;
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

/** グループ級受注 API。授権店舗集合での横断一覧を提供する（集合作用域）。 */
@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

  private final AdminOrderService adminOrderService;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_SET_MANAGE')")
  public ResponseEntity<Page<AdminOrderResponse>> list(
      @PageableDefault(sort = "businessDate", direction = Sort.Direction.DESC) Pageable pageable) {
    return ResponseEntity.ok(adminOrderService.list(pageable));
  }
}
