package com.kizuna.notificationdelivery.application;

import com.kizuna.customer.contact.MarketingContacts;
import com.kizuna.customer.domain.ContactType;
import com.kizuna.notificationdelivery.domain.DeliveryPurpose;
import com.kizuna.notificationdelivery.domain.DeliverySourceType;
import com.kizuna.order.contact.OrderApplicationBusinessContact;
import com.kizuna.order.contact.SourceCustomers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationSourceResolver implements DeliverySourceResolver {
  private final OrderApplicationBusinessContact business;
  private final SourceCustomers customers;
  private final MarketingContacts marketing;

  @Override
  public DeliverySourceType sourceType() {
    return DeliverySourceType.APPLICATION;
  }

  @Override
  public DeliveryRecipient resolve(
      Long storeId, String sourceId, ContactType type, DeliveryPurpose purpose) {
    return switch (purpose) {
      case BUSINESS -> DeliveryRecipient.business(business.decide(storeId, sourceId, type));
      case MARKETING ->
          DeliveryRecipient.marketing(
              customers
                  .ofApplication(storeId, sourceId)
                  .flatMap(customer -> marketing.preferred(storeId, customer, type)));
    };
  }
}
