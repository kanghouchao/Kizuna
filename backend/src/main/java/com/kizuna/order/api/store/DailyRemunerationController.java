package com.kizuna.order.api.store;

import com.kizuna.order.api.dto.DailyRemunerationResponse;
import com.kizuna.order.application.DailyRemunerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store/daily-remunerations")
@RequiredArgsConstructor
public class DailyRemunerationController {
  private final DailyRemunerationService service;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public DailyRemunerationResponse daily(
      @RequestParam(name = "person_id") Long personId,
      @RequestParam(name = "business_date") String businessDate,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.store(personId, businessDate, page, size);
  }
}
