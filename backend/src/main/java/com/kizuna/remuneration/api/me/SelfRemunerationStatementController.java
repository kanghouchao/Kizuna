package com.kizuna.remuneration.api.me;

import com.kizuna.remuneration.api.dto.RemunerationStatementResponse;
import com.kizuna.remuneration.application.RemunerationStatementService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/remuneration-statements")
@RequiredArgsConstructor
public class SelfRemunerationStatementController {
  private final RemunerationStatementService service;

  @GetMapping
  @PreAuthorize("hasRole('CAST')")
  public RemunerationStatementResponse statement(
      Principal principal,
      @RequestParam(name = "store_id") Long storeId,
      @RequestParam String month,
      @Qualifier("order")
          @PageableDefault(
              size = 20,
              sort = {})
          Pageable orderPageable,
      @Qualifier("bonus")
          @PageableDefault(
              size = 20,
              sort = {})
          Pageable bonusPageable) {
    return service.self(principal.getName(), storeId, month, orderPageable, bonusPageable);
  }
}
