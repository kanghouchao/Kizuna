package com.kizuna.order.api;

import com.kizuna.order.api.dto.MonthlyRemunerationCastSummary;
import com.kizuna.order.api.dto.MonthlyRemunerationResponse;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.application.MonthlyRemunerationService;
import com.kizuna.shared.storescope.CurrentStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 現在店舗の月次報酬照会。 */
@RestController
@RequestMapping("/monthly-remunerations")
@RequiredArgsConstructor
public class MonthlyRemunerationController {
  private final MonthlyRemunerationService service;
  private final OrderMapper mapper;

  @GetMapping("/casts")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public Page<MonthlyRemunerationCastSummary> casts(
      @CurrentStore Long storeId,
      @RequestParam(required = false) String search,
      @PageableDefault(
              size = 20,
              sort = {})
          Pageable pageable) {
    return service.casts(storeId, search, pageable).map(mapper::toCastSummary);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public MonthlyRemunerationResponse monthly(
      @CurrentStore Long storeId,
      @RequestParam(name = "person_id") Long personId,
      @RequestParam String month,
      @PageableDefault(
              size = 20,
              sort = {})
          Pageable pageable) {
    return mapper.toResponse(service.monthly(storeId, personId, month, pageable));
  }
}
