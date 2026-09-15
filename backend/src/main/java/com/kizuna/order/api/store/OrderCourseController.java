package com.kizuna.order.api.store;

import com.kizuna.order.application.OrderService;
import com.kizuna.service.application.CourseTerms;
import com.kizuna.service.application.OrderCourseCatalog;
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
public class OrderCourseController {
  private final OrderCourseCatalog catalog;
  private final OrderService orders;

  @GetMapping("/course-candidates")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE')")
  public Page<Candidate> candidates(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return catalog.candidates(search, page, size).map(Candidate::of);
  }

  @GetMapping("/{id}/course-revisions")
  @PreAuthorize("hasAuthority('PERM_ORDER_MANAGE') and hasAuthority('PERM_ORDER_CORRECT')")
  public CursorPage<Revision> revisions(
      @PathVariable String id,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    orders.get(id);
    return catalog.history(search, cursor, size).map(Revision::of);
  }

  public record Candidate(
      String serviceId,
      String revisionId,
      long revisionNumber,
      String name,
      int durationMinutes,
      int price,
      int remuneration) {
    static Candidate of(CourseTerms c) {
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
    static Revision of(CourseTerms c) {
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
