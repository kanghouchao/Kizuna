package com.kizuna.order.api.store;

import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.api.dto.OrderSpecialServiceEventResponse;
import com.kizuna.order.api.dto.OrderStartRequest;
import com.kizuna.order.application.OrderService;
import com.kizuna.order.application.OrderSpecialServices;
import com.kizuna.service.application.OrderSpecialServiceCatalog;
import com.kizuna.service.application.SpecialServiceTerms;
import com.kizuna.shared.web.CursorPage;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store/orders")
@RequiredArgsConstructor
public class OrderSpecialServiceController {
  private final OrderSpecialServiceCatalog catalog;
  private final OrderSpecialServices specials;
  private final OrderService orders;

  @GetMapping("/special-service-candidates")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public Page<Candidate> candidates(
      @RequestParam("cast_id") String castId,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return catalog.candidates(castId, search, page, size).map(Candidate::of);
  }

  @GetMapping("/{id}/special-service-revisions")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_ORDER_CORRECT')")
  public CursorPage<Revision> revisions(
      @PathVariable String id,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    orders.get(id);
    return catalog.history(search, cursor, size).map(Revision::of);
  }

  @GetMapping("/{id}/special-service-events")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public CursorPage<OrderSpecialServiceEventResponse> events(
      @PathVariable String id,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return specials.history(id, cursor, size);
  }

  @PostMapping("/{id}/start")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public OrderResponse start(
      @PathVariable String id, @Valid @RequestBody OrderStartRequest request, Principal principal) {
    return orders.start(id, request.expectedVersion(), request.reason(), principal.getName());
  }

  public record Candidate(
      String serviceId,
      String revisionId,
      long revisionNumber,
      long termsVersion,
      String name,
      String chargeType,
      int price,
      int remuneration,
      String consentEventId,
      Long consentVersion) {
    static Candidate of(SpecialServiceTerms t) {
      return new Candidate(
          t.serviceId(),
          t.revisionId(),
          t.revisionNumber(),
          t.termsVersion(),
          t.name(),
          t.chargeType(),
          t.price(),
          t.remuneration(),
          t.consentEventId(),
          t.consentVersion());
    }
  }

  public record Revision(
      String serviceId,
      String revisionId,
      long revisionNumber,
      long termsVersion,
      String name,
      String chargeType,
      int price,
      int remuneration,
      OffsetDateTime occurredAt,
      boolean serviceDeleted) {
    static Revision of(SpecialServiceTerms t) {
      return new Revision(
          t.serviceId(),
          t.revisionId(),
          t.revisionNumber(),
          t.termsVersion(),
          t.name(),
          t.chargeType(),
          t.price(),
          t.remuneration(),
          t.occurredAt(),
          t.serviceDeleted());
    }
  }
}
