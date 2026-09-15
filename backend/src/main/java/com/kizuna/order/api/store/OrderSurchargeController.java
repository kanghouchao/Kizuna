package com.kizuna.order.api.store;

import com.kizuna.order.application.OrderService;
import com.kizuna.service.application.OrderServiceCatalog;
import com.kizuna.service.application.OrderServiceTerms;
import com.kizuna.shared.web.CursorPage;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store/orders")
@RequiredArgsConstructor
public class OrderSurchargeController {
  private final OrderServiceCatalog catalog;
  private final OrderService orders;

  @GetMapping("/surcharge-candidates")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public Page<SurchargeCandidateResponse> candidates(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return catalog
        .candidates(OrderServiceCatalog.SelectionKind.SURCHARGE, search, page, size)
        .map(SurchargeCandidateResponse::of);
  }

  @GetMapping("/{id}/surcharge-revisions")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_ORDER_CORRECT')")
  public CursorPage<SurchargeRevisionResponse> revisions(
      @PathVariable String id,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    orders.get(id);
    return catalog
        .history(OrderServiceCatalog.SelectionKind.SURCHARGE, search, cursor, size)
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
