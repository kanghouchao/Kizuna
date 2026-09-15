package com.kizuna.order.application;

import com.kizuna.order.api.dto.OrderSpecialServiceEventResponse;
import com.kizuna.order.api.dto.OrderSpecialServiceResponse;
import com.kizuna.order.domain.Order;
import com.kizuna.order.domain.OrderRepository;
import com.kizuna.order.domain.OrderSpecialServiceEvent;
import com.kizuna.order.domain.OrderSpecialServiceEventRepository;
import com.kizuna.order.domain.SpecialServiceHistorySnapshot;
import com.kizuna.order.domain.SpecialServiceSnapshot;
import com.kizuna.service.application.OrderSpecialServiceCatalog;
import com.kizuna.service.application.SpecialServiceRejection;
import com.kizuna.service.application.SpecialServiceRejectionHandler;
import com.kizuna.service.application.SpecialServiceTerms;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.store.domain.StoreRepository;
import com.kizuna.user.application.ActorIdentityService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderSpecialServices implements SpecialServiceRejectionHandler {
  private final OrderSpecialServiceCatalog catalog;
  private final OrderSpecialServiceEventRepository events;
  private final OrderRepository orders;
  private final StoreRepository stores;
  private final StoreContext context;
  private final ActorIdentityService actors;

  @StoreScoped
  @Transactional(propagation = Propagation.MANDATORY)
  public void lock() {
    stores.lockCastFields(context.getStoreId());
  }

  @StoreScoped
  @Transactional(propagation = Propagation.MANDATORY)
  public List<SpecialServiceSnapshot> select(
      Order original, String cast, List<String> ids, boolean confirmedInput) {
    if (original != null && !Objects.equals(original.getCastId(), cast)) {
      if (ids != null && !ids.isEmpty()) throw new ServiceException("担当変更を保存してから特殊サービスを選び直してください");
      return List.of();
    }
    if (ids == null) return original == null ? List.of() : original.getSpecialServices();
    requireUnique(ids);
    var previous =
        original == null ? List.<SpecialServiceSnapshot>of() : original.getSpecialServices();
    return ids.stream()
        .sorted()
        .map(
            id ->
                previous.stream()
                    .filter(s -> s.serviceId().equals(id))
                    .findFirst()
                    .orElseGet(
                        () -> {
                          try {
                            return snapshot(catalog.current(cast, id), cast, "ACCEPTED_TERMS");
                          } catch (NotFoundException | ServiceException ex) {
                            if (confirmedInput)
                              throw new OrderConfirmationConflict("confirmation_token");
                            throw ex;
                          }
                        }))
        .toList();
  }

  @StoreScoped
  @Transactional(propagation = Propagation.MANDATORY)
  public List<SpecialServiceSnapshot> historical(Order order, List<String> ids) {
    if (ids == null) return order.getSpecialServices();
    requireUnique(ids);
    if (!ids.isEmpty() && order.getCastId() == null)
      throw new ServiceException("担当未設定の受注には特殊サービスを追加できません");
    var result =
        ids.stream()
            .sorted()
            .map(
                id ->
                    order.getSpecialServices().stream()
                        .filter(s -> s.revisionId().equals(id))
                        .findFirst()
                        .orElseGet(
                            () ->
                                snapshot(
                                    catalog.historical(id),
                                    order.getCastId(),
                                    "HISTORICAL_CORRECTION")))
            .toList();
    if (result.stream().map(SpecialServiceSnapshot::serviceId).distinct().count() != result.size())
      throw new ServiceException("同じ特殊サービスの版本は一つだけ選択してください");
    return result;
  }

  private void requireUnique(List<String> ids) {
    if (ids.stream().anyMatch(id -> id == null || id.isBlank())
        || ids.stream().distinct().count() != ids.size())
      throw new ServiceException("特殊サービスは重複せず指定してください");
  }

  private SpecialServiceSnapshot snapshot(SpecialServiceTerms t, String cast, String basis) {
    return new SpecialServiceSnapshot(
        t.serviceId(),
        t.revisionId(),
        t.revisionNumber(),
        t.termsVersion(),
        t.name(),
        t.chargeType(),
        t.price(),
        t.remuneration(),
        basis,
        OffsetDateTime.now(),
        cast,
        t.consentEventId(),
        t.consentVersion());
  }

  @StoreScoped
  @Transactional(propagation = Propagation.MANDATORY)
  public void requireProgress(Order order) {
    if (!events.unresolved(order.getId()).isEmpty())
      throw new OrderConfirmationConflict("special_services", "拒否された特殊サービスを編集画面で修復してください");
  }

  @StoreScoped
  @Transactional(propagation = Propagation.MANDATORY)
  public List<OrderSpecialServiceResponse> describe(Order order) {
    var unresolved =
        order.getId() == null
            ? List.<OrderSpecialServiceEvent>of()
            : events.unresolved(order.getId());
    return order.getSpecialServices().stream()
        .map(
            s ->
                new OrderSpecialServiceResponse(
                    s,
                    !order.getStatus().isTerminal()
                        && unresolved.stream()
                            .anyMatch(
                                e ->
                                    e.getServiceId().equals(s.serviceId())
                                        && e.getEnrollmentId().equals(s.enrollmentId())),
                    order.getStatus().isTerminal()
                        ? null
                        : catalog.status(s.enrollmentId(), s.serviceId())))
        .toList();
  }

  @StoreScoped
  @Transactional(propagation = Propagation.MANDATORY)
  public void resolve(
      Order order, List<SpecialServiceSnapshot> previous, int previousTotal, String previousCast) {
    var unresolved = events.unresolved(order.getId());
    var before = historySnapshots(previous, unresolved);
    var after = historySnapshots(order.getSpecialServices(), unresolved);
    for (var rejection : unresolved) {
      if (order.getSpecialServices().stream()
          .anyMatch(
              s ->
                  s.serviceId().equals(rejection.getServiceId())
                      && s.enrollmentId().equals(rejection.getEnrollmentId()))) continue;
      var resolution =
          !Objects.equals(order.getCastId(), previousCast)
              ? "CAST_CHANGED"
              : order.getSpecialServices().stream()
                      .anyMatch(
                          s ->
                              previous.stream().noneMatch(p -> p.serviceId().equals(s.serviceId())))
                  ? "RESELECTED"
                  : "REMOVED";
      events.save(
          rejection.resolved(
              order,
              before,
              after,
              previousTotal,
              resolution,
              actors.requireUserId(
                  SecurityContextHolder.getContext().getAuthentication().getName())));
    }
  }

  private List<SpecialServiceHistorySnapshot> historySnapshots(
      List<SpecialServiceSnapshot> snapshots, List<OrderSpecialServiceEvent> unresolved) {
    return snapshots.stream()
        .map(
            s ->
                new SpecialServiceHistorySnapshot(
                    s,
                    unresolved.stream()
                        .anyMatch(
                            e ->
                                e.getServiceId().equals(s.serviceId())
                                    && e.getEnrollmentId().equals(s.enrollmentId()))))
        .toList();
  }

  @Override
  @StoreScoped
  @Transactional(propagation = Propagation.MANDATORY)
  public void applyRejection(SpecialServiceRejection event) {
    for (var order : orders.findUnfinishedSpecialOrders(event.enrollmentId(), event.serviceId())) {
      events.save(
          OrderSpecialServiceEvent.rejected(
              order,
              historySnapshots(order.getSpecialServices(), events.unresolved(order.getId())),
              event.enrollmentId(),
              event.serviceId(),
              event.consentEventId(),
              event.actorId(),
              event.occurredAt()));
      order.setUpdatedAt(event.occurredAt());
    }
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<OrderSpecialServiceEventResponse> history(
      String orderId, String cursor, int size) {
    orders.findScopedById(orderId).orElseThrow(() -> new NotFoundException("受注が見つかりません"));
    if (size < 1 || size > Math.min(100, CursorPage.MAX_SIZE))
      throw new ServiceException("取得件数が正しくありません");
    var key = cursor == null ? null : PageCursor.decode(cursor);
    OffsetDateTime at;
    try {
      at =
          key == null
              ? OffsetDateTime.parse("9999-12-31T23:59:59Z")
              : OffsetDateTime.parse(key.key());
    } catch (RuntimeException ex) {
      throw new ServiceException("カーソルが正しくありません");
    }
    return CursorPage.of(
            events.history(orderId, at, key == null ? "" : key.id(), PageRequest.of(0, size + 1)),
            size,
            e -> new PageCursor(e.getOccurredAt().toString(), e.getId()).encode())
        .map(OrderSpecialServiceEventResponse::of);
  }
}
