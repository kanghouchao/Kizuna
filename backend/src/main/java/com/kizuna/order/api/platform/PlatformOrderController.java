package com.kizuna.order.api.platform;

import com.kizuna.order.api.dto.PlatformOrderResponse;
import com.kizuna.order.application.OrderCorrectionHistory;
import com.kizuna.order.application.PlatformOrderService;
import com.kizuna.order.result.OrderCorrectionResult;
import com.kizuna.shared.web.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 平台（統一）受注 API。授権店舗集合での横断一覧を提供する（集合作用域）。 */
@RestController
@RequestMapping("/platform/orders")
@RequiredArgsConstructor
public class PlatformOrderController {

  private final OrderCorrectionHistory correctionHistory;

  private final PlatformOrderService platformOrderService;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_SET_MANAGE')")
  public ResponseEntity<Page<PlatformOrderResponse>> list(
      @PageableDefault(sort = "businessDate", direction = Sort.Direction.DESC) Pageable pageable) {
    return ResponseEntity.ok(platformOrderService.list(pageable));
  }

  @GetMapping("/{id}/corrections")
  @PreAuthorize("hasAuthority('PERM_ORDER_SET_MANAGE')")
  public CursorPage<OrderCorrectionResult> corrections(
      @PathVariable String id,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return correctionHistory.platform(id, cursor, size);
  }
}
