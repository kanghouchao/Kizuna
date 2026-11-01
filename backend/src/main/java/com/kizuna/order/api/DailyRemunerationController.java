package com.kizuna.order.api;

import com.kizuna.order.api.dto.DailyRemunerationResponse;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.application.DailyRemunerationService;
import com.kizuna.shared.storescope.CurrentStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/daily-remunerations")
@RequiredArgsConstructor
public class DailyRemunerationController {
  private final DailyRemunerationService service;
  private final OrderMapper mapper;

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public DailyRemunerationResponse daily(
      @CurrentStore Long storeId,
      @RequestParam(name = "person_id") Long personId,
      @RequestParam(name = "business_date") String businessDate,
      @PageableDefault(
              size = 20,
              sort = {})
          Pageable pageable) {
    return mapper.toResponse(service.store(storeId, personId, businessDate, pageable));
  }
}
