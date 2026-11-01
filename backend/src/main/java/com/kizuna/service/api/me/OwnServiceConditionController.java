package com.kizuna.service.api.me;

import com.kizuna.service.api.dto.OwnConsentRequest;
import com.kizuna.service.api.dto.OwnServiceConditionSummary;
import com.kizuna.service.application.OwnServiceConditionService;
import com.kizuna.service.domain.ServiceKind;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CAST は権限（PERM_）を持たず {@code @CurrentStore} で店舗を名乗れないため、store_id を受け、本人の現行在籍をサービス内で検証する。 */
@RestController
@RequestMapping("/me/service-conditions")
@RequiredArgsConstructor
public class OwnServiceConditionController {
  private final OwnServiceConditionService service;

  @GetMapping
  @PreAuthorize("hasAuthority('ROLE_CAST')")
  public Page<OwnServiceConditionSummary> list(
      @RequestParam("store_id") Long storeId,
      @RequestParam(required = false) ServiceKind kind,
      @PageableDefault(
              size = 20,
              sort = {"id"})
          Pageable pageable,
      Principal principal) {
    return service.list(storeId, principal.getName(), kind, pageable);
  }

  @PutMapping("/{id}/consent")
  @PreAuthorize("hasAuthority('ROLE_CAST')")
  public OwnServiceConditionSummary decide(
      @PathVariable String id,
      @RequestParam("store_id") Long storeId,
      @Valid @RequestBody OwnConsentRequest request,
      Principal principal) {
    return service.decide(storeId, principal.getName(), id, request);
  }
}
