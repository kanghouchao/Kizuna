package com.kizuna.order.api;

import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderResponse;
import com.kizuna.order.api.dto.OrderSpecialServiceEventResponse;
import com.kizuna.order.api.dto.OrderStartRequest;
import com.kizuna.order.application.OrderQueryService;
import com.kizuna.order.application.OrderService;
import com.kizuna.order.application.OrderSpecialServices;
import com.kizuna.service.application.OrderSpecialServiceCatalog;
import com.kizuna.service.application.SpecialServiceTerms;
import com.kizuna.shared.storescope.CurrentStore;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderSpecialServiceController {
  private final OrderSpecialServiceCatalog catalog;
  private final OrderSpecialServices specials;
  private final OrderService orders;
  private final OrderQueryService orderQueries;
  private final OrderMapper orderMapper;

  @GetMapping("/special-service-candidates")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public Page<Candidate> candidates(
      @CurrentStore Long storeId,
      @RequestParam("cast_id") String castId,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return catalog.candidates(storeId, castId, search, page, size).map(Candidate::of);
  }

  @GetMapping("/{id}/special-service-revisions")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_ORDER_CORRECT')")
  public Page<Revision> revisions(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @RequestParam(required = false) String search,
      @PageableDefault(
              size = 20,
              sort = {"occurredAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    orderQueries.get(storeId, id);
    return catalog.history(storeId, search, pageable).map(Revision::of);
  }

  @GetMapping("/{id}/special-service-events")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public Page<OrderSpecialServiceEventResponse> events(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @PageableDefault(
              size = 20,
              sort = {"occurredAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return specials.history(storeId, id, pageable).map(orderMapper::toResponse);
  }

  @PostMapping("/{id}/start")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public OrderResponse start(
      @CurrentStore Long storeId,
      @PathVariable String id,
      @Valid @RequestBody OrderStartRequest request,
      Principal principal) {
    return orderMapper.toResponse(
        orders.start(
            storeId, id, request.expectedVersion(), request.reason(), principal.getName()));
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
