package com.kizuna.notificationdelivery.domain;

import com.kizuna.customer.domain.ContactType;
import com.kizuna.notification.transport.ContactTransport;

/** 作成時に明示指定し、基盤は自動選択しない。値ごとに宛先となる連絡先種別と、送信手段のチャネルが一つずつ決まる。 */
public enum DeliveryChannel {
  EMAIL(ContactType.EMAIL, ContactTransport.Channel.EMAIL);

  private final ContactType contactType;
  private final ContactTransport.Channel transportChannel;

  DeliveryChannel(ContactType contactType, ContactTransport.Channel transportChannel) {
    this.contactType = contactType;
    this.transportChannel = transportChannel;
  }

  public ContactType contactType() {
    return contactType;
  }

  public ContactTransport.Channel transportChannel() {
    return transportChannel;
  }
}
