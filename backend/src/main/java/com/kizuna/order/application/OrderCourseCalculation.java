package com.kizuna.order.application;

import com.kizuna.order.api.dto.OrderFeeLineRequest;
import com.kizuna.order.api.dto.OrderMapper;
import com.kizuna.order.api.dto.OrderPreviewResponse;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderCourse;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.service.application.CourseTerms;
import com.kizuna.service.application.OrderCourseCatalog;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCourseCalculation {
  private final OrderCourseCatalog catalog;
  private final OrderMapper mapper;
  private final OrderConfirmation confirmation;

  public OrderCourse current(String courseId, boolean saving) {
    try {
      return snapshot(catalog.current(courseId), "CURRENT_SETTING");
    } catch (NotFoundException ex) {
      if (saving) throw new OrderConfirmationConflict("confirmation_token");
      throw ex;
    }
  }

  public OrderCourse historical(String revisionId) {
    return snapshot(catalog.historical(revisionId), "HISTORICAL_CORRECTION");
  }

  private OrderCourse snapshot(CourseTerms terms, String basis) {
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

  public Order calculate(Order original, OrderCourse course, List<OrderFeeLineRequest> lines) {
    Order copy = Order.builder().status(OrderStatus.CONFIRMED).build();
    copy.adoptCourse(
        course,
        lines == null
            ? original == null ? List.of() : original.editableFeeLines()
            : mapper.toFeeLineDrafts(lines));
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
        .filter(line -> "BASE_COURSE".equals(line.getKind()))
        .forEach(line -> line.setRemuneration(c.remuneration()));
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
            points);
    return new OrderPreviewResponse(
        confirmation.sign(operation, id, input, result),
        result.course(),
        result.feeLines(),
        result.totalFee(),
        points);
  }

  public void requirePreviewInput(String token) {
    if (token != null) throw new ServiceException("試算要求に確認値は指定できません");
  }

  public void verify(String token, OrderPreviewResponse preview) {
    confirmation.verify(token, preview.confirmationToken());
  }
}
