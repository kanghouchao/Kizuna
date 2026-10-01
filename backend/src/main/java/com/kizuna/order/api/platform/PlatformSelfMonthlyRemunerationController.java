package com.kizuna.order.api.platform;

import com.kizuna.order.api.dto.SelfMonthlyRemunerationResponse;
import com.kizuna.order.api.dto.SelfMonthlyRemunerationStoreSummary;
import com.kizuna.order.application.SelfMonthlyRemunerationService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/me/monthly-remunerations")
@RequiredArgsConstructor
public class PlatformSelfMonthlyRemunerationController {
  private final SelfMonthlyRemunerationService service;

  @GetMapping("/stores")
  @PreAuthorize("hasRole('CAST')")
  public Page<SelfMonthlyRemunerationStoreSummary> stores(
      Principal principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.stores(principal.getName(), page, size);
  }

  @GetMapping
  @PreAuthorize("hasRole('CAST')")
  public SelfMonthlyRemunerationResponse monthly(
      Principal principal,
      @RequestParam(name = "store_id") Long storeId,
      @RequestParam String month,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.monthly(principal.getName(), storeId, month, page, size);
  }
}
