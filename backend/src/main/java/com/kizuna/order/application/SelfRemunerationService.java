package com.kizuna.order.application;

import com.kizuna.cast.domain.CastEnrollmentRepository;
import com.kizuna.cast.domain.CastRepository;
import com.kizuna.order.api.dto.SelfRemunerationChangeResponse;
import com.kizuna.order.api.dto.SelfRemunerationEnrollmentSummary;
import com.kizuna.order.api.dto.SelfRemunerationItem;
import com.kizuna.order.api.dto.SelfRemunerationResponse;
import com.kizuna.order.api.dto.SelfRemunerationSnapshot;
import com.kizuna.order.api.dto.SelfRemunerationSummary;
import com.kizuna.order.domain.OrderCorrectionSnapshot;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderStatus;
import com.kizuna.order.domain.SelfRemunerationView;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScopeExempt;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.user.application.ActorIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

  @StoreScopeExempt(reason = "認証主体から本人を解決し、退店を含む全在籍を本人 ID で絞る")
  @Transactional(readOnly = true)
  public Page<SelfRemunerationEnrollmentSummary> enrollments(String actor, int page, int size) {
    var pageable = page(page, size);
    var person = people.findByPlatformUserId(actors.requireUserId(actor));
    if (person.isEmpty()) return Page.empty(pageable);
    return enrollments
        .findPersonEnrollments(person.get().getId(), pageable)
        .map(
            e ->
                new SelfRemunerationEnrollmentSummary(
                    e.getId(), e.getStoreId(), e.getStoreName(), e.getStatus(), e.getEndedAt()));
  }

  @StoreScopeExempt(reason = "認証主体と受注の担当在籍の本人をクエリで照合し、退店履歴も許可する")
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Page<SelfRemunerationSummary> list(String actor, String enrollmentId, int page, int size) {
    var pageable = page(page, size);
    Long userId = actors.requireUserId(actor);
    if (enrollmentId == null)
      return orders.findSelfRemunerations(userId, pageable).map(this::summary);
    var person =
        people.findByPlatformUserId(userId).orElseThrow(() -> new NotFoundException("在籍が見つかりません"));
    enrollments
        .findById(enrollmentId)
        .filter(e -> person.getId().equals(e.getCastId()))
        .orElseThrow(() -> new NotFoundException("在籍が見つかりません"));
    return orders
        .findSelfRemunerationsByEnrollment(userId, enrollmentId, pageable)
        .map(this::summary);
  }

  @StoreScopeExempt(reason = "認証主体と担当在籍の本人一致を各受注の読み取り条件に含める")
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SelfRemunerationResponse detail(String actor, String id) {
    Long userId = actors.requireUserId(actor);
    var view =
        orders
            .findSelfRemuneration(userId, id)
            .orElseThrow(() -> new NotFoundException("報酬明細が見つかりません"));
    var order =
        orders.findSelfOrder(userId, id).orElseThrow(() -> new NotFoundException("報酬明細が見つかりません"));
    return new SelfRemunerationResponse(
        summary(view),
        order.getVersion(),
        SelfRemunerationItems.from(OrderCorrectionSnapshot.of(order)));
  }

  @StoreScopeExempt(reason = "本人の受注所有権を検証する履歴読み取りへ委譲し、専用 DTO に限定する")
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CursorPage<SelfRemunerationChangeResponse> changes(
      String actor, String id, String cursor, int size) {
    return history
        .self(actors.requireUserId(actor), id, cursor, size)
        .map(
            c ->
                new SelfRemunerationChangeResponse(
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

  private SelfRemunerationSnapshot snapshot(OrderCorrectionSnapshot value) {
    var items = SelfRemunerationItems.from(value);
    return new SelfRemunerationSnapshot(
        items,
        items.stream().mapToInt(SelfRemunerationItem::remuneration).sum(),
        value.accruedRemuneration(),
        value.completionInvalidated());
  }

  private SelfRemunerationSummary summary(SelfRemunerationView view) {
    boolean planned =
        view.getStatus() == OrderStatus.CONFIRMED || view.getStatus() == OrderStatus.IN_SERVICE;
    int accrued =
        view.getStatus() == OrderStatus.COMPLETED && !view.isCompletionInvalidated()
            ? view.getAccruedRemuneration()
            : 0;
    return new SelfRemunerationSummary(
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

  private PageRequest page(int page, int size) {
    if (page < 0) throw new ServiceException("ページ番号が不正です");
    return PageRequest.of(page, CursorPage.clampSize(size));
  }
}
