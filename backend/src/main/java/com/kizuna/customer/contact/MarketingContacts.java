package com.kizuna.customer.contact;

import com.kizuna.customer.domain.ContactPermissionStatus;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.CustomerContactRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketingContacts {
  private final CustomerContactRepository contacts;

  public record Contact(String value, boolean allowed) {}

  /** 顧客台帳の優先連絡先と、その販促の可否だけを読む。業務の許可や受注の今回限りの許可からは推定しない。 */
  @Transactional(readOnly = true)
  public Optional<Contact> preferred(Long storeId, String customerId, ContactType type) {
    return contacts
        .findPreferred(storeId, customerId, type)
        .map(
            c ->
                new Contact(
                    c.getValue(), c.getMarketingStatus() == ContactPermissionStatus.ALLOWED));
  }
}
