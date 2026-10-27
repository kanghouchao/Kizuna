package com.kizuna.order.application;

import com.kizuna.customer.contact.GuestContactImport;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCalculation {
  private final OrderServiceCatalog catalog;
  private final OrderConfirmation confirmation;
  private final OrderFeeLineSelection selection;
  private final OrderSpecialServices specialServices;

  public OrderCourse current(Long storeId, String courseId, boolean saving) {
    try {
      return snapshot(
          catalog.current(storeId, courseId, OrderServiceCatalog.SelectionKind.COURSE),
          "CURRENT_SETTING");
    } catch (NotFoundException ex) {
      if (saving) throw new OrderConfirmationConflict("confirmation_token");
      throw ex;
    }
  }

  public OrderCourse historical(Long storeId, String revisionId) {
    return snapshot(
        catalog.historical(storeId, revisionId, OrderServiceCatalog.SelectionKind.COURSE),
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

  public Order calculate(
      Long storeId,
      Order original,
      OrderCourse course,
      List<OrderFeeLineInput> lines,
      boolean saving) {
    return calculate(
        storeId,
        original,
        course,
        lines,
        original == null ? List.of() : original.getSpecialServices(),
        saving);
  }

  public Order calculate(
      Long storeId,
      Order original,
      OrderCourse course,
      List<OrderFeeLineInput> lines,
      List<SpecialServiceSnapshot> specials,
      boolean saving) {
    Order source =
        original == null ? Order.builder().status(OrderStatus.CONFIRMED).build() : original;
    return source.calculateWith(
        course,
        specials,
        selection.resolve(
            storeId,
            original,
            lines,
            original != null && original.getStatus() == OrderStatus.COMPLETED,
            saving));
  }

  public OrderPreview preview(
      Long storeId,
      String operation,
      String id,
      String actor,
      Object input,
      Order calculated,
      OrderPoints points) {
    return preview(storeId, operation, id, actor, input, calculated, points, List.of());
  }

  public OrderPreview preview(
      Long storeId,
      String operation,
      String id,
      String actor,
      Object input,
      Order calculated,
      OrderPoints points,
      List<GuestContactImport> contactImports) {
    var specials = specialServices.describeForPreview(storeId, calculated);
    var consent = OrderConsent.of(calculated, points, specials, contactImports);
    return new OrderPreview(
        confirmation.sign(storeId, operation, id, actor, input, consent),
        calculated.getCourse(),
        calculated.getFeeLines(),
        calculated.getTotalFee(),
        calculated.grantBasisAmount(),
        calculated.getTotalDurationMinutes(),
        calculated.getTotalRemuneration(),
        points,
        specials,
        contactImports);
  }

  public void requirePreviewInput(String token) {
    if (token != null) throw new ServiceException("試算要求に確認値は指定できません");
  }

  public boolean wasPreviewed(
      Long storeId, String operation, String id, String actor, Object input, String token) {
    return confirmation.wasPreviewed(storeId, operation, id, actor, input, token);
  }

  public void verify(
      String token,
      Long storeId,
      String operation,
      String id,
      String actor,
      Object input,
      Order calculated,
      OrderPoints points) {
    verify(token, storeId, operation, id, actor, input, calculated, points, List.of());
  }

  /** 確定の照合。表示だけの値は組み立てず、同意した内容を計算し直して試算の確認値と突き合わせる。 */
  public void verify(
      String token,
      Long storeId,
      String operation,
      String id,
      String actor,
      Object input,
      Order calculated,
      OrderPoints points,
      List<GuestContactImport> contactImports) {
    var consent =
        OrderConsent.of(
            calculated, points, specialServices.attention(storeId, calculated), contactImports);
    confirmation.verify(token, confirmation.sign(storeId, operation, id, actor, input, consent));
  }
}
