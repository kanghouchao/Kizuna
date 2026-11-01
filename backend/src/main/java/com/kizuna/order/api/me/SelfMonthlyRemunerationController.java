package com.kizuna.order.api.me;

import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.SelfMonthlyRemunerationResponse;
import com.kizuna.order.api.dto.SelfMonthlyRemunerationStoreSummary;
import com.kizuna.order.application.SelfMonthlyRemunerationService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/monthly-remunerations")
@RequiredArgsConstructor
public class SelfMonthlyRemunerationController {
  private final SelfMonthlyRemunerationService service;
  private final OrderMapper mapper;

  @GetMapping("/stores")
  @PreAuthorize("hasRole('CAST')")
  public Page<SelfMonthlyRemunerationStoreSummary> stores(
      Principal principal,
      @PageableDefault(
              size = 20,
              sort = {})
          Pageable pageable) {
    return service.stores(principal.getName(), pageable).map(mapper::toStoreSummary);
  }

  @GetMapping
  @PreAuthorize("hasRole('CAST')")
  public SelfMonthlyRemunerationResponse monthly(
      Principal principal,
      @RequestParam(name = "store_id") Long storeId,
      @RequestParam String month,
      @PageableDefault(
              size = 20,
              sort = {})
          Pageable pageable) {
    return mapper.toResponse(service.monthly(principal.getName(), storeId, month, pageable));
  }
}
