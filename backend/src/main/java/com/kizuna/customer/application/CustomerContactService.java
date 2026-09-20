package com.kizuna.customer.application;

import com.kizuna.customer.api.dto.ContactHistoryResponse;
import com.kizuna.customer.api.dto.ContactPermissionRequest;
import com.kizuna.customer.api.dto.ContactRequest;
import com.kizuna.customer.api.dto.ContactResponse;
import com.kizuna.customer.api.dto.ContactSummary;
import com.kizuna.customer.domain.ContactAction;
import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactPermissions;
import com.kizuna.customer.domain.ContactPurpose;
import com.kizuna.customer.domain.ContactRestrictionView;
import com.kizuna.customer.domain.ContactState;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.CustomerContact;
import com.kizuna.customer.domain.CustomerContactHistory;
import com.kizuna.customer.domain.CustomerContactHistoryRepository;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.exception.StaleSessionException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.web.CursorPage;
import com.kizuna.shared.web.PageCursor;
import com.kizuna.user.domain.PlatformUserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerContactService {
  private final CustomerRepository customers;
  private final CustomerContactRepository contacts;
  private final CustomerContactHistoryRepository histories;
  private final PlatformUserRepository users;

  @StoreScoped
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CursorPage<ContactResponse> list(String customerId, String cursor, int requestedSize) {
    String resolved = resolve(customerId);
    int size = CursorPage.clampSize(requestedSize);
    var page =
        CursorPage.of(
            contacts.findByCustomerIdAndDeletedFalseAndIdGreaterThanOrderByIdAsc(
                resolved, cursor == null ? "" : PageCursor.decodeKey(cursor), Limit.of(size + 1)),
            size,
            c -> PageCursor.encodeKey(c.getId()));
    var values = page.content().stream().map(CustomerContact::getValue).distinct().toList();
    if (values.isEmpty()) return new CursorPage<>(List.of(), null);
    // 同値の状態組だけを読むため、重複行数によらず一値あたり最大 3 種類 × 9 状態組に収まる。
    var restrictions =
        contacts.findRestrictions(resolved, values).stream()
            .collect(
                Collectors.toMap(
                    c -> new ContactKey(c.getType(), c.getValue()),
                    ContactRestrictionView::permissions,
                    ContactPermissions::restrict));
    return page.map(
        c -> ContactResponse.from(c, restrictions.get(new ContactKey(c.getType(), c.getValue()))));
  }

  @StoreScoped
  @Transactional
  public ContactResponse create(String customerId, ContactRequest input) {
    lock(customerId);
    CustomerContact contact =
        contacts.saveAndFlush(CustomerContact.create(customerId, input.type(), input.value()));
    record(contact, ContactAction.CREATE, null, actorId(), UUID.randomUUID().toString());
    return response(contact);
  }

  @StoreScoped
  @Transactional
  public ContactResponse update(String customerId, String id, ContactRequest input) {
    lock(customerId);
    var contact = active(customerId, id);
    var before = contact.state();
    if (contact.isPreferred()) {
      contacts
          .findPreferred(customerId, input.type())
          .filter(other -> !other.getId().equals(id))
          .ifPresent(
              other -> {
                throw new ConflictException("変更先の種類に優先連絡先があります。先に優先指定を解除してください");
              });
    }
    contact.change(input.type(), input.value());
    String operationId = UUID.randomUUID().toString();
    Long actorId = actorId();
    if (before.type() != contact.getType() || !before.value().equals(contact.getValue()))
      inheritBeforeRemoval(contact, before, operationId, actorId);
    record(contact, ContactAction.UPDATE, before, actorId, operationId);
    contacts.flush();
    return response(contact);
  }

  @StoreScoped
  @Transactional
  public ContactResponse changePermission(
      String customerId, String id, ContactPurpose purpose, ContactPermissionRequest input) {
    lock(customerId);
    var contact = active(customerId, id);
    var before = contact.state();
    contact.changePermission(purpose, input.status());
    histories.save(
        CustomerContactHistory.permission(
            contact,
            actorId(),
            before,
            purpose,
            input.source(),
            input.reason(),
            UUID.randomUUID().toString()));
    contacts.flush();
    return response(contact);
  }

  @StoreScoped
  @Transactional
  public void delete(String customerId, String id) {
    lock(customerId);
    var contact = active(customerId, id);
    var before = contact.state();
    String operationId = UUID.randomUUID().toString();
    Long actorId = actorId();
    inheritBeforeRemoval(contact, before, operationId, actorId);
    contact.delete();
    record(contact, ContactAction.DELETE, before, actorId, operationId);
  }

  @StoreScoped
  @Transactional
  public void prefer(String customerId, ContactType type, String id) {
    lock(customerId);
    CustomerContact chosen = id == null ? null : active(customerId, id);
    if (chosen != null && chosen.getType() != type) throw new ServiceException("連絡先の種類が一致しません");
    var previous = contacts.findPreferred(customerId, type).orElse(null);
    if (previous == chosen) return;
    Long actorId = actorId();
    String operationId = UUID.randomUUID().toString();
    if (previous != null) {
      var before = previous.state();
      previous.prefer(false);
      record(previous, ContactAction.PREFERENCE, before, actorId, operationId);
      // 部分一意索引を満たしたまま指定先を切り替えるため、解除を先に確定させる。
      contacts.flush();
    }
    if (chosen != null) {
      var before = chosen.state();
      chosen.prefer(true);
      record(chosen, ContactAction.PREFERENCE, before, actorId, operationId);
    }
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<ContactHistoryResponse> history(
      String customerId, String cursor, int requestedSize) {
    String resolved = resolve(customerId);
    int size = CursorPage.clampSize(requestedSize);
    var key = cursor == null ? null : PageCursor.decode(cursor);
    var rows =
        key == null
            ? histories.history(resolved, Limit.of(size + 1))
            : histories.historyAfter(resolved, key.timestampKey(), key.id(), Limit.of(size + 1));
    return CursorPage.of(
            rows, size, h -> new PageCursor(h.getOccurredAt().toString(), h.getId()).encode())
        .map(
            h ->
                new ContactHistoryResponse(
                    h.getId(),
                    h.getContactId(),
                    h.getOriginCustomerId(),
                    h.getAction(),
                    h.getActorId(),
                    h.getOccurredAt(),
                    h.getBefore(),
                    h.getAfter(),
                    h.getOperationId(),
                    h.getPurpose(),
                    h.getSource(),
                    h.getReason(),
                    h.getSourceContactId()));
  }

  @StoreScoped
  @Transactional(readOnly = true)
  public Map<String, List<ContactSummary>> preferred(Collection<String> customerIds) {
    if (customerIds.isEmpty()) return Map.of();
    return contacts
        .findByCustomerIdInAndPreferredTrueAndDeletedFalseOrderByIdAsc(customerIds)
        .stream()
        .collect(
            Collectors.groupingBy(
                CustomerContact::getCustomerId,
                Collectors.mapping(ContactSummary::from, Collectors.toList())));
  }

  @StoreScoped
  @Transactional
  public void transfer(String survivingId, String mergedId, Long actorId) {
    // 呼出元の統合処理が両顧客を ID 順にロック済み。連絡先も同じ排他境界に参加する。
    var surviving =
        contacts.findByCustomerIdInAndPreferredTrueAndDeletedFalseOrderByIdAsc(
            List.of(survivingId));
    var moving = contacts.findByCustomerIdOrderByIdAsc(mergedId);
    for (var contact : moving) {
      if (contact.isPreferred()
          && surviving.stream().anyMatch(c -> c.getType() == contact.getType()))
        throw new ConflictException("両方の顧客に同じ種類の優先連絡先があります。顧客編集で優先指定を解除してから統合してください");
    }
    String operationId = UUID.randomUUID().toString();
    for (var contact : moving) {
      var before = contact.state();
      contact.transfer(survivingId);
      record(contact, ContactAction.TRANSFER, before, actorId, operationId);
    }
  }

  private void record(
      CustomerContact contact,
      ContactAction action,
      ContactState before,
      Long actorId,
      String operationId) {
    if (!contact.state().equals(before))
      histories.save(CustomerContactHistory.record(contact, action, actorId, before, operationId));
  }

  private record ContactKey(ContactType type, String value) {}

  private ContactResponse response(CustomerContact contact) {
    var group =
        contacts.findByCustomerIdAndTypeAndValueAndDeletedFalse(
            contact.getCustomerId(), contact.getType(), contact.getValue());
    return ContactResponse.from(contact, restriction(group));
  }

  private ContactPermissions restriction(List<CustomerContact> group) {
    return group.stream()
        .map(CustomerContact::permissions)
        .reduce(
            new ContactPermissions(
                ContactPermissionStatus.ALLOWED, ContactPermissionStatus.ALLOWED),
            ContactPermissions::restrict);
  }

  private void inheritBeforeRemoval(
      CustomerContact source, ContactState beforeRemoval, String operationId, Long actorId) {
    var group =
        contacts.findByCustomerIdAndTypeAndValueAndDeletedFalse(
            source.getCustomerId(), beforeRemoval.type(), beforeRemoval.value());
    // 値変更後の照会には除去元が含まれないため、操作直前の状態を共同制約へ戻す。
    var restriction =
        restriction(group)
            .restrict(
                new ContactPermissions(
                    beforeRemoval.businessStatus(), beforeRemoval.marketingStatus()));
    for (var target : group) {
      if (target.getId().equals(source.getId())) continue;
      var before = target.state();
      target.inheritRestriction(restriction);
      histories.save(
          CustomerContactHistory.inheritance(target, actorId, before, operationId, source.getId()));
    }
  }

  private CustomerContact active(String customerId, String id) {
    return contacts
        .findByIdAndCustomerIdAndDeletedFalse(id, customerId)
        .orElseThrow(() -> new NotFoundException("連絡先が見つかりません"));
  }

  private String resolve(String customerId) {
    return customers
        .findResolvingMerge(customerId)
        .orElseThrow(() -> new NotFoundException("顧客が見つかりません"))
        .getId();
  }

  private void lock(String customerId) {
    customers.findByIdForUpdate(customerId).orElseThrow(() -> new NotFoundException("顧客が見つかりません"));
    if (customers.isMerged(customerId)) throw new ConflictException("統合済みの顧客です。統合先の顧客を編集してください");
  }

  private Long actorId() {
    String email = SecurityContextHolder.getContext().getAuthentication().getName();
    return users
        .findByEmail(email)
        .orElseThrow(() -> new StaleSessionException("認証セッションの主体が存在しません"))
        .getId();
  }
}
