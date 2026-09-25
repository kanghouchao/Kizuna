package com.kizuna.customer.contact;

import com.kizuna.customer.domain.ContactAction;
import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactPurpose;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.CustomerContact;
import com.kizuna.customer.domain.CustomerContactHistory;
import com.kizuna.customer.domain.CustomerContactHistoryRepository;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.customer.domain.CustomerRepository;
import com.kizuna.shared.exception.ConflictException;
import com.kizuna.shared.exception.NotFoundException;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScoped;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuestContactImports {
  private final CustomerRepository customers;
  private final CustomerContactRepository contacts;
  private final CustomerContactHistoryRepository histories;

  @StoreScoped
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  @Transactional(propagation = Propagation.MANDATORY)
  public GuestContactImport preview(
      String customerId, String contactId, ContactType type, String value, boolean marketing) {
    if (customerId != null) lock(customerId);
    if (contactId != null) {
      if (customerId == null) throw new ServiceException("新規顧客には既存の連絡先を指定できません");
      target(customerId, contactId, type, value);
    }
    return new GuestContactImport(
        type, value, result(customerId, type, value, marketing), contactId);
  }

  @StoreScoped
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
  @Transactional(propagation = Propagation.MANDATORY)
  public GuestContactImport record(
      String customerId,
      GuestContactImport input,
      String applicationId,
      String evidence,
      Long actorId) {
    lock(customerId);
    var contact =
        input.contactId() == null
            ? contacts.saveAndFlush(CustomerContact.create(customerId, input.type(), input.value()))
            : target(customerId, input.contactId(), input.type(), input.value());
    String operationId = UUID.randomUUID().toString();
    if (input.contactId() == null)
      histories.save(
          CustomerContactHistory.record(contact, ContactAction.CREATE, actorId, null, operationId));
    if (input.marketingResult() == GuestContactImport.MarketingResult.ALLOWED) {
      var before = contact.state();
      contact.changePermission(ContactPurpose.MARKETING, ContactPermissionStatus.ALLOWED);
      histories.save(
          CustomerContactHistory.guestConsent(
              contact, actorId, before, applicationId, evidence, operationId));
    }
    return new GuestContactImport(
        input.type(), input.value(), input.marketingResult(), contact.getId());
  }

  private GuestContactImport.MarketingResult result(
      String customerId, ContactType type, String value, boolean marketing) {
    if (!marketing) return GuestContactImport.MarketingResult.NOT_REQUESTED;
    if (customerId != null
        && contacts.findByCustomerIdAndTypeAndValueAndDeletedFalse(customerId, type, value).stream()
            .anyMatch(c -> c.getMarketingStatus() == ContactPermissionStatus.DENIED))
      return GuestContactImport.MarketingResult.DENIED_PRESERVED;
    return GuestContactImport.MarketingResult.ALLOWED;
  }

  private CustomerContact target(String customerId, String id, ContactType type, String value) {
    var contact =
        contacts
            .findByIdAndCustomerIdAndDeletedFalse(id, customerId)
            .orElseThrow(() -> new NotFoundException("連絡先が見つかりません"));
    if (contact.getType() != type || !contact.getValue().equals(value))
      throw new ServiceException("申請と同じ種類・値の連絡先を選択してください");
    return contact;
  }

  private void lock(String id) {
    try {
      var customer =
          customers
              .findByIdForUpdateNoWait(id)
              .orElseThrow(() -> new NotFoundException("顧客が見つかりません"));
      if (customer.getMergedIntoId() != null) throw new ConflictException("顧客が統合されました。選択し直してください");
    } catch (CannotAcquireLockException ex) {
      throw new ConflictException("顧客情報が変更中です。入力を保持して再試算してください");
    }
  }
}
