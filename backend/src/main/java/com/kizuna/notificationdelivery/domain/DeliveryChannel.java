package com.kizuna.notificationdelivery.domain;

import com.kizuna.customer.domain.ContactType;

/** 作成時に明示指定し、基盤は自動選択しない。値ごとに宛先となる連絡先種別が一つ決まる。 */
public enum DeliveryChannel {
  EMAIL(ContactType.EMAIL);

  private final ContactType contactType;

  DeliveryChannel(ContactType contactType) {
    this.contactType = contactType;
  }

  public ContactType contactType() {
    return contactType;
  }
}
