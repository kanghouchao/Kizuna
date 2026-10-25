package com.kizuna.notificationdelivery.application;

import com.kizuna.customer.domain.ContactType;
import com.kizuna.notificationdelivery.domain.DeliveryContent;
import com.kizuna.order.contact.OrderApplicationBusinessContact;
import com.kizuna.order.contact.OrderBusinessContact;
import com.kizuna.shared.storescope.StoreContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DeliveryContact {
  private final OrderBusinessContact orders;
  private final OrderApplicationBusinessContact applications;
  private final StoreContext store;

  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public OrderBusinessContact.Decision decide(DeliveryContent content) {
    return content.sourceType() == DeliveryContent.SourceType.ORDER
        ? orders.decide(store.getStoreId(), content.sourceId(), ContactType.EMAIL)
        : applications.decide(store.getStoreId(), content.sourceId(), ContactType.EMAIL);
  }
}
