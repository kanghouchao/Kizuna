package com.kizuna.customer.application;

import com.kizuna.customer.api.dto.ContactHistoryResponse;
import com.kizuna.customer.api.dto.ContactRequest;
import com.kizuna.customer.api.dto.ContactResponse;
import com.kizuna.customer.api.dto.ContactSummary;
import com.kizuna.customer.domain.ContactAction;
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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerContactService {
  private final CustomerRepository customers;
  private final CustomerContactRepository contacts;
  private final CustomerContactHistoryRepository histories;
  private final PlatformUserRepository users;

  @StoreScoped
  @Transactional(readOnly = true)
  public CursorPage<ContactResponse> list(String customerId, String cursor, int requestedSize) {
    String resolved = resolve(customerId);
    int size = CursorPage.clampSize(requestedSize);
    return CursorPage.of(
            contacts.findByCustomerIdAndDeletedFalseAndIdGreaterThanOrderByIdAsc(
                resolved, cursor == null ? "" : PageCursor.decodeKey(cursor), Limit.of(size + 1)),
            size,
            c -> PageCursor.encodeKey(c.getId()))
        .map(ContactResponse::from);
  }

  @StoreScoped
  @Transactional
  public ContactResponse create(String customerId, ContactRequest input) {
    lock(customerId);
    CustomerContact contact =
        contacts.saveAndFlush(CustomerContact.create(customerId, input.type(), input.value()));
    record(contact, ContactAction.CREATE, null, actorId());
    return ContactResponse.from(contact);
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
    record(contact, ContactAction.UPDATE, before, actorId());
    contacts.flush();
    return ContactResponse.from(contact);
  }

  @StoreScoped
  @Transactional
  public void delete(String customerId, String id) {
    lock(customerId);
    var contact = active(customerId, id);
    var before = contact.state();
    contact.delete();
    record(contact, ContactAction.DELETE, before, actorId());
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
    if (previous != null) {
      var before = previous.state();
      previous.prefer(false);
      record(previous, ContactAction.PREFERENCE, before, actorId);
      // 部分一意索引を満たしたまま指定先を切り替えるため、解除を先に確定させる。
      contacts.flush();
    }
    if (chosen != null) {
      var before = chosen.state();
      chosen.prefer(true);
      record(chosen, ContactAction.PREFERENCE, before, actorId);
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
                    h.getAfter()));
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
    for (var contact : moving) {
      var before = contact.state();
      contact.transfer(survivingId);
      record(contact, ContactAction.TRANSFER, before, actorId);
    }
  }

  private void record(
      CustomerContact contact, ContactAction action, ContactState before, Long actorId) {
    if (!contact.state().equals(before))
      histories.save(CustomerContactHistory.record(contact, action, actorId, before));
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
