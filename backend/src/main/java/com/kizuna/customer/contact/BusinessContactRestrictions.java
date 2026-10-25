package com.kizuna.customer.contact;

import com.kizuna.customer.domain.ContactType;
import com.kizuna.customer.domain.CustomerContactRepository;
import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.storescope.StoreScoped;
import com.kizuna.shared.validation.ContactValues;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BusinessContactRestrictions {
  private final CustomerContactRepository contacts;

  /** 授権済みの店舗について、送信・再送の直前に呼ぶ。顧客選択や確認状態では対象を限定しない。 */
  @StoreScoped
  @Transactional(readOnly = true)
  public boolean isDenied(Long storeId, ContactType type, String value) {
    if (storeId == null) throw new ServiceException("店舗を指定してください");
    if (type == null || value == null || value.isBlank())
      throw new ServiceException("連絡先を指定してください");
    String normalized =
        switch (type) {
          case PHONE -> ContactValues.phone(value, "value");
          case EMAIL -> ContactValues.email(value, "value");
          case LINE -> value.strip();
        };
    return contacts.hasBusinessDenial(storeId, type, normalized);
  }
}
