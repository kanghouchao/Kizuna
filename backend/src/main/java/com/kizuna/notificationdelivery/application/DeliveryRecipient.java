package com.kizuna.notificationdelivery.application;

import com.kizuna.customer.contact.MarketingContacts;
import com.kizuna.order.contact.OrderBusinessContact;
import java.util.Optional;

/** 解決器が送信直前に返す宛先と、その用途での連絡可否。許可されたときだけ宛先へ送る。 */
public record DeliveryRecipient(String address, ContactDecision decision) {
  static DeliveryRecipient business(OrderBusinessContact.Decision decision) {
    return new DeliveryRecipient(
        decision.value(),
        switch (decision.decision()) {
          case ALLOWED -> ContactDecision.ALLOWED;
          case STORE_DENIED -> ContactDecision.STORE_DENIED;
          case NOT_ALLOWED -> ContactDecision.NOT_ALLOWED;
        });
  }

  static DeliveryRecipient marketing(Optional<MarketingContacts.Contact> contact) {
    return contact
        .map(
            c ->
                new DeliveryRecipient(
                    c.value(), c.allowed() ? ContactDecision.ALLOWED : ContactDecision.NOT_ALLOWED))
        .orElse(new DeliveryRecipient(null, ContactDecision.NOT_ALLOWED));
  }
}
