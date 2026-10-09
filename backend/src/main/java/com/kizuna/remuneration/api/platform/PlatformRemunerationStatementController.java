package com.kizuna.remuneration.api.platform;

import com.kizuna.remuneration.api.dto.RemunerationStatementResponse;
import com.kizuna.remuneration.application.RemunerationStatementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/remuneration-statements")
@RequiredArgsConstructor
public class PlatformRemunerationStatementController {
  private final RemunerationStatementService service;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_SET_MANAGE') and hasAuthority('PERM_REMUNERATION_VIEW')")
  public RemunerationStatementResponse statement(
      @RequestParam(name = "store_id") Long storeId,
      @RequestParam(name = "person_id") Long personId,
      @RequestParam String month,
      @RequestParam(name = "order_page", defaultValue = "0") int orderPage,
      @RequestParam(name = "bonus_page", defaultValue = "0") int bonusPage,
      @RequestParam(defaultValue = "20") int size) {
    return service.platform(storeId, personId, month, orderPage, bonusPage, size);
  }
}
