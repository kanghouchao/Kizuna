package com.kizuna.order.api.me;

import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.SelfDailyRemunerationResponse;
import com.kizuna.order.application.DailyRemunerationService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/daily-remunerations")
@RequiredArgsConstructor
public class SelfDailyRemunerationController {
  private final DailyRemunerationService service;
  private final OrderMapper mapper;

  @GetMapping
  @PreAuthorize("hasRole('CAST')")
  public SelfDailyRemunerationResponse daily(
      Principal principal,
      @RequestParam(name = "store_id") Long storeId,
      @RequestParam(name = "business_date") String businessDate,
      @PageableDefault(
              size = 20,
              sort = {})
          Pageable pageable) {
    return mapper.toResponse(service.self(principal.getName(), storeId, businessDate, pageable));
  }
}
