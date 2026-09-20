package com.kizuna.customer.domain;

import com.kizuna.shared.exception.ServiceException;
import com.kizuna.shared.persistence.StoreScopedEntity;
import com.kizuna.shared.validation.ContactValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "t_customer_contacts")
@Filter(name = "storeFilter", condition = "store_id = :storeId")
@Filter(name = "storeSetFilter", condition = "store_id in (:storeIds)")
@Getter
@NoArgsConstructor
public class CustomerContact extends StoreScopedEntity {
  @Column(nullable = false)
  private String customerId;

  @Column(nullable = false, updatable = false)
  private String originCustomerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ContactType type;

  @Column(nullable = false, length = 320)
  private String value;

  @Column(nullable = false)
  private boolean preferred;

  @Column(nullable = false)
  private boolean deleted;

  public static CustomerContact create(String customerId, ContactType type, String value) {
    var contact = new CustomerContact();
    contact.customerId = customerId;
    contact.originCustomerId = customerId;
    contact.change(type, value);
    return contact;
  }

  public void change(ContactType type, String value) {
    if (type == null || value == null || value.isBlank() || value.length() > 320)
      throw new ServiceException("連絡先の種類と320文字以内の値を入力してください");
    this.value =
        switch (type) {
          case PHONE -> ContactValues.phone(value, "value");
          case EMAIL -> ContactValues.email(value, "value");
          case LINE -> value.strip();
        };
    this.type = type;
  }

  public void prefer(boolean preferred) {
    this.preferred = preferred;
  }

  public void delete() {
    deleted = true;
    preferred = false;
  }

  public void transfer(String customerId) {
    this.customerId = customerId;
  }

  public ContactState state() {
    return new ContactState(customerId, type, value, preferred, deleted);
  }
}
