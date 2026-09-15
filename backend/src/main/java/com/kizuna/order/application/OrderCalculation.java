package com.kizuna.order.application;

import com.kizuna.order.api.dto.OrderFeeLineRequest;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderPreviewResponse;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCourse;
import com.kizuna.order.domain.OrderStatus;
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
    Order copy = Order.builder().status(OrderStatus.CONFIRMED).build();
    copy.adoptCourse(
        course,
        selection.resolve(
            original,
            lines,
            original != null && original.getStatus() == OrderStatus.COMPLETED,
            saving));
    if (original != null && original.getStatus() == OrderStatus.COMPLETED) {
      int points =
          original.getFeeLines().stream()
              .filter(line -> line.getKind().isSystemOwned())
              .mapToInt(line -> -line.getAmount())
              .sum();
      copy.completeWith(
          points, original.getAutoGrantPoints() == null ? 0 : original.getAutoGrantPoints());
    }
    return copy;
  }

  public OrderPreviewResponse preview(
      String operation,
      String id,
      Object input,
      Order calculated,
      OrderPreviewResponse.Points points) {
    var c = calculated.getCourse();
    var lines = mapper.toFeeLineResponses(calculated.getFeeLines());
    lines.stream()
        .filter(line -> line.getLineId() == null)
        .forEach(line -> line.setAdoptedAt(null));
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
            calculated.getTotalDurationMinutes(),
            calculated.getTotalRemuneration(),
            points);
    return new OrderPreviewResponse(
        confirmation.sign(operation, id, input, result),
        result.course(),
        result.feeLines(),
        result.totalFee(),
        result.totalDurationMinutes(),
        result.totalRemuneration(),
        points);
  }

  public void requirePreviewInput(String token) {
    if (token != null) throw new ServiceException("試算要求に確認値は指定できません");
  }

  public void verify(String token, OrderPreviewResponse preview) {
    confirmation.verify(token, preview.confirmationToken());
  }
}
