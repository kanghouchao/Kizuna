package com.kizuna.order.api.store;

import com.kizuna.order.api.dto.MonthlyRemunerationCastSummary;
import com.kizuna.order.api.dto.MonthlyRemunerationResponse;
import com.kizuna.order.application.MonthlyRemunerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store/monthly-remunerations")
@RequiredArgsConstructor
public class MonthlyRemunerationController {
  private final MonthlyRemunerationService service;

  @GetMapping("/casts")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public Page<MonthlyRemunerationCastSummary> casts(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.casts(search, page, size);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public MonthlyRemunerationResponse monthly(
      @RequestParam(name = "person_id") Long personId,
      @RequestParam String month,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.monthly(personId, month, page, size);
  }
}
