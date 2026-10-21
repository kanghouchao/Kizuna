package com.kizuna.order.api.admin;

import com.kizuna.order.api.dto.AdminMonthlyRemunerationResponse;
import com.kizuna.order.api.dto.AdminMonthlyRemunerationStoreSummary;
import com.kizuna.order.api.dto.MonthlyRemunerationCastSummary;
import com.kizuna.order.application.AdminMonthlyRemunerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/monthly-remunerations")
@RequiredArgsConstructor
public class AdminMonthlyRemunerationController {
  private final AdminMonthlyRemunerationService service;

  @GetMapping("/stores")
  @PreAuthorize("hasAuthority('PERM_ORDER_SET_MANAGE')")
  public Page<AdminMonthlyRemunerationStoreSummary> stores(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.stores(search, page, size);
  }

  @GetMapping("/casts")
  @PreAuthorize("hasAuthority('PERM_ORDER_SET_MANAGE')")
  public Page<MonthlyRemunerationCastSummary> casts(
      @RequestParam(name = "store_id") Long storeId,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.casts(storeId, search, page, size);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_SET_MANAGE')")
  public AdminMonthlyRemunerationResponse monthly(
      @RequestParam(name = "store_id") Long storeId,
      @RequestParam(name = "person_id") Long personId,
      @RequestParam String month,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.monthly(storeId, personId, month, page, size);
  }
}
