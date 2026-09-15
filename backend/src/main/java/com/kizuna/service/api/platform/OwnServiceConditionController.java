package com.kizuna.service.api.platform;

import com.kizuna.service.api.dto.OwnConsentRequest;
import com.kizuna.service.api.dto.OwnServiceConditionSummary;
import com.kizuna.service.application.OwnServiceConditionService;
import com.kizuna.service.domain.ServiceKind;
import com.kizuna.shared.storescope.StoreContext;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/me/service-conditions")
@RequiredArgsConstructor
public class OwnServiceConditionController {
  private final OwnServiceConditionService service;
  private final StoreContext context;

  @GetMapping
  @PreAuthorize("hasAuthority('ROLE_CAST')")
  public Page<OwnServiceConditionSummary> list(
      @RequestParam("store_id") Long storeId,
      @RequestParam(required = false) ServiceKind kind,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Principal principal) {
    return inStore(storeId, () -> service.list(principal.getName(), kind, page, size));
  }

  @PutMapping("/{id}/consent")
  @PreAuthorize("hasAuthority('ROLE_CAST')")
  public OwnServiceConditionSummary decide(
      @PathVariable String id,
      @RequestParam("store_id") Long storeId,
      @Valid @RequestBody OwnConsentRequest request,
      Principal principal) {
    return inStore(storeId, () -> service.decide(principal.getName(), id, request));
  }

  // 本人の現行在籍をサービス内で検証する。JWT の店舗集合では退店・再入店の即時性を保証できない。
  private <T> T inStore(Long storeId, Supplier<T> action) {
    try {
      context.setStoreId(storeId);
      return action.get();
    } finally {
      context.clear();
    }
  }
}
