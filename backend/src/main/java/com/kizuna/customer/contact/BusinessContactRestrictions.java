package com.kizuna.customer.contact;

import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreContext;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.validation.ContactValues;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BusinessContactRestrictions {
  private final CustomerContactRepository contacts;
  private final StoreContext storeContext;

  /** 授権済み店舗文脈で、送信・再送の直前に呼ぶ。顧客選択や確認状態では対象を限定しない。 */
  @StoreScoped
  @Transactional(readOnly = true)
  public boolean isDenied(ContactType type, String value) {
    if (!storeContext.hasStoreId()) throw new ServiceException("店舗を指定してください");
    if (type == null || value == null || value.isBlank())
      throw new ServiceException("連絡先を指定してください");
    String normalized =
        switch (type) {
          case PHONE -> ContactValues.phone(value, "value");
          case EMAIL -> ContactValues.email(value, "value");
          case LINE -> value.strip();
        };
    return contacts.hasBusinessDenial(type, normalized);
  }
}
