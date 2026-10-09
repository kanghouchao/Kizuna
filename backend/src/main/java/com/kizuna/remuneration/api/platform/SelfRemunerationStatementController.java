package com.kizuna.remuneration.api.platform;

import com.kizuna.remuneration.api.dto.RemunerationStatementResponse;
import com.kizuna.remuneration.application.RemunerationStatementService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/me/remuneration-statements")
@RequiredArgsConstructor
public class SelfRemunerationStatementController {
  private final RemunerationStatementService service;

  @GetMapping
  @PreAuthorize("hasRole('CAST')")
  public RemunerationStatementResponse statement(
      Principal principal,
      @RequestParam(name = "store_id") Long storeId,
      @RequestParam String month,
      @RequestParam(name = "order_page", defaultValue = "0") int orderPage,
      @RequestParam(name = "bonus_page", defaultValue = "0") int bonusPage,
      @RequestParam(defaultValue = "20") int size) {
    return service.self(principal.getName(), storeId, month, orderPage, bonusPage, size);
  }
}
