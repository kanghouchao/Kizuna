package com.kizuna.order.application;

import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.order.domain.OrderCorrectionSnapshot;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.domain.SelfRemunerationView;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.user.application.ActorIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SelfRemunerationService {
  private final ActorIdentityService actors;
  private final CastRepository people;
  private final CastEnrollmentRepository enrollments;
  private final OrderRepository orders;
  private final OrderCorrectionHistory history;

  @Transactional(readOnly = true)
  public Page<SelfRemunerationEnrollment> enrollments(String actor, Pageable pageable) {
    var person = people.findByUserId(actors.requireUserId(actor));
    if (person.isEmpty()) return Page.empty(pageable);
    return enrollments
        .findPersonEnrollments(person.get().getId(), pageable)
        .map(
            e ->
                new SelfRemunerationEnrollment(
                    e.getId(), e.getStoreId(), e.getStoreName(), e.getStatus(), e.getEndedAt()));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<SelfRemunerationOrder> list(String actor, String enrollmentId, Pageable pageable) {
    Long userId = actors.requireUserId(actor);
    if (enrollmentId == null)
      return orders.findSelfRemunerations(userId, pageable).map(this::summary);
    var person = people.findByUserId(userId).orElseThrow(() -> new NotFoundException("在籍が見つかりません"));
    if (!enrollments.existsByIdAndCastId(enrollmentId, person.getId()))
      throw new NotFoundException("在籍が見つかりません");
    return orders
        .findSelfRemunerationsByEnrollment(userId, enrollmentId, pageable)
        .map(this::summary);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SelfRemunerationDetail detail(String actor, String id) {
    Long userId = actors.requireUserId(actor);
    var view =
        orders
            .findSelfRemuneration(userId, id)
            .orElseThrow(() -> new NotFoundException("報酬明細が見つかりません"));
    var order =
        orders.findSelfOrder(userId, id).orElseThrow(() -> new NotFoundException("報酬明細が見つかりません"));
    return new SelfRemunerationDetail(
        summary(view),
        order.getVersion(),
        SelfRemunerationItems.from(OrderCorrectionSnapshot.of(order)));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<SelfRemunerationChange> changes(String actor, String id, Pageable pageable) {
    return history
        .self(actors.requireUserId(actor), id, pageable)
        .map(
            c ->
                new SelfRemunerationChange(
                    c.getId(),
                    c.getChangeType(),
                    c.getOrderId(),
                    c.getBusinessDate(),
                    c.getCompletedAt(),
                    c.getCorrectedAt(),
                    c.getReason(),
                    c.getBeforeVersion(),
                    c.getAfterVersion(),
                    snapshot(c.getBeforeSnapshot()),
                    snapshot(c.getAfterSnapshot())));
  }

  private SelfRemunerationState snapshot(OrderCorrectionSnapshot value) {
    var items = SelfRemunerationItems.from(value);
    return new SelfRemunerationState(
        items,
        items.stream().mapToInt(SelfRemunerationLine::remuneration).sum(),
        value.accruedRemuneration(),
        value.completionInvalidated());
  }

  private SelfRemunerationOrder summary(SelfRemunerationView view) {
    boolean planned =
        view.getStatus() == OrderStatus.CONFIRMED || view.getStatus() == OrderStatus.IN_SERVICE;
    int accrued =
        view.getStatus() == OrderStatus.COMPLETED && !view.isCompletionInvalidated()
            ? view.getAccruedRemuneration()
            : 0;
    return new SelfRemunerationOrder(
        view.getOrderId(),
        view.getEnrollmentId(),
        view.getStoreId(),
        view.getStoreName(),
        view.getBusinessDate(),
        view.getCompletedAt(),
        view.getStatus(),
        view.isCompletionInvalidated(),
        view.getAgreedRemuneration(),
        planned ? view.getAgreedRemuneration() : 0,
        accrued);
  }
}
