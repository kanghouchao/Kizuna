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
public class OrderCourseController {
  private final OrderServiceCatalog catalog;
  private final OrderQueryService orders;

  @GetMapping("/course-candidates")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public Page<Candidate> candidates(
      @CurrentStore Long storeId,
      @RequestParam(required = false) String search,
      @PageableDefault(
              size = 20,
              sort = {"afterTerms.name", "serviceId", "id"})
          Pageable pageable) {
    return catalog
        .candidates(storeId, OrderServiceCatalog.SelectionKind.COURSE, search, pageable)
        .map(Candidate::of);
  }

  @GetMapping("/{id}/course-revisions")
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
    orders.get(storeId, id);
    return catalog
        .history(storeId, OrderServiceCatalog.SelectionKind.COURSE, search, pageable)
        .map(Revision::of);
  }

  public record Candidate(
      String serviceId,
      String revisionId,
      long revisionNumber,
      String name,
      int durationMinutes,
      int price,
      int remuneration) {
    static Candidate of(OrderServiceTerms c) {
      return new Candidate(
          c.serviceId(),
          c.revisionId(),
          c.revisionNumber(),
          c.name(),
          c.durationMinutes(),
          c.price(),
          c.remuneration());
    }
  }

  public record Revision(
      String serviceId,
      String revisionId,
      long revisionNumber,
      String name,
      int durationMinutes,
      int price,
      int remuneration,
      OffsetDateTime occurredAt,
      boolean serviceDeleted) {
    static Revision of(OrderServiceTerms c) {
      return new Revision(
          c.serviceId(),
          c.revisionId(),
          c.revisionNumber(),
          c.name(),
          c.durationMinutes(),
          c.price(),
          c.remuneration(),
          c.occurredAt(),
          c.serviceDeleted());
    }
  }
}
