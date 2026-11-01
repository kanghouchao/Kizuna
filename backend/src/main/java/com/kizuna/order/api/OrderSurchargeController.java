package com.kizuna.order.api;

import com.kizuna.order.application.OrderQueryService;
import com.kizuna.service.application.OrderServiceCatalog;
import com.kizuna.service.application.OrderServiceTerms;
import com.kizuna.shared.storescope.CurrentStore;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderSurchargeController {
  private final OrderServiceCatalog catalog;
  private final OrderQueryService orders;

  @GetMapping("/surcharge-candidates")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public Page<SurchargeCandidateResponse> candidates(
      @CurrentStore Long storeId,
      @RequestParam(required = false) String search,
      @PageableDefault(
              size = 20,
              sort = {"afterTerms.name", "serviceId", "id"})
          Pageable pageable) {
    return catalog
        .candidates(storeId, OrderServiceCatalog.SelectionKind.SURCHARGE, search, pageable)
        .map(SurchargeCandidateResponse::of);
  }

  @GetMapping("/{id}/surcharge-revisions")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_ORDER_CORRECT')")
  public Page<SurchargeRevisionResponse> revisions(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @RequestParam(required = false) String search,
      @PageableDefault(
              size = 20,
              sort = {"occurredAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    orders.get(storeId, id);
    return catalog
        .history(storeId, OrderServiceCatalog.SelectionKind.SURCHARGE, search, pageable)
        .map(SurchargeRevisionResponse::of);
  }

  public record SurchargeCandidateResponse(
      String serviceId,
      String revisionId,
      long revisionNumber,
      String name,
      int price,
      int remuneration) {
    static SurchargeCandidateResponse of(OrderServiceTerms terms) {
      return new SurchargeCandidateResponse(
          terms.serviceId(),
          terms.revisionId(),
          terms.revisionNumber(),
          terms.name(),
          terms.price(),
          terms.remuneration());
    }
  }

  public record SurchargeRevisionResponse(
      String serviceId,
      String revisionId,
      long revisionNumber,
      String name,
      int price,
      int remuneration,
      OffsetDateTime occurredAt,
      boolean serviceDeleted) {
    static SurchargeRevisionResponse of(OrderServiceTerms terms) {
      return new SurchargeRevisionResponse(
          terms.serviceId(),
          terms.revisionId(),
          terms.revisionNumber(),
          terms.name(),
          terms.price(),
          terms.remuneration(),
          terms.occurredAt(),
          terms.serviceDeleted());
    }
  }
}
