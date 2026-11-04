package com.kizuna.notificationdelivery.application;

import com.kizuna.customer.contact.MarketingContacts;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.notificationdelivery.domain.DeliveryPurpose;
import com.kizuna.notificationdelivery.domain.DeliverySourceType;
import com.kizuna.order.contact.OrderBusinessContact;
import com.kizuna.order.contact.SourceCustomers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderSourceResolver implements DeliverySourceResolver {
  private final OrderBusinessContact business;
  private final SourceCustomers customers;
  private final MarketingContacts marketing;

  @Override
  public DeliverySourceType sourceType() {
    return DeliverySourceType.ORDER;
  }

  @Override
  public DeliveryRecipient resolve(
      Long storeId, String sourceId, ContactType type, DeliveryPurpose purpose) {
    return switch (purpose) {
      case BUSINESS -> DeliveryRecipient.business(business.decide(storeId, sourceId, type));
      case MARKETING ->
          DeliveryRecipient.marketing(
              customers
                  .ofOrder(storeId, sourceId)
                  .flatMap(customer -> marketing.preferred(storeId, customer, type)));
    };
  }
}
