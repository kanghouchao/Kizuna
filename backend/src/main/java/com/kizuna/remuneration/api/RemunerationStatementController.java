package com.kizuna.remuneration.api;

import com.kizuna.remuneration.api.dto.RemunerationStatementResponse;
import com.kizuna.remuneration.application.RemunerationStatementService;
import com.kizuna.shared.storescope.CurrentStore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/remuneration-statements")
@RequiredArgsConstructor
public class RemunerationStatementController {
  private final RemunerationStatementService service;

  @GetMapping
  @PreAuthorize(
      "hasAnyAuthority('PERM_ORDER_MANAGE', 'PERM_ORDER_SET_VIEW') and hasAuthority('PERM_REMUNERATION_VIEW')")
  public RemunerationStatementResponse statement(
      @CurrentStore Long storeId,
      @RequestParam(name = "person_id") Long personId,
      @RequestParam String month,
      @RequestParam(name = "order_page", defaultValue = "0") int orderPage,
      @RequestParam(name = "bonus_page", defaultValue = "0") int bonusPage,
      @RequestParam(defaultValue = "20") int size) {
    return service.store(storeId, personId, month, orderPage, bonusPage, size);
  }
}
