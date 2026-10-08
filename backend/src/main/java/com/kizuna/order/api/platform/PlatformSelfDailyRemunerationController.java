package com.kizuna.order.api.platform;

import com.kizuna.order.api.dto.SelfDailyRemunerationResponse;
import com.kizuna.order.application.DailyRemunerationService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/me/daily-remunerations")
@RequiredArgsConstructor
public class PlatformSelfDailyRemunerationController {
  private final DailyRemunerationService service;

  @GetMapping
  @PreAuthorize("hasRole('CAST')")
  public SelfDailyRemunerationResponse daily(
      Principal principal,
      @RequestParam(name = "store_id") Long storeId,
      @RequestParam(name = "business_date") String businessDate,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.self(principal.getName(), storeId, businessDate, page, size);
  }
}
