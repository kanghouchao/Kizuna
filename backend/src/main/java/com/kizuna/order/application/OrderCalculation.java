package com.kizuna.order.application;

import com.kizuna.customer.contact.GuestContactImport;
import com.kizuna.order.api.dto.OrderFeeLineRequest;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderPreviewResponse;
import com.kizuna.order.api.dto.OrderSpecialServiceResponse;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCourse;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.domain.SpecialServiceSnapshot;
import com.kizuna.service.application.OrderServiceCatalog;
import com.kizuna.service.application.OrderServiceTerms;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCalculation {
  private final OrderServiceCatalog catalog;
  private final OrderMapper mapper;
  private final OrderConfirmation confirmation;
  private final OrderFeeLineSelection selection;
  private final OrderSpecialServices specialServices;

  public OrderCourse current(String courseId, boolean saving) {
    try {
      return snapshot(
          catalog.current(courseId, OrderServiceCatalog.SelectionKind.COURSE), "CURRENT_SETTING");
    } catch (NotFoundException ex) {
      if (saving) throw new OrderConfirmationConflict("confirmation_token");
      throw ex;
    }
  }

  public OrderCourse historical(String revisionId) {
    return snapshot(
        catalog.historical(revisionId, OrderServiceCatalog.SelectionKind.COURSE),
        "HISTORICAL_CORRECTION");
  }

  private OrderCourse snapshot(OrderServiceTerms terms, String basis) {
    return new OrderCourse(
        terms.serviceId(),
        terms.revisionId(),
        terms.revisionNumber(),
        terms.name(),
        terms.durationMinutes(),
        terms.price(),
        terms.remuneration(),
        basis,
        OffsetDateTime.now());
  }

  public void requireVersion(Order order, Long version) {
    if (!Objects.equals(order.getVersion(), version))
      throw new OrderConfirmationConflict("expected_version");
  }

  public Order calculate(
      Order original, OrderCourse course, List<OrderFeeLineRequest> lines, boolean saving) {
    return calculate(
        original,
        course,
        lines,
        original == null ? List.of() : original.getSpecialServices(),
        saving);
  }

  public Order calculate(
      Order original,
      OrderCourse course,
      List<OrderFeeLineRequest> lines,
      List<SpecialServiceSnapshot> specials,
      boolean saving) {
    Order source =
        original == null ? Order.builder().status(OrderStatus.CONFIRMED).build() : original;
    return source.calculateWith(
        course,
        specials,
        selection.resolve(
            original,
            lines,
            original != null && original.getStatus() == OrderStatus.COMPLETED,
            saving));
  }

  public OrderPreviewResponse preview(
      String operation,
      String id,
      Object input,
      Order calculated,
      OrderPreviewResponse.Points points) {
    return preview(operation, id, input, calculated, points, List.of());
  }

  public OrderPreviewResponse preview(
      String operation,
      String id,
      Object input,
      Order calculated,
      OrderPreviewResponse.Points points,
      List<GuestContactImport> contactImports) {
    var c = calculated.getCourse();
    var lines = mapper.toFeeLineResponses(calculated.getFeeLines());
    lines.stream()
        .filter(line -> line.getLineId() == null)
        .forEach(line -> line.setAdoptedAt(null));
    var specials = specialServices.describe(calculated);
    int unresolved =
        (int) specials.stream().filter(OrderSpecialServiceResponse::requiresAttention).count();
    var result =
        new OrderPreviewResponse(
            null,
            new OrderPreviewResponse.CourseCondition(
                c.serviceId(),
                c.revisionId(),
                c.revisionNumber(),
                c.name(),
                c.durationMinutes(),
                c.price(),
                c.remuneration(),
                c.adoptionBasis()),
            lines,
            calculated.getTotalFee(),
            calculated.grantBasisAmount(),
            calculated.getTotalDurationMinutes(),
            calculated.getTotalRemuneration(),
            points,
            specials,
            unresolved > 0,
            unresolved,
            contactImports);
    return new OrderPreviewResponse(
        confirmation.sign(operation, id, input, result),
        result.course(),
        result.feeLines(),
        result.totalFee(),
        result.pointBasisAmount(),
        result.totalDurationMinutes(),
        result.totalRemuneration(),
        points,
        specials,
        unresolved > 0,
        unresolved,
        contactImports);
  }

  public void requirePreviewInput(String token) {
    if (token != null) throw new ServiceException("試算要求に確認値は指定できません");
  }

  public boolean wasPreviewed(String operation, String id, Object input, String token) {
    return confirmation.wasPreviewed(operation, id, input, token);
  }

  public void verify(String token, OrderPreviewResponse preview) {
    confirmation.verify(token, preview.confirmationToken());
  }
}
