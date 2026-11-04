package com.kizuna.notificationdelivery.application;

import com.kizuna.customer.domain.ContactType;
import com.kizuna.notificationdelivery.domain.DeliveryPurpose;
import com.kizuna.notificationdelivery.domain.DeliverySourceType;

/** 起点種別ごとに一つ置く解決器。起点が無ければ NotFoundException を投げ、送信側は SOURCE_UNAVAILABLE に落とす。 */
public interface DeliverySourceResolver {
  DeliverySourceType sourceType();

  DeliveryRecipient resolve(
      Long storeId, String sourceId, ContactType type, DeliveryPurpose purpose);
}
